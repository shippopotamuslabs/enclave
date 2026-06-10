package com.browntowndev.enclave.feature.inference.titlegen

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.download.ModelConfig
import com.browntowndev.enclave.domain.port.repository.UtilityModelFilePort
import com.browntowndev.enclave.domain.usecase.chat.ChatTitleSanitizer
import java.io.File
import java.nio.LongBuffer
import java.security.MessageDigest

/**
 * On-device diagnostic runner for the chat title generator lane.
 *
 * Used by instrumentation tests and adb-driven harness loops; logs a structured report under
 * [LOG_TAG] so failures can be diagnosed without manual log copy/paste.
 */
object TitleGenHarnessDiagnostics {
    const val LOG_TAG = "TitleGenHarness"

    data class Report(
        val userPrompt: String,
        val encoderPath: String?,
        val decoderPath: String?,
        val tokenizerPath: String?,
        val modelSizeBytes: Long?,
        val modelSha256Prefix: String?,
        val configPrefix: String,
        val inputTokenCount: Int,
        val inputTailTokenIds: List<Long>,
        val onnxOutputShape: String?,
        val generatedTokenIds: List<Int>,
        val rawDecode: String?,
        val sanitizedTitle: String?,
        val portGenerateTitle: String?,
        val failureStage: String?,
        val failureDetail: String?,
    ) {
        fun toLogString(): String =
            buildString {
                appendLine("=== TitleGen harness report ===")
                appendLine("prompt=${userPrompt.quote()}")
                appendLine("encoderPath=${encoderPath ?: "UNRESOLVED"}")
                appendLine("decoderPath=${decoderPath ?: "UNRESOLVED"}")
                appendLine("tokenizerPath=${tokenizerPath ?: "UNRESOLVED"}")
                appendLine("encoderSizeBytes=${modelSizeBytes ?: "n/a"}")
                appendLine("encoderSha256Prefix=${modelSha256Prefix ?: "n/a"}")
                appendLine("configPrefix=${configPrefix.quote()}")
                appendLine("inputTokenCount=$inputTokenCount")
                appendLine("inputTailTokenIds=$inputTailTokenIds")
                appendLine("onnxOutputShape=${onnxOutputShape ?: "n/a"}")
                appendLine("generatedTokenIds=$generatedTokenIds")
                appendLine("rawDecode=${rawDecode.quote()}")
                appendLine("sanitizedTitle=${sanitizedTitle.quote()}")
                appendLine("portGenerateTitle=${portGenerateTitle.quote()}")
                appendLine("failureStage=${failureStage ?: "none"}")
                appendLine("failureDetail=${failureDetail ?: "none"}")
                appendLine("=== end harness report ===")
            }

        private fun String?.quote(): String = if (this == null) "null" else "'$this'"
    }

    suspend fun run(
        context: Context,
        utilityModelFilePort: UtilityModelFilePort,
        userPrompt: String,
        portGenerateTitle: suspend (String) -> String?,
    ): Report {
        val encoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_ENCODER)
        val decoderPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_DECODER)
        val tokenizerPath = utilityModelFilePort.resolveUtilityModelPath(UtilityType.TITLE_GENERATOR_TOKENIZER)
        val encoderFile = encoderPath?.let(::File)
        val encoderSize = encoderFile?.takeIf { it.exists() }?.length()
        val encoderShaPrefix = encoderFile?.takeIf { it.exists() }?.let { sha256Prefix(it) }

        val unresolved =
            when {
                encoderPath == null ->
                    unresolvedReport(
                        userPrompt = userPrompt,
                        decoderPath = decoderPath,
                        tokenizerPath = tokenizerPath,
                        portGenerateTitle = portGenerateTitle,
                        stage = "resolve_encoder_path",
                        detail =
                            "TITLE_GENERATOR_ENCODER path is null (missing file or checksum mismatch). " +
                                describeTitleAssetsOnDisk(context),
                    )
                decoderPath == null ->
                    unresolvedReport(
                        userPrompt = userPrompt,
                        encoderPath = encoderPath,
                        tokenizerPath = tokenizerPath,
                        encoderSize = encoderSize,
                        encoderShaPrefix = encoderShaPrefix,
                        portGenerateTitle = portGenerateTitle,
                        stage = "resolve_decoder_path",
                        detail = "TITLE_GENERATOR_DECODER path is null (missing file or checksum mismatch)",
                    )
                tokenizerPath == null ->
                    unresolvedReport(
                        userPrompt = userPrompt,
                        encoderPath = encoderPath,
                        decoderPath = decoderPath,
                        encoderSize = encoderSize,
                        encoderShaPrefix = encoderShaPrefix,
                        portGenerateTitle = portGenerateTitle,
                        stage = "resolve_tokenizer_path",
                        detail = "TITLE_GENERATOR_TOKENIZER path is null (missing file or checksum mismatch)",
                    )
                else -> null
            }
        if (unresolved != null) return unresolved

        val resolvedEncoderPath = requireNotNull(encoderPath)
        val resolvedDecoderPath = requireNotNull(decoderPath)
        val resolvedTokenizerPath = requireNotNull(tokenizerPath)

        return try {
            val config =
                TitleGenAssetConfig.parse(
                    context.assets
                        .open(CONFIG_ASSET_PATH)
                        .bufferedReader()
                        .use { it.readText() },
                )
            val tokenizer = T5UnigramTokenizer.fromTokenizerJson(File(resolvedTokenizerPath).readText())
            val inputIds =
                tokenizer.encode(
                    text = config.taskPrefix + userPrompt,
                    eosTokenId = config.eosTokenId,
                    maxLength = config.maxInputLength,
                )
            val preflight =
                when {
                    config.taskPrefix.isBlank() ->
                        baseReport(
                            userPrompt = userPrompt,
                            encoderPath = resolvedEncoderPath,
                            decoderPath = resolvedDecoderPath,
                            tokenizerPath = resolvedTokenizerPath,
                            encoderSize = encoderSize,
                            encoderShaPrefix = encoderShaPrefix,
                            config = config,
                            portGenerateTitle = portGenerateTitle,
                            stage = "config",
                            detail = "Bundled task_prefix is blank",
                        )
                    inputIds.isEmpty() ->
                        baseReport(
                            userPrompt = userPrompt,
                            encoderPath = resolvedEncoderPath,
                            decoderPath = resolvedDecoderPath,
                            tokenizerPath = resolvedTokenizerPath,
                            encoderSize = encoderSize,
                            encoderShaPrefix = encoderShaPrefix,
                            config = config,
                            portGenerateTitle = portGenerateTitle,
                            stage = "tokenize",
                            detail = "encode returned empty ids",
                        )
                    else -> null
                }
            if (preflight != null) return preflight

            val environment = OrtEnvironment.getEnvironment()
            val encoderSession =
                OrtSession.SessionOptions().use { options ->
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                    environment.createSession(resolvedEncoderPath, options)
                }
            val decoderSession =
                OrtSession.SessionOptions().use { options ->
                    options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.NO_OPT)
                    environment.createSession(resolvedDecoderPath, options)
                }
            encoderSession.use { encSession ->
                decoderSession.use { decSession ->
                    val generation =
                        runGeneration(
                            encoderSession = encSession,
                            decoderSession = decSession,
                            environment = environment,
                            config = config,
                            tokenizer = tokenizer,
                            inputIds = inputIds,
                        )
                    val sanitized = ChatTitleSanitizer.sanitize(generation.rawDecode)
                    val portTitle = portGenerateTitle(userPrompt)
                    Report(
                        userPrompt = userPrompt,
                        encoderPath = resolvedEncoderPath,
                        decoderPath = resolvedDecoderPath,
                        tokenizerPath = resolvedTokenizerPath,
                        modelSizeBytes = encoderSize,
                        modelSha256Prefix = encoderShaPrefix,
                        configPrefix = config.taskPrefix,
                        inputTokenCount = inputIds.size,
                        inputTailTokenIds = inputIds.takeLast(12).toList(),
                        onnxOutputShape = generation.outputShape,
                        generatedTokenIds = generation.tokenIds,
                        rawDecode = generation.rawDecode,
                        sanitizedTitle = sanitized,
                        portGenerateTitle = portTitle,
                        failureStage = generation.failureStage,
                        failureDetail = generation.failureDetail,
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Harness run failed", e)
            Report(
                userPrompt = userPrompt,
                encoderPath = resolvedEncoderPath,
                decoderPath = resolvedDecoderPath,
                tokenizerPath = resolvedTokenizerPath,
                modelSizeBytes = encoderSize,
                modelSha256Prefix = encoderShaPrefix,
                configPrefix = "",
                inputTokenCount = -1,
                inputTailTokenIds = emptyList(),
                onnxOutputShape = null,
                generatedTokenIds = emptyList(),
                rawDecode = null,
                sanitizedTitle = null,
                portGenerateTitle = runCatching { portGenerateTitle(userPrompt) }.getOrNull(),
                failureStage = "exception",
                failureDetail = "${e.javaClass.simpleName}: ${e.message}",
            )
        }
    }

    private data class GenerationResult(
        val tokenIds: List<Int>,
        val rawDecode: String?,
        val outputShape: String?,
        val failureStage: String?,
        val failureDetail: String?,
    )

    private fun runGeneration(
        encoderSession: OrtSession,
        decoderSession: OrtSession,
        environment: OrtEnvironment,
        config: TitleGenAssetConfig,
        tokenizer: T5UnigramTokenizer,
        inputIds: LongArray,
    ): GenerationResult {
        val encoderHiddenStates =
            runEncoder(
                environment = environment,
                session = encoderSession,
                inputIds = inputIds,
            )
                ?: return GenerationResult(
                    tokenIds = emptyList(),
                    rawDecode = null,
                    outputShape = null,
                    failureStage = "encoder_forward",
                    failureDetail = "Encoder returned no hidden states",
                )

        val seqLen = inputIds.size
        val attentionMask = LongArray(seqLen) { 1L }
        val encoderAttention =
            OnnxTensor.createTensor(
                environment,
                LongBuffer.wrap(attentionMask),
                longArrayOf(1, seqLen.toLong()),
            )
        var outputShape: String? = null
        val generated =
            try {
                TitleGenBeamDecoder.decode(config) { decoderIds ->
                    val decoderTensor =
                        OnnxTensor.createTensor(
                            environment,
                            LongBuffer.wrap(decoderIds),
                            longArrayOf(1, decoderIds.size.toLong()),
                        )
                    decoderTensor.use { tensor ->
                        val decoderInputs =
                            mapOf(
                                "input_ids" to tensor,
                                "encoder_attention_mask" to encoderAttention,
                                "encoder_hidden_states" to encoderHiddenStates,
                            )
                        decoderSession.run(decoderInputs).use { result ->
                            val logits = lastStepLogits(result[0].value)
                            outputShape = logits?.size?.let { vocab -> "[1, ${decoderIds.size}, $vocab]" }
                            logits
                        }
                    }
                }
            } finally {
                encoderAttention.close()
                encoderHiddenStates.close()
            }
        val rawDecode = tokenizer.decode(generated).trim()
        val stage =
            when {
                generated.isEmpty() -> "decode"
                rawDecode.isBlank() -> "decode"
                else -> null
            }
        val detail =
            when {
                generated.isEmpty() -> "No tokens generated before EOS/PAD or max steps"
                rawDecode.isBlank() -> "Decoded output blank after ${generated.size} tokens"
                else -> null
            }
        return GenerationResult(
            tokenIds = generated,
            rawDecode = rawDecode,
            outputShape = outputShape,
            failureStage = stage,
            failureDetail = detail,
        )
    }

    private fun runEncoder(
        environment: OrtEnvironment,
        session: OrtSession,
        inputIds: LongArray,
    ): OnnxTensor? {
        val seqLen = inputIds.size
        val attentionMask = LongArray(seqLen) { 1L }
        val inputs =
            mapOf(
                "input_ids" to
                    OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(inputIds),
                        longArrayOf(1, seqLen.toLong()),
                    ),
                "attention_mask" to
                    OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(attentionMask),
                        longArrayOf(1, seqLen.toLong()),
                    ),
            )
        return try {
            session.run(inputs).use { result ->
                result[0] as? OnnxTensor
            }
        } finally {
            inputs.values.forEach(OnnxTensor::close)
        }
    }

    private suspend fun unresolvedReport(
        userPrompt: String,
        encoderPath: String? = null,
        decoderPath: String? = null,
        tokenizerPath: String? = null,
        encoderSize: Long? = null,
        encoderShaPrefix: String? = null,
        portGenerateTitle: suspend (String) -> String?,
        stage: String,
        detail: String,
    ): Report =
        Report(
            userPrompt = userPrompt,
            encoderPath = encoderPath,
            decoderPath = decoderPath,
            tokenizerPath = tokenizerPath,
            modelSizeBytes = encoderSize,
            modelSha256Prefix = encoderShaPrefix,
            configPrefix = "",
            inputTokenCount = -1,
            inputTailTokenIds = emptyList(),
            onnxOutputShape = null,
            generatedTokenIds = emptyList(),
            rawDecode = null,
            sanitizedTitle = null,
            portGenerateTitle = runCatching { portGenerateTitle(userPrompt) }.getOrNull(),
            failureStage = stage,
            failureDetail = detail,
        )

    private suspend fun baseReport(
        userPrompt: String,
        encoderPath: String,
        decoderPath: String,
        tokenizerPath: String,
        encoderSize: Long?,
        encoderShaPrefix: String?,
        config: TitleGenAssetConfig,
        portGenerateTitle: suspend (String) -> String?,
        stage: String,
        detail: String,
    ): Report =
        Report(
            userPrompt = userPrompt,
            encoderPath = encoderPath,
            decoderPath = decoderPath,
            tokenizerPath = tokenizerPath,
            modelSizeBytes = encoderSize,
            modelSha256Prefix = encoderShaPrefix,
            configPrefix = config.taskPrefix,
            inputTokenCount = -1,
            inputTailTokenIds = emptyList(),
            onnxOutputShape = null,
            generatedTokenIds = emptyList(),
            rawDecode = null,
            sanitizedTitle = null,
            portGenerateTitle = runCatching { portGenerateTitle(userPrompt) }.getOrNull(),
            failureStage = stage,
            failureDetail = detail,
        )

    private fun sha256Prefix(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }.take(16)
    }

    private fun describeTitleAssetsOnDisk(context: Context): String {
        val modelsDir = File(context.getExternalFilesDir(null), ModelConfig.MODELS_DIR)
        if (!modelsDir.exists()) {
            return "modelsDir missing at ${modelsDir.absolutePath}"
        }
        val titleFiles =
            modelsDir
                .listFiles()
                ?.filter { file ->
                    file.name == "tokenizer.json" ||
                        file.name.startsWith("encoder_model") ||
                        file.name.startsWith("decoder_model")
                }
                ?.sortedBy { it.name }
                .orEmpty()
        if (titleFiles.isEmpty()) {
            return "no title-generator files under ${modelsDir.absolutePath}"
        }
        return titleFiles.joinToString(prefix = "onDisk=[", postfix = "]") { file ->
            "${file.name}:${file.length()}b:${sha256Prefix(file)}"
        }
    }

    private const val CONFIG_ASSET_PATH = "models/chat_title_generator/model_config.json"
}
