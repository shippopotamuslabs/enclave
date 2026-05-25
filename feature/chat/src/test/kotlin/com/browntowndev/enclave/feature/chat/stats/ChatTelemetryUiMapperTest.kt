package com.browntowndev.enclave.feature.chat.stats

import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChatTelemetryUiMapperTest {
    private val mapper = ChatTelemetryUiMapper()

    @Test
    fun `TEL_003 historical message without telemetry renders unavailable state`() {
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mLegacy1,
                rows = emptyList(),
            )
        val sheet = mapper.mapResponseStats(summary)

        assertEquals("Telemetry unavailable for this response", sheet.availabilityMessage)
        assertEquals("Not available", sheet.providerLabel)
        assertEquals("Not available", sheet.modelLabel)
        assertEquals("Not available", sheet.metricRows.first { it.label == "Provider" }.value)
        assertEquals("Not available", sheet.metricRows.first { it.label == "Cost" }.value)
    }

    @Test
    fun `TEL_002 response stats sheet shows local model timing values and unavailable fields`() {
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mLitert1,
                rows = listOf(TelemetryTestFixtures.telLitert1),
            )
        val sheet = mapper.mapResponseStats(summary)

        assertEquals("LiteRT", sheet.providerLabel)
        assertEquals("200 ms", sheet.metricRows.first { it.label == "Time to first token" }.value)
        assertEquals("30.0 tok/s", sheet.metricRows.first { it.label == "Output speed" }.value)
        assertEquals("Not available", sheet.metricRows.first { it.label == "Cost" }.value)
        assertEquals("Not available", sheet.metricRows.first { it.label == "Input tokens" }.value)
    }

    @Test
    fun `TEL_013 chat stats provider breakdown includes request count for multi request turns`() {
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(TelemetryTestFixtures.telAgent1, TelemetryTestFixtures.telAgent2),
            )
        val sheet = mapper.mapChatStats(summary)
        val openAiRow = sheet.providerBreakdownRows.single { it.label == "OpenAI" }

        assertEquals("$0.003000", openAiRow.costLabel)
        assertEquals(1, openAiRow.turnCount)
        assertEquals(2, openAiRow.requestCount)
    }
}
