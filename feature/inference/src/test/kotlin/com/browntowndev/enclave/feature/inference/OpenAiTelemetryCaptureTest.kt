package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.feature.inference.telemetry.OpenAiTelemetryUsageInput
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OpenAiTelemetryCaptureTest {
    private val normalizer =
        ProviderTelemetryNormalizer(
            costEstimator = ProviderCostEstimator(SavedApiModelPricingEstimator()),
        )

    @Test
    fun `TEL_001 openai usage normalization captures exact cost and cache read tokens`() {
        val row = TelemetryTestFixtures.telOpenAi1
        val captured = normalizer.normalizeOpenAiUsage(row.toOpenAiInput(providerLabel = "OpenAI"))

        assertEquals("0.002500", captured.costUsd?.toPlainString())
        assertEquals(TelemetryCostStatus.EXACT, captured.costStatus)
        assertEquals(TelemetryCostSource.PROVIDER, captured.costSource)
        assertEquals(400, captured.cacheReadInputTokens)
        assertEquals(
            400L,
            captured.firstOutputTokenAtMs!! - captured.requestStartedAtMs,
        )
    }

    @Test
    fun `xAI usage without saved pricing leaves cost unavailable when tokens present`() {
        val captured =
            normalizer.normalizeOpenAiUsage(
                OpenAiTelemetryUsageInput(
                    rowId = "tel-xai-1",
                    messageId = "msg-1",
                    chatId = "chat-1",
                    sequenceIndex = 0,
                    providerLabel = "xAI",
                    modelId = "grok-4.3",
                    modelLabel = "grok-4.3",
                    requestStartedAtMs = 1_000L,
                    firstOutputTokenAtMs = 1_400L,
                    completedAtMs = 5_000L,
                    inputTokens = 1_000,
                    outputTokens = 200,
                    reasoningTokens = 80,
                    cacheReadInputTokens = 400,
                    cacheWriteInputTokens = null,
                    costUsd = null,
                    clientBuild = TelemetryTestFixtures.telOpenAi1.clientBuild,
                ),
            )

        assertEquals(TelemetryProviderKey.XAI, captured.providerKey)
        assertEquals(TelemetryCostStatus.UNAVAILABLE, captured.costStatus)
        assertEquals(TelemetryCostSource.NOT_AVAILABLE, captured.costSource)
        assertEquals(null, captured.costUsd)
        assertEquals(400, captured.cacheReadInputTokens)
        assertEquals(80, captured.reasoningTokens)
    }

    @Test
    fun `xAI usage estimates from saved model pricing when configured`() {
        val captured =
            normalizer.normalizeOpenAiUsage(
                OpenAiTelemetryUsageInput(
                    rowId = "tel-xai-saved",
                    messageId = "msg-1",
                    chatId = "chat-1",
                    sequenceIndex = 0,
                    providerLabel = "xAI",
                    modelId = "grok-4.3",
                    modelLabel = "grok-4.3",
                    requestStartedAtMs = 1_000L,
                    firstOutputTokenAtMs = 1_400L,
                    completedAtMs = 5_000L,
                    inputTokens = 1_000,
                    outputTokens = 200,
                    reasoningTokens = 80,
                    cacheReadInputTokens = 400,
                    cacheWriteInputTokens = null,
                    costUsd = null,
                    savedPricing =
                        ApiModelSavedPricing(
                            promptUsdPerMillion = 1.25,
                            completionUsdPerMillion = 2.5,
                            cachedPromptUsdPerMillion = 0.2,
                        ),
                    clientBuild = TelemetryTestFixtures.telOpenAi1.clientBuild,
                ),
            )

        assertEquals(TelemetryCostStatus.ESTIMATED, captured.costStatus)
        assertEquals(TelemetryCostSource.SAVED_MODEL_PRICING, captured.costSource)
        assertEquals(true, captured.costUsd != null && captured.costUsd!! > BigDecimal.ZERO)
    }

    @Test
    fun `openrouter usage normalization captures exact provider cost and cache fields`() {
        val row = TelemetryTestFixtures.telOpenRouter1
        val captured = normalizer.normalizeOpenAiUsage(row.toOpenAiInput(providerLabel = "OpenRouter"))

        assertEquals(TelemetryProviderKey.OPENROUTER, captured.providerKey)
        assertEquals("0.001200", captured.costUsd?.toPlainString())
        assertEquals(TelemetryCostStatus.EXACT, captured.costStatus)
        assertEquals(100, captured.cacheReadInputTokens)
        assertEquals(20, captured.cacheWriteInputTokens)
    }

    private fun TurnTelemetry.toOpenAiInput(providerLabel: String): OpenAiTelemetryUsageInput =
        OpenAiTelemetryUsageInput(
            rowId = id.value,
            messageId = messageId.value,
            chatId = chatId.value,
            sequenceIndex = sequenceIndex,
            providerLabel = providerLabel,
            modelId = modelId,
            modelLabel = modelLabel,
            requestStartedAtMs = requestStartedAtMs,
            firstOutputTokenAtMs = firstOutputTokenAtMs,
            completedAtMs = completedAtMs,
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            reasoningTokens = reasoningTokens,
            cacheReadInputTokens = cacheReadInputTokens,
            cacheWriteInputTokens = cacheWriteInputTokens,
            costUsd = costUsd,
            clientBuild = clientBuild,
        )
}
