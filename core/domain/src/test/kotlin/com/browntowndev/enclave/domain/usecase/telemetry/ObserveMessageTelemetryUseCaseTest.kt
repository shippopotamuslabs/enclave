package com.browntowndev.enclave.domain.usecase.telemetry

import app.cash.turbine.test
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ObserveMessageTelemetryUseCaseTest {
    @Test
    fun `TEL_003 historical message without telemetry renders unavailable state`() =
        runTest {
            val useCase =
                ObserveMessageTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        messageRows = mapOf(TelemetryTestFixtures.mLegacy1 to emptyList()),
                    ),
                )

            useCase(TelemetryTestFixtures.mLegacy1).test {
                val summary = awaitItem()
                assertEquals(TelemetryTestFixtures.mLegacy1, summary.messageId)
                assertEquals(0, summary.requestCount)
                assertTrue(summary.requestRows.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `TEL_013 multiple model request telemetry rows aggregate into one response and chat summary`() =
        runTest {
            val useCase =
                ObserveMessageTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        messageRows =
                            mapOf(
                                TelemetryTestFixtures.mAgent1 to
                                    listOf(
                                        TelemetryTestFixtures.telAgent1,
                                        TelemetryTestFixtures.telAgent2,
                                    ),
                            ),
                    ),
                )

            useCase(TelemetryTestFixtures.mAgent1).test {
                val summary = awaitItem()
                assertEquals(2, summary.requestCount)
                assertEquals(100, summary.totalOutputTokens)
                assertEquals(BigDecimal("0.003000"), summary.totalCostUsd)
                assertEquals(400L, summary.averageTtftMs)
                assertEquals(25.0, summary.averageOutputTokensPerSecond)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `multiple providers on one message use multiple providers label`() =
        runTest {
            val useCase =
                ObserveMessageTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        messageRows =
                            mapOf(
                                TelemetryTestFixtures.mAgent1 to
                                    listOf(
                                        TelemetryTestFixtures.telAgent1,
                                        TelemetryTestFixtures.telAnthropic1.copy(
                                            id = com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId("tel-agent-mix"),
                                            messageId = TelemetryTestFixtures.mAgent1,
                                            sequenceIndex = 1,
                                        ),
                                    ),
                            ),
                    ),
                )

            useCase(TelemetryTestFixtures.mAgent1).test {
                val summary = awaitItem()
                assertEquals("Multiple providers", summary.providerLabel)
                assertEquals("Multiple models", summary.modelLabel)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
