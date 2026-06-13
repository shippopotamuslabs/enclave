package com.browntowndev.enclave.domain.usecase.telemetry

import app.cash.turbine.test
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ObserveChatTelemetryUseCaseTest {
    @Test
    fun `TEL_005 chat stats summary aggregates totals and breakdowns correctly`() =
        runTest {
            val useCase =
                ObserveChatTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        chatRows = mapOf(TelemetryTestFixtures.chat1 to TelemetryTestFixtures.chat1Rows),
                    ),
                )

            useCase(TelemetryTestFixtures.chat1).test {
                val summary = awaitItem()
                assertEquals(BigDecimal("0.005500"), summary.totalCostUsd)
                assertEquals(400L, summary.averageTtftMs)
                assertEquals(45.0, summary.averageOutputTokensPerSecond)
                assertEquals(360, summary.totalOutputTokens)
                assertEquals(130, summary.totalReasoningTokens)
                assertEquals(19.0, summary.cacheHitRatePercent)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `TEL_006 historical provider model breakdown uses persisted telemetry not current default assignment`() =
        runTest {
            val useCase =
                ObserveChatTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        chatRows = mapOf(TelemetryTestFixtures.chat1 to TelemetryTestFixtures.chat1Rows),
                        currentDefaultProviderLabel = "xAI",
                        currentDefaultModelLabel = "grok-4.3",
                    ),
                )

            useCase(TelemetryTestFixtures.chat1).test {
                val summary = awaitItem()
                val openAiRow =
                    summary.providerBreakdown.singleOrNull {
                        it.providerLabel == "OpenAI"
                    }
                assertTrue(openAiRow != null)
                assertEquals(BigDecimal("0.002500"), openAiRow!!.totalCostUsd)
                assertEquals(1, openAiRow.turnCount)
                assertFalse(
                    summary.providerBreakdown.any { it.providerLabel == "xAI" },
                )
                cancelAndIgnoreRemainingEvents()
            }
        }
}
