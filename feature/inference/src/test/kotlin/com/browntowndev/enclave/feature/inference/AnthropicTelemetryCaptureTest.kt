package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.feature.inference.telemetry.AnthropicTelemetryUsageInput
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnthropicTelemetryCaptureTest {
    @Test
    fun `TEL_009 exact versus estimated cost is labeled correctly at response level`() {
        val normalizer =
            ProviderTelemetryNormalizer(
                costEstimator = ProviderCostEstimator(SavedApiModelPricingEstimator()),
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
                    savedPricing =
                        ApiModelSavedPricing(
                            promptUsdPerMillion = 3.0,
                            completionUsdPerMillion = 15.0,
                        ),
                    clientBuild = row.clientBuild,
                ),
            )

        assertEquals(true, captured.costUsd != null)
        assertEquals(TelemetryCostStatus.ESTIMATED, captured.costStatus)
        assertEquals(TelemetryCostSource.SAVED_MODEL_PRICING, captured.costSource)
    }
}
