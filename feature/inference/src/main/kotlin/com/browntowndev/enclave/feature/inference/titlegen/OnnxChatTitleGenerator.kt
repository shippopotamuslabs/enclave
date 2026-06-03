package com.browntowndev.enclave.feature.inference.titlegen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device chat-title generator backed by an encoder-decoder (flan-t5-small) ONNX model.
 *
 * Part of the utility-ONNX lane (see INFERENCE_RULES.md §7): runs on [Dispatchers.IO],
 * never acquires the inference lock (so it executes in parallel with the chat LLM on the
 * CPU execution provider), lazy-loads its sessions once behind a [Mutex], and degrades
 * silently to `null` on any failure.
 *
 * Model files are **downloaded on demand** and resolved via [UtilityModelFilePort]; only the
 * tiny generation-parameter config is bundled in the APK. Until all three files have been
 * downloaded, [generateTitle] returns `null` and the caller keeps the truncated title.
 *
 * The FP16 graph **must** run with graph optimizations disabled ([OrtSession.SessionOptions.OptLevel.NO_OPT]):
 * ONNX Runtime's layer-norm fusion conflicts with the inserted fp16 casts and crashes session init.
 */
@Singleton
class OnnxChatTitleGenerator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val utilityModelFilePort: UtilityModelFilePort,
    ) : ChatTitleGeneratorPort {
        private val loadMutex = Mutex()

        @Volatile
        private var loadedModel: LoadedTitleModel? = null

        override suspend fun generateTitle(prompt: String): String? =
            withContext(Dispatchers.IO) {
                if (prompt.isBlank()) return@withContext null
                try {
                    val model = getLoadedModel()
                    if (model == null) {
                        return@withContext null
                    }
                    runGeneration(model, prompt)?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Title generation failed; falling back", e)
                    null
                }
            }

        private suspend fun getLoadedModel(): LoadedTitleModel? =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: loadModel()?.also { model ->
                    loadedModel = model
                }
            }

        /** Returns null when the downloaded model files are not yet available. */
        private suspend fun loadModel(): LoadedTitleModel? {
            val encoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER)
            if (encoderPath == null) return null

            val decoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER)
            if (decoderPath == null) return null

            val tokenizerPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER)
            if (tokenizerPath == null) return null

            val config = TitleGenAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
            val tokenizer = T5UnigramTokenizer.fromTokenizerJson(File(tokenizerPath).readText())
            val environment = OrtEnvironment.getEnvironment()
            return LoadedTitleModel(
                config = config,
                tokenizer = tokenizer,
                encoderSession = createSession(environment, encoderPath),
                decoderSession = createSession(environment, decoderPath),
            )
        }

        private fun createSession(
            environment: OrtEnvironment,
            modelPath: String,
        ): OrtSession =
            OrtSession.SessionOptions().use { sessionOptions ->
                // FP16 graphs crash under ORT layer-norm fusion; disable all graph optimizations.
                sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                environment.createSession(modelPath, sessionOptions)
            }

        /** Encoder forward pass + greedy decode loop bounded by `max_output_tokens`. */
        private fun runGeneration(
            model: LoadedTitleModel,
            prompt: String,
        ): String? {
            val config = model.config
            val environment = OrtEnvironment.getEnvironment()
            val inputIds = model.tokenizer.encode(config.taskPrefix + prompt, config.eosTokenId, config.maxInputLength)
            val seqLen = inputIds.size.toLong()
            val attentionMask = LongArray(inputIds.size) { 1L }

            val encoderInputs =
                mapOf(
                    "input_ids" to OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), longArrayOf(1, seqLen)),
                    "attention_mask" to OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), longArrayOf(1, seqLen)),
                )
            try {
                model.encoderSession.run(encoderInputs).use { encoderResult ->
                    val hidden = encoderResult[0] as OnnxTensor

                    val encoderAttention =
                        OnnxTensor.createTensor(
                            environment,
                            LongBuffer.wrap(attentionMask),
                            longArrayOf(1, seqLen),
                        )
                    try {
                        val generated = greedyDecode(model, environment, hidden, encoderAttention)
                        return model.tokenizer.decode(generated)
                    } finally {
                        encoderAttention.close()
                    }
                }
            } finally {
                encoderInputs.values.forEach { tensor -> tensor.close() }
            }
        }

        private fun greedyDecode(
            model: LoadedTitleModel,
            environment: OrtEnvironment,
            encoderHiddenStates: OnnxTensor,
            encoderAttention: OnnxTensor,
        ): List<Int> {
            val config = model.config
            val decoderIds = ArrayList<Long>().apply { add(config.decoderStartTokenId.toLong()) }
            val generated = ArrayList<Int>()
            repeat(config.maxOutputTokens) { step ->
                val current = decoderIds.toLongArray()
                val decoderTensor =
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(current), longArrayOf(1, current.size.toLong()))
                decoderTensor.use { tensor ->
                    val decoderInputs =
                        mapOf(
                            "input_ids" to tensor,
                            "encoder_attention_mask" to encoderAttention,
                            "encoder_hidden_states" to encoderHiddenStates,
                        )
                    model.decoderSession.run(decoderInputs).use { decoderResult ->
                        val outputValue = decoderResult[0].value
                        val logits = lastStepLogits(outputValue)
                        if (logits != null) {
                            val nextToken = argmax(logits)
                            if (nextToken == config.eosTokenId || nextToken == config.padTokenId) {
                                return generated
                            }
                            generated.add(nextToken)
                            decoderIds.add(nextToken.toLong())
                        } else {
                            return generated
                        }
                    }
                }
            }
            return generated
        }

        private fun readAssetText(path: String): String =
            context.assets.open(path).bufferedReader().use { reader -> reader.readText() }

        private data class LoadedTitleModel(
            val config: TitleGenAssetConfig,
            val tokenizer: T5UnigramTokenizer,
            val encoderSession: OrtSession,
            val decoderSession: OrtSession,
        )

        private companion object {
            private const val TAG = "OnnxChatTitleGen"
            private const val CONFIG_ASSET_PATH = "models/chat_title_generator/model_config.json"
        }
    }

/** Returns the logits for the final decode step from a `[1, seq, vocab]` output, or null. */
internal fun lastStepLogits(value: Any?): FloatArray? {
    val batch = value as? Array<*> ?: return null
    val steps = batch.firstOrNull() as? Array<*> ?: return null
    return steps.lastOrNull() as? FloatArray
}

/** Index of the maximum value in [row]. */
internal fun argmax(row: FloatArray): Int {
    var bestIndex = 0
    var bestValue = row[0]
    for (index in 1 until row.size) {
        if (row[index] > bestValue) {
            bestValue = row[index]
            bestIndex = index
        }
    }
    return bestIndex
}
