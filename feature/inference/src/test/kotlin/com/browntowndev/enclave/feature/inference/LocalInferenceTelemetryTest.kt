package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.feature.inference.telemetry.ProviderCostEstimator
import com.browntowndev.enclave.feature.inference.telemetry.ProviderTelemetryNormalizer
import com.browntowndev.enclave.feature.inference.telemetry.SavedApiModelPricingEstimator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalInferenceTelemetryTest {
    @Test
    fun `TEL_002 local inference telemetry exposes timing and unavailable cost fields`() {
        val row = TelemetryTestFixtures.telLitert1
        val captured =
            ProviderTelemetryNormalizer(ProviderCostEstimator(SavedApiModelPricingEstimator()))
                .normalizeLocalGeneration(
                    rowId = row.id.value,
                    messageId = row.messageId.value,
                    chatId = row.chatId.value,
                    providerLabel = "LiteRT",
                    modelLabel = row.modelLabel,
                    requestStartedAtMs = row.requestStartedAtMs,
                    firstOutputTokenAtMs = row.firstOutputTokenAtMs,
                    completedAtMs = row.completedAtMs,
                    outputTokens = row.outputTokens,
                    appVersionName = row.clientBuild.appVersionName,
                    appVersionCode = row.clientBuild.appVersionCode,
                    telemetrySchemaVersion = row.clientBuild.telemetrySchemaVersion,
                )

        assertEquals(TelemetrySourceKind.LOCAL, captured.sourceKind)
        assertEquals(
            200L,
            captured.firstOutputTokenAtMs!! - captured.requestStartedAtMs,
        )
        val durationMs = captured.completedAtMs!! - captured.firstOutputTokenAtMs!!
        assertEquals(30.0, captured.outputTokens!!.toDouble() / (durationMs / 1000.0), 0.1)
        assertEquals(60, captured.outputTokens)
        assertNull(captured.inputTokens)
        assertNull(captured.costUsd)
        assertEquals(TelemetryCostStatus.UNAVAILABLE, captured.costStatus)
    }

    @Test
    fun `local inference telemetry threads reasoning token estimate`() {
        val captured =
            ProviderTelemetryNormalizer(ProviderCostEstimator(SavedApiModelPricingEstimator()))
                .normalizeLocalGeneration(
                    rowId = "tel-litert-reasoning",
                    messageId = TelemetryTestFixtures.mLitert1.value,
                    chatId = TelemetryTestFixtures.chat1.value,
                    providerLabel = "LiteRT",
                    modelLabel = "test-litert",
                    requestStartedAtMs = 1_000L,
                    firstOutputTokenAtMs = 1_200L,
                    completedAtMs = 3_200L,
                    outputTokens = 60,
                    appVersionName = "1.4.0",
                    appVersionCode = 140L,
                    telemetrySchemaVersion = 1,
                    reasoningTokens = 25,
                )

        assertEquals(25, captured.reasoningTokens)
    }
}
