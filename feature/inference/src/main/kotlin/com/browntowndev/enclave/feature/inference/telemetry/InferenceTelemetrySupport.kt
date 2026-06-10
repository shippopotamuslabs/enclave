package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class InferenceTurnTiming {
    val requestStartedAtMs: Long = System.currentTimeMillis()
    var firstOutputTokenAtMs: Long? = null

    fun markFirstOutput() {
        if (firstOutputTokenAtMs == null) {
            firstOutputTokenAtMs = System.currentTimeMillis()
        }
    }

    fun completedAtMs(): Long = System.currentTimeMillis()
}

@Singleton
class InferenceTelemetrySupport
    @Inject
    constructor(
        private val normalizer: ProviderTelemetryNormalizer,
        private val clientBuildProvider: TelemetryClientBuildProvider,
    ) {
        data class CaptureContext(
            val messageId: MessageId,
            val chatId: ChatId,
            val sequenceIndex: Int,
            val clientBuild: TelemetryClientBuild,
        )

        fun captureContext(options: GenerationOptions): CaptureContext? {
            val messageId = options.assistantMessageId ?: return null
            val chatId = options.chatId ?: return null
            return CaptureContext(
                messageId = messageId,
                chatId = chatId,
                sequenceIndex = options.telemetrySequenceIndex,
                clientBuild = clientBuildProvider.current(),
            )
        }

        suspend fun emitCaptured(
            telemetry: TurnTelemetry?,
            modelType: ModelType,
            emit: suspend (InferenceEvent) -> Unit,
        ) {
            if (telemetry != null) {
                emit(InferenceEvent.TelemetryCaptured(telemetry, modelType))
            }
        }

        fun openAiTurnTelemetry(
            context: CaptureContext,
            providerLabel: String,
            modelId: String,
            modelLabel: String,
            timing: InferenceTurnTiming,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
            cacheWriteInputTokens: Int? = null,
            costUsd: BigDecimal?,
            savedPricing: ApiModelSavedPricing? = null,
        ): TurnTelemetry =
            normalizer.normalizeOpenAiUsage(
                OpenAiTelemetryUsageInput(
                    rowId = newRowId(),
                    messageId = context.messageId.value,
                    chatId = context.chatId.value,
                    sequenceIndex = context.sequenceIndex,
                    providerLabel = providerLabel,
                    modelId = modelId,
                    modelLabel = modelLabel,
                    requestStartedAtMs = timing.requestStartedAtMs,
                    firstOutputTokenAtMs = timing.firstOutputTokenAtMs,
                    completedAtMs = timing.completedAtMs(),
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheReadInputTokens = cacheReadInputTokens,
                    cacheWriteInputTokens = cacheWriteInputTokens,
                    costUsd = costUsd,
                    savedPricing = savedPricing,
                    clientBuild = context.clientBuild,
                ),
            )

        fun anthropicTurnTelemetry(
            context: CaptureContext,
            modelId: String,
            modelLabel: String,
            timing: InferenceTurnTiming,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheCreationInputTokens: Int?,
            cacheReadInputTokens: Int? = null,
            savedPricing: ApiModelSavedPricing? = null,
        ): TurnTelemetry =
            normalizer.normalizeAnthropicUsage(
                AnthropicTelemetryUsageInput(
                    rowId = newRowId(),
                    messageId = context.messageId.value,
                    chatId = context.chatId.value,
                    sequenceIndex = context.sequenceIndex,
                    modelId = modelId,
                    modelLabel = modelLabel,
                    requestStartedAtMs = timing.requestStartedAtMs,
                    firstOutputTokenAtMs = timing.firstOutputTokenAtMs,
                    completedAtMs = timing.completedAtMs(),
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheCreationInputTokens = cacheCreationInputTokens,
                    cacheReadInputTokens = cacheReadInputTokens,
                    savedPricing = savedPricing,
                    clientBuild = context.clientBuild,
                ),
            )

        fun localTurnTelemetry(
            context: CaptureContext,
            providerLabel: String,
            modelLabel: String,
            timing: InferenceTurnTiming,
            outputTokens: Int?,
            inputTokens: Int? = null,
            cacheReadInputTokens: Int? = null,
            reasoningTokens: Int? = null,
        ): TurnTelemetry =
            normalizer.normalizeLocalGeneration(
                rowId = newRowId(),
                messageId = context.messageId.value,
                chatId = context.chatId.value,
                providerLabel = providerLabel,
                modelLabel = modelLabel,
                requestStartedAtMs = timing.requestStartedAtMs,
                firstOutputTokenAtMs = timing.firstOutputTokenAtMs,
                completedAtMs = timing.completedAtMs(),
                outputTokens = outputTokens,
                appVersionName = context.clientBuild.appVersionName,
                appVersionCode = context.clientBuild.appVersionCode,
                telemetrySchemaVersion = context.clientBuild.telemetrySchemaVersion,
                sequenceIndex = context.sequenceIndex,
                inputTokens = inputTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                reasoningTokens = reasoningTokens,
            )

        fun providerLabel(provider: String): String =
            when (provider.uppercase()) {
                ApiProvider.OPENAI.name -> "OpenAI"
                ApiProvider.ANTHROPIC.name -> "Anthropic"
                ApiProvider.GOOGLE.name -> "Google"
                ApiProvider.XAI.name -> "xAI"
                "OPENROUTER" -> "OpenRouter"
                "LITERT", "LITERTLM" -> "LiteRT"
                "LLAMA" -> "llama.cpp"
                else -> provider
            }

        private fun newRowId(): String = UUID.randomUUID().toString()
    }
