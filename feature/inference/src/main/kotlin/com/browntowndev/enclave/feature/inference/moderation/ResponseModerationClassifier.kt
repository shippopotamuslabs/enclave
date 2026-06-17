package com.browntowndev.enclave.feature.inference.moderation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
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
import kotlin.math.exp

/**
 * ONNX-Runtime implementation of [PromptModerationPort] running a DeBERTa text-safety classifier
 * (KoalaAI/Text-Moderation) on the CPU. The ONNX weights are downloaded on demand from R2 via
 * [UtilityModelFilePort]; only the small tokenizer/config files ship in APK assets. Loads lazily
 * once (guarded by [loadMutex] and [loadedModel]) and degrades silently — any load or inference
 * failure returns empty scores so the moderation pipeline is never broken (per
 * `INFERENCE_RULES.md §7`).
 */
@Singleton
class ResponseModerationClassifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val utilityModelFilePort: UtilityModelFilePort,
    ) : PromptModerationPort {
        private val loadMutex = Mutex()

        @Volatile
        private var loadedModel: LoadedModerationModel? = null

        override suspend fun classify(prompt: String): PromptModerationScores =
            withContext(Dispatchers.IO) {
                runCatching {
                    val model = getLoadedModel()
                    if (model == null) {
                        Log.w(TAG, "Model not downloaded; prompt passes unmoderated (${prompt.length} chars)")
                        return@withContext notBlocked()
                    }
                    val encoded = model.tokenizer.encode(prompt)
                    val logits = model.session.runModeration(encoded, model.config.maxSequenceLength)
                    val probabilities =
                        if (model.config.isSigmoid()) {
                            logits.map { logit -> sigmoid(logit) }
                        } else {
                            softmax(logits).toList().map { it.toDouble() }
                        }
                    PromptModerationScores(
                        scoresByLabel =
                            model.config.labels.zip(probabilities).associate { (label, score) ->
                                label to score
                            },
                        labelAliases = model.config.labelAliases,
                        thresholds = model.config.thresholds,
                        combinedIllegalLabels = model.config.combinedIllegalLabels,
                    )
                }.getOrElse { e ->
                    Log.w(TAG, "Classification failed; prompt passes unmoderated (${prompt.length} chars)", e)
                    notBlocked()
                }
            }

        private fun notBlocked(): PromptModerationScores = PromptModerationScores(scoresByLabel = emptyMap())

        private suspend fun getLoadedModel(): LoadedModerationModel? =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: runCatching { loadModerationModel() }
                    .onFailure { e -> Log.w(TAG, "Failed to load prompt-moderation model", e) }
                    .getOrNull()
                    ?.also { loadedModel = it }
            }

        private suspend fun loadModerationModel(): LoadedModerationModel {
            val config = ModerationAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
            val modelFile =
                resolveDownloadedModelFile()
                    ?: throw IllegalStateException(
                        "Prompt safety classifier is not downloaded (utilityType=${UtilityType.PROMPT_SAFETY_CLASSIFIER.apiValue})",
                    )
            val tokenizerJson = readAssetText("$ASSET_BASE_PATH/${config.tokenizerPath}")
            val tokenizer =
                HuggingFaceByteLevelBpeTokenizer.fromTokenizerJson(
                    tokenizerJson = tokenizerJson,
                    maxSequenceLength = config.maxSequenceLength,
                )
            val environment = OrtEnvironment.getEnvironment()
            val session =
                OrtSession.SessionOptions().use { sessionOptions ->
                    sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    environment.createSession(modelFile.absolutePath, sessionOptions)
                }
            return LoadedModerationModel(
                config = config,
                tokenizer = tokenizer,
                session = session,
            )
        }

        private fun OrtSession.runModeration(
            encoded: EncodedPrompt,
            maxSequenceLength: Int,
        ): FloatArray {
            val shape = longArrayOf(1L, maxSequenceLength.toLong())
            val tensors =
                mutableListOf(
                    OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(encoded.inputIds), shape),
                    OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(encoded.attentionMask), shape),
                    OnnxTensor.createTensor(OrtEnvironment.getEnvironment(), LongBuffer.wrap(encoded.tokenTypeIds), shape),
                )
            val inputs = mutableMapOf<String, OnnxTensor>()
            try {
                val expectedInputNames = this.inputNames
                inputs["input_ids"] = tensors[0]
                if ("attention_mask" in expectedInputNames) {
                    inputs["attention_mask"] = tensors[1]
                }
                if ("token_type_ids" in expectedInputNames) {
                    inputs["token_type_ids"] = tensors[2]
                }

                run(inputs).use { results ->
                    return when (val output = results[0].value) {
                        is Array<*> -> output.firstOrNull() as? FloatArray ?: error("Unexpected moderation output array")
                        is FloatArray -> output
                        else -> error("Unsupported moderation output: ${output::class.java.name}")
                    }
                }
            } finally {
                tensors.forEach { tensor -> tensor.close() }
            }
        }

        private fun readAssetText(path: String): String =
            context.assets
                .open(path)
                .bufferedReader()
                .use { reader -> reader.readText() }

        private suspend fun resolveDownloadedModelFile(): File? {
            val resolvedPath =
                utilityModelFilePort.resolveUtilityModelPath(UtilityType.PROMPT_SAFETY_CLASSIFIER)
                    ?: return null
            val file = File(resolvedPath)
            return file.takeIf { it.exists() && it.isFile }
        }

        private data class LoadedModerationModel(
            val config: ModerationAssetConfig,
            val tokenizer: HuggingFaceByteLevelBpeTokenizer,
            val session: OrtSession,
        )

        private companion object {
            private const val TAG = "ResponseModeration"
            private const val ASSET_BASE_PATH = "models/nsfw_text_classifier"
            private const val CONFIG_ASSET_PATH = "$ASSET_BASE_PATH/model_config.json"
        }
    }

private fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: return FloatArray(0)
    val expValues = logits.map { logit -> exp((logit - max).toDouble()) }
    val sum = expValues.sum()
    return expValues.map { value -> (value / sum).toFloat() }.toFloatArray()
}

private fun sigmoid(logit: Float): Double = 1.0 / (1.0 + exp(-logit.toDouble()))
