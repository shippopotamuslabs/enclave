package com.browntowndev.enclave.domain.usecase.telemetry

import app.cash.turbine.test
import com.browntowndev.enclave.domain.model.telemetry.ProviderUsageSpendRow
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ObserveUsageSpendSummaryUseCaseTest {
    @Test
    fun `TEL_008 settings current month filter excludes prior month rows`() =
        runTest {
            val useCase =
                ObserveUsageSpendSummaryUseCase(
                    repository =
                        FakeInferenceTelemetryRepositoryPort(
                            usageCurrentMonth =
                                UsageSpendSummary(
                                    period = UsagePeriod.CURRENT_MONTH,
                                    periodStartUtcMs = null,
                                    periodEndUtcMs = null,
                                    totalCostUsd = BigDecimal("0.005500"),
                                    telemetryBackedTurnCount = 3,
                                    missingCostTurnCount = 1,
                                    providerBreakdown =
                                        listOf(
                                            ProviderUsageSpendRow("OpenAI", BigDecimal("0.002500"), 1),
                                            ProviderUsageSpendRow("Anthropic", BigDecimal("0.003000"), 1),
                                            ProviderUsageSpendRow("LiteRT", null, 1),
                                        ),
                                ),
                        ),
                )

            useCase(UsagePeriod.CURRENT_MONTH).test {
                val summary = awaitItem()
                assertEquals(BigDecimal("0.005500"), summary.totalCostUsd)
                assertFalse(summary.providerBreakdown.any { it.providerLabel == "OpenRouter" })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
