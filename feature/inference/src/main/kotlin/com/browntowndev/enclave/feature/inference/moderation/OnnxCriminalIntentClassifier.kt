package com.browntowndev.enclave.feature.inference.moderation

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import com.browntowndev.enclave.domain.port.inference.CriminalIntentModerationPort
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
class OnnxCriminalIntentClassifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : CriminalIntentModerationPort {
        private val loadMutex = Mutex()

        @Volatile
        private var loadedModel: LoadedCriminalIntentModel? = null

        override suspend fun classify(prompt: String): PromptModerationScores =
            withContext(Dispatchers.IO) {
                val model = getLoadedModel()
                val encoded = model.tokenizer.encode(prompt)
                val logits = model.session.runModeration(encoded, model.config.maxSequenceLength)
                // Multi-label sequence classification output has sigmoid probabilities
                val probabilities = logits.map { logit -> sigmoid(logit) }
                val scoresByLabel =
                    model.config.labels.zip(probabilities).associate { (label, score) ->
                        label to score
                    }
                PromptModerationScores(
                    scoresByLabel = scoresByLabel,
                    labelAliases = model.config.labelAliases,
                    thresholds = model.config.thresholds,
                    combinedIllegalLabels = model.config.combinedIllegalLabels,
                )
            }

        private suspend fun getLoadedModel(): LoadedCriminalIntentModel =
            loadedModel ?: loadMutex.withLock {
                loadedModel ?: loadCriminalIntentModel().also { model ->
                    loadedModel = model
                }
            }

        private fun loadCriminalIntentModel(): LoadedCriminalIntentModel {
            val config = ModerationAssetConfig.parse(readAssetText(CONFIG_ASSET_PATH))
            val modelFile = copyAssetToModelDirectory(config.modelPath)
            val vocabLines = readAssetLines("$ASSET_BASE_PATH/vocab.txt")
            val tokenizer =
                HuggingFaceWordPieceTokenizer(
                    vocabLines = vocabLines,
                    maxSequenceLength = config.maxSequenceLength,
                )
            val environment = OrtEnvironment.getEnvironment()
            val session =
                OrtSession.SessionOptions().use { sessionOptions ->
                    sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    environment.createSession(modelFile.absolutePath, sessionOptions)
                }
            return LoadedCriminalIntentModel(
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

        private fun readAssetLines(path: String): List<String> =
            context.assets
                .open(path)
                .bufferedReader()
                .readLines()

        private fun copyAssetToModelDirectory(modelPath: String): File {
            val targetDirectory = File(context.noBackupFilesDir, ASSET_BASE_PATH)
            targetDirectory.mkdirs()
            val targetFile = File(targetDirectory, modelPath)
            if (targetFile.exists()) {
                return targetFile
            }
            val tempFile = File(targetDirectory, "$modelPath.tmp")
            try {
                context.assets.open("$ASSET_BASE_PATH/$modelPath").use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (!tempFile.renameTo(targetFile)) {
                    throw java.io.IOException("Failed to rename temporary model file to $targetFile")
                }
            } catch (e: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                throw e
            }
            return targetFile
        }

        private data class LoadedCriminalIntentModel(
            val config: ModerationAssetConfig,
            val tokenizer: HuggingFaceWordPieceTokenizer,
            val session: OrtSession,
        )

        private companion object {
            private const val ASSET_BASE_PATH = "models/criminal_intent_classifier"
            private const val CONFIG_ASSET_PATH = "$ASSET_BASE_PATH/model_config.json"
        }
    }

private fun sigmoid(logit: Float): Double = 1.0 / (1.0 + exp(-logit.toDouble()))
