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
 * Decoding uses beam search (width from bundled config, default 4) with a [ChatTitleSanitizer]
 * quality gate applied upstream in [com.browntowndev.enclave.domain.usecase.chat.GenerateChatTitleUseCase].
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
                    val model = getLoadedModel() ?: return@withContext null
                    runGeneration(model, prompt)?.takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Title generation failed; falling back", e)
                    null
                }
            }

        private suspend fun getLoadedModel(): LoadedTitleModel? =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: loadModel()?.also { loadedModel = it }
            }

        private suspend fun loadModel(): LoadedTitleModel? {
            val encoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER)
                ?: return null
            val decoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER)
                ?: return null
            val tokenizerPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER)
                ?: return null

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
                sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                environment.createSession(modelPath, sessionOptions)
            }

        private fun runGeneration(
            model: LoadedTitleModel,
            prompt: String,
        ): String? {
            val config = model.config
            val environment = OrtEnvironment.getEnvironment()
            val inputIds =
                model.tokenizer.encode(
                    text = config.taskPrefix + prompt,
                    eosTokenId = config.eosTokenId,
                    maxLength = config.maxInputLength,
                )
            val seqLen = inputIds.size.toLong()
            val attentionMask = LongArray(inputIds.size) { 1L }

            val encoderInputs =
                mapOf(
                    "input_ids" to
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(inputIds), longArrayOf(1, seqLen)),
                    "attention_mask" to
                        OnnxTensor.createTensor(environment, LongBuffer.wrap(attentionMask), longArrayOf(1, seqLen)),
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
                        val generated =
                            TitleGenBeamDecoder.decode(config) { decoderIds ->
                                runDecoderStep(
                                    environment = environment,
                                    decoderSession = model.decoderSession,
                                    decoderIds = decoderIds,
                                    encoderHiddenStates = hidden,
                                    encoderAttention = encoderAttention,
                                )
                            }
                        return model.tokenizer.decode(generated)
                    } finally {
                        encoderAttention.close()
                    }
                }
            } finally {
                encoderInputs.values.forEach(OnnxTensor::close)
            }
        }

        private fun runDecoderStep(
            environment: OrtEnvironment,
            decoderSession: OrtSession,
            decoderIds: LongArray,
            encoderHiddenStates: OnnxTensor,
            encoderAttention: OnnxTensor,
        ): FloatArray? {
            val decoderTensor =
                OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(decoderIds),
                    longArrayOf(1, decoderIds.size.toLong()),
                )
            return try {
                decoderTensor.use { tensor ->
                    val decoderInputs =
                        mapOf(
                            "input_ids" to tensor,
                            "encoder_attention_mask" to encoderAttention,
                            "encoder_hidden_states" to encoderHiddenStates,
                        )
                    decoderSession.run(decoderInputs).use { decoderResult ->
                        lastStepLogits(decoderResult[0].value)
                    }
                }
            } catch (_: Exception) {
                null
            }
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
