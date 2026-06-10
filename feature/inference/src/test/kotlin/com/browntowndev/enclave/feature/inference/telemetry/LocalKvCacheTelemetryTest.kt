package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LocalKvCacheTelemetryTest {
    @Test
    fun `local generation with kv prefix reuse computes cache hit rate from prompt tokens`() {
        val row =
            ProviderTelemetryNormalizer(ProviderCostEstimator(SavedApiModelPricingEstimator()))
                .normalizeLocalGeneration(
                    rowId = "tel-llama-kv",
                    messageId = TelemetryTestFixtures.mLitert1.value,
                    chatId = TelemetryTestFixtures.chat1.value,
                    providerLabel = "llama.cpp",
                    modelLabel = "test-llama",
                    requestStartedAtMs = 1_000L,
                    firstOutputTokenAtMs = 1_200L,
                    completedAtMs = 3_200L,
                    outputTokens = 50,
                    appVersionName = "1.4.0",
                    appVersionCode = 140L,
                    telemetrySchemaVersion = 1,
                    inputTokens = 200,
                    cacheReadInputTokens = 150,
                )

        assertEquals(TelemetrySourceKind.LOCAL, row.sourceKind)
        assertEquals(200, row.inputTokens)
        assertEquals(150, row.cacheReadInputTokens)

        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mLitert1,
                rows = listOf(row),
            )
        assertEquals(75.0, summary.cacheHitRatePercent)
    }
}
