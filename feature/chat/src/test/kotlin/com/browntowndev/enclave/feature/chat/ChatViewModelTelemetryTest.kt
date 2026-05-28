package com.browntowndev.enclave.feature.chat

import app.cash.turbine.test
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.feature.chat.stats.ChatTelemetryUiMapper
import com.browntowndev.enclave.feature.chat.stats.ResponseStatsSheetUiState
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatViewModelTelemetryTest {
    @Test
    fun `TEL_001 response stats sheet shows exact remote provider telemetry values`() =
        runTest {
            val useCase =
                ObserveMessageTelemetryUseCase(
                    FakeInferenceTelemetryRepositoryPort(
                        messageRows = mapOf(TelemetryTestFixtures.mOpenAi1 to listOf(TelemetryTestFixtures.telOpenAi1)),
                    ),
                )
            val mapper = ChatTelemetryUiMapper()

            useCase(TelemetryTestFixtures.mOpenAi1).test {
                val sheet =
                    mapper.mapResponseStats(awaitItem()).copy(
                        isVisible = true,
                        title = "Response statistics",
                    )
                assertResponseStats(sheet)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun assertResponseStats(sheet: ResponseStatsSheetUiState) {
        assertEquals("Response statistics", sheet.title)
        assertEquals("OpenAI", sheet.providerLabel)
        assertEquals("gpt-4.1", sheet.modelLabel)
        assertEquals("400 ms", sheet.metricRows.first { it.label == "Time to first token" }.value)
        assertEquals("50.0 tok/s", sheet.metricRows.first { it.label == "Output speed" }.value)
        assertEquals("200", sheet.metricRows.first { it.label == "Output tokens" }.value)
        assertEquals("80", sheet.metricRows.first { it.label == "Reasoning tokens" }.value)
        assertEquals("1000", sheet.metricRows.first { it.label == "Input tokens" }.value)
        assertEquals("400", sheet.metricRows.first { it.label == "Cache read input tokens" }.value)
        assertEquals("$0.002500", sheet.metricRows.first { it.label == "Cost" }.value)
        assertEquals("Exact", sheet.metricRows.first { it.label == "Cost status" }.value)
    }
}
