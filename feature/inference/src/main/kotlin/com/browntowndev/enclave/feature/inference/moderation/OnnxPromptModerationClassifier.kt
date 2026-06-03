package com.browntowndev.enclave.feature.inference.moderation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
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

@Singleton
class OnnxPromptModerationClassifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : PromptModerationPort {
        private val loadMutex = Mutex()

        @Volatile
        private var loadedModel: LoadedModerationModel? = null

        override suspend fun classify(prompt: String): PromptModerationScores =
            withContext(Dispatchers.IO) {
                val model = getLoadedModel()
                val encoded = model.tokenizer.encode(prompt)
                val logits = model.session.runModeration(encoded, model.config.maxSequenceLength)
                val probabilities = softmax(logits)
                PromptModerationScores(
                    scoresByLabel = model.config.labels.zip(probabilities.asIterable()).associate { (label, score) ->
                        label to score.toDouble()
                    },
                    labelAliases = model.config.labelAliases,
                    thresholds = model.config.thresholds,
                    combinedIllegalLabels = model.config.combinedIllegalLabels,
                )
            }

        private suspend fun getLoadedModel(): LoadedModerationModel =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: loadModerationModel().also { model ->
                    loadedModel = model
                }
            }

        private fun loadModerationModel(): LoadedModerationModel {
            val config = ModerationAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
            val modelFile = copyAssetToModelDirectory(config.modelPath)
            val tokenizerJson = readAssetText("$ASSET_BASE_PATH/${config.tokenizerPath}")
            val tokenizer =
                HuggingFaceByteLevelBpeTokenizer.fromTokenizerJson(
                    tokenizerJson = tokenizerJson,
                    maxSequenceLength = config.maxSequenceLength,
                )
            val environment = OrtEnvironment.getEnvironment()
            val sessionOptions =
                OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
            val session = environment.createSession(modelFile.absolutePath, sessionOptions)
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
            context.assets.open(path).bufferedReader().use { reader -> reader.readText() }

        private fun copyAssetToModelDirectory(modelPath: String): File {
            val targetDirectory = File(context.noBackupFilesDir, ASSET_BASE_PATH)
            targetDirectory.mkdirs()
            val targetFile = File(targetDirectory, modelPath)
            if (targetFile.exists()) {
                return targetFile
            }
            context.assets.open("$ASSET_BASE_PATH/$modelPath").use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return targetFile
        }

        private data class LoadedModerationModel(
            val config: ModerationAssetConfig,
            val tokenizer: HuggingFaceByteLevelBpeTokenizer,
            val session: OrtSession,
        )

        private companion object {
            private const val ASSET_BASE_PATH = "models/koala_text_moderation"
            private const val CONFIG_ASSET_PATH = "$ASSET_BASE_PATH/model_config.json"
        }
    }

private fun softmax(logits: FloatArray): FloatArray {
    val max = logits.maxOrNull() ?: return FloatArray(0)
    val expValues = logits.map { logit -> exp((logit - max).toDouble()) }
    val sum = expValues.sum()
    return expValues.map { value -> (value / sum).toFloat() }.toFloatArray()
}
