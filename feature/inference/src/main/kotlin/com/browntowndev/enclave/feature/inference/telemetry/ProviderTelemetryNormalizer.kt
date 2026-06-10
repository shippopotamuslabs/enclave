package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId
import javax.inject.Inject

class ProviderTelemetryNormalizer
    @Inject
    constructor(
        private val costEstimator: ProviderCostEstimator,
    ) {
        fun normalizeOpenAiUsage(input: OpenAiTelemetryUsageInput): TurnTelemetry {
            val providerKey = providerKeyForLabel(input.providerLabel)
            val cost =
                if (input.costUsd != null) {
                    CostEstimate(
                        costUsd = input.costUsd,
                        costStatus = TelemetryCostStatus.EXACT,
                        costSource = TelemetryCostSource.PROVIDER,
                    )
                } else {
                    costEstimator.estimateOpenAiCompatible(
                        inputTokens = input.inputTokens,
                        outputTokens = input.outputTokens,
                        reasoningTokens = input.reasoningTokens,
                        cacheReadInputTokens = input.cacheReadInputTokens,
                        savedPricing = input.savedPricing,
                    )
                }
            return TurnTelemetry(
                id = TurnTelemetryId(input.rowId),
                messageId = MessageId(input.messageId),
                chatId = ChatId(input.chatId),
                sequenceIndex = input.sequenceIndex,
                providerKey = providerKey,
                providerLabel = input.providerLabel,
                modelId = input.modelId,
                modelLabel = input.modelLabel,
                sourceKind = TelemetrySourceKind.REMOTE,
                requestStartedAtMs = input.requestStartedAtMs,
                firstOutputTokenAtMs = input.firstOutputTokenAtMs,
                completedAtMs = input.completedAtMs,
                inputTokens = input.inputTokens,
                outputTokens = input.outputTokens,
                reasoningTokens = input.reasoningTokens,
                cachedInputTokens = null,
                cacheReadInputTokens = input.cacheReadInputTokens,
                cacheWriteInputTokens = input.cacheWriteInputTokens,
                cacheCreationInputTokens = null,
                costUsd = cost.costUsd,
                costStatus = cost.costStatus,
                costSource = cost.costSource,
                clientBuild = input.clientBuild,
            )
        }

        fun normalizeAnthropicUsage(input: AnthropicTelemetryUsageInput): TurnTelemetry {
            val estimate =
                costEstimator.estimateAnthropic(
                    inputTokens = input.inputTokens,
                    outputTokens = input.outputTokens,
                    reasoningTokens = input.reasoningTokens,
                    cacheCreationInputTokens = input.cacheCreationInputTokens,
                    cacheReadInputTokens = input.cacheReadInputTokens,
                    savedPricing = input.savedPricing,
                )
            return TurnTelemetry(
                id = TurnTelemetryId(input.rowId),
                messageId = MessageId(input.messageId),
                chatId = ChatId(input.chatId),
                sequenceIndex = input.sequenceIndex,
                providerKey = TelemetryProviderKey.ANTHROPIC,
                providerLabel = "Anthropic",
                modelId = input.modelId,
                modelLabel = input.modelLabel,
                sourceKind = TelemetrySourceKind.REMOTE,
                requestStartedAtMs = input.requestStartedAtMs,
                firstOutputTokenAtMs = input.firstOutputTokenAtMs,
                completedAtMs = input.completedAtMs,
                inputTokens = input.inputTokens,
                outputTokens = input.outputTokens,
                reasoningTokens = input.reasoningTokens,
                cachedInputTokens = null,
                cacheReadInputTokens = input.cacheReadInputTokens,
                cacheWriteInputTokens = null,
                cacheCreationInputTokens = input.cacheCreationInputTokens,
                costUsd = estimate.costUsd,
                costStatus = estimate.costStatus,
                costSource = estimate.costSource,
                clientBuild = input.clientBuild,
            )
        }

        @Suppress("LongParameterList")
        fun normalizeLocalGeneration(
            rowId: String,
            messageId: String,
            chatId: String,
            providerLabel: String,
            modelLabel: String,
            requestStartedAtMs: Long,
            firstOutputTokenAtMs: Long?,
            completedAtMs: Long?,
            outputTokens: Int?,
            appVersionName: String,
            appVersionCode: Long,
            telemetrySchemaVersion: Int,
            sequenceIndex: Int = 0,
            inputTokens: Int? = null,
            cacheReadInputTokens: Int? = null,
            reasoningTokens: Int? = null,
        ): TurnTelemetry =
            TurnTelemetry(
                id = TurnTelemetryId(rowId),
                messageId = MessageId(messageId),
                chatId = ChatId(chatId),
                sequenceIndex = sequenceIndex,
                providerKey = providerKeyForLabel(providerLabel),
                providerLabel = providerLabel,
                modelId = modelLabel,
                modelLabel = modelLabel,
                sourceKind = TelemetrySourceKind.LOCAL,
                requestStartedAtMs = requestStartedAtMs,
                firstOutputTokenAtMs = firstOutputTokenAtMs,
                completedAtMs = completedAtMs,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cachedInputTokens = null,
                cacheReadInputTokens = cacheReadInputTokens,
                cacheWriteInputTokens = null,
                cacheCreationInputTokens = null,
                costUsd = null,
                costStatus = TelemetryCostStatus.UNAVAILABLE,
                costSource = TelemetryCostSource.NOT_AVAILABLE,
                clientBuild =
                    TelemetryClientBuild(
                        appVersionName = appVersionName,
                        appVersionCode = appVersionCode,
                        telemetrySchemaVersion = telemetrySchemaVersion,
                    ),
            )

        private fun providerKeyForLabel(label: String): TelemetryProviderKey =
            when (label) {
                "OpenAI" -> TelemetryProviderKey.OPENAI
                "Anthropic" -> TelemetryProviderKey.ANTHROPIC
                "LiteRT" -> TelemetryProviderKey.LITERT
                "llama.cpp" -> TelemetryProviderKey.LLAMA
                "OpenRouter" -> TelemetryProviderKey.OPENROUTER
                "Google" -> TelemetryProviderKey.GOOGLE
                "xAI" -> TelemetryProviderKey.XAI
                else -> TelemetryProviderKey.OTHER
            }
    }
