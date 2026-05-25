package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.feature.inference.telemetry.AnthropicTelemetryUsageInput
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderPricingCatalog
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnthropicTelemetryCaptureTest {
    @Test
    fun `TEL_009 exact versus estimated cost is labeled correctly at response level`() {
        val normalizer =
            ProviderTelemetryNormalizer(
                costEstimator = ProviderCostEstimator(ProviderPricingCatalog()),
            )
        val row = TelemetryTestFixtures.telAnthropic1
        val captured =
            normalizer.normalizeAnthropicUsage(
                AnthropicTelemetryUsageInput(
                    rowId = row.id.value,
                    messageId = row.messageId.value,
                    chatId = row.chatId.value,
                    sequenceIndex = row.sequenceIndex,
                    modelId = row.modelId,
                    modelLabel = row.modelLabel,
                    requestStartedAtMs = row.requestStartedAtMs,
                    firstOutputTokenAtMs = row.firstOutputTokenAtMs,
                    completedAtMs = row.completedAtMs,
                    inputTokens = row.inputTokens,
                    outputTokens = row.outputTokens,
                    reasoningTokens = row.reasoningTokens,
                    cacheCreationInputTokens = row.cacheCreationInputTokens,
                    clientBuild = row.clientBuild,
                ),
            )

        assertEquals("0.003000", captured.costUsd?.toPlainString())
        assertEquals(TelemetryCostStatus.ESTIMATED, captured.costStatus)
        assertEquals(TelemetryCostSource.LOCAL_PRICING_TABLE, captured.costSource)
    }
}
