package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import java.math.BigDecimal
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.feature.inference.telemetry.OpenAiTelemetryUsageInput
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderPricingCatalog
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OpenAiTelemetryCaptureTest {
    private val normalizer =
        ProviderTelemetryNormalizer(
            costEstimator = ProviderCostEstimator(ProviderPricingCatalog()),
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
    fun `xAI usage without provider cost estimates from local pricing when tokens present`() {
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
        assertEquals(TelemetryCostStatus.ESTIMATED, captured.costStatus)
        assertEquals(TelemetryCostSource.LOCAL_PRICING_TABLE, captured.costSource)
        assertEquals(true, captured.costUsd != null && captured.costUsd!! > BigDecimal.ZERO)
        assertEquals(400, captured.cacheReadInputTokens)
        assertEquals(80, captured.reasoningTokens)
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

    private fun TurnTelemetry.toOpenAiInput(
        providerLabel: String,
    ): OpenAiTelemetryUsageInput =
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
