package com.browntowndev.enclave.feature.chat.stats

import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId
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

    @Test
    fun `chat stats summary rows include cache read and cache write tokens`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                cacheWriteInputTokens = null,
                cacheReadInputTokens = 8000,
                cacheCreationInputTokens = 500,
            )
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(row),
            )
        val sheet = mapper.mapChatStats(summary)

        assertEquals("8000", sheet.summaryRows.first { it.label == "Cache read tokens" }.value)
        assertEquals("500", sheet.summaryRows.first { it.label == "Cache write tokens" }.value)
    }

    @Test
    fun `chat stats cache rows show 0 when no cache telemetry exists`() {
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(TelemetryTestFixtures.telAgent1),
            )
        val sheet = mapper.mapChatStats(summary)

        assertEquals("0", sheet.summaryRows.first { it.label == "Cache read tokens" }.value)
        assertEquals("0", sheet.summaryRows.first { it.label == "Cache write tokens" }.value)
    }

    @Test
    fun `chat stats summary includes peak input tokens and billing prompt total rows`() {
        val messageId = MessageId("m-tool-loop")
        val hop1 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-hop-1"),
                messageId = messageId,
                chatId = TelemetryTestFixtures.chat1,
                inputTokens = 20_000,
            )
        val hop2 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-hop-2"),
                messageId = messageId,
                chatId = TelemetryTestFixtures.chat1,
                inputTokens = 26_240,
            )
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(hop1, hop2),
            )
        val sheet = mapper.mapChatStats(summary)

        assertEquals("26240", sheet.summaryRows.first { it.label == "Input tokens" }.value)
        assertEquals(
            "46240",
            sheet.summaryRows.first { it.label == "Total prompt tokens (all API calls)" }.value,
        )
    }

    @Test
    fun `chat stats summary includes normalized input tokens row`() {
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(TelemetryTestFixtures.telOpenAi1),
            )
        val sheet = mapper.mapChatStats(summary)

        // The chat-level sheet must surface input tokens (previously omitted entirely).
        val inputRow = sheet.summaryRows.first { it.label == "Input tokens" }
        // OpenAI fixture: inputTokens=1000, cache read is a subset — normalized stays 1000.
        assertEquals("1000", inputRow.value)
    }

    @Test
    fun `chat stats normalized input tokens add Anthropic cache read and creation`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                chatId = TelemetryTestFixtures.chat1,
                inputTokens = 800,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(row),
            )
        val sheet = mapper.mapChatStats(summary)

        // Anthropic reports non-cached input only; normalized adds cache read + creation for display.
        assertEquals((800 + 16_000 + 3_000).toString(),
            sheet.summaryRows.first { it.label == "Input tokens" }.value)
    }

    @Test
    fun `response stats Input tokens row normalizes Anthropic cache tokens`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                inputTokens = 7_787,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mAnthropic1,
                rows = listOf(row),
            )
        val sheet = mapper.mapResponseStats(summary)

        assertEquals((7_787 + 16_000 + 3_000).toString(),
            sheet.metricRows.first { it.label == "Input tokens" }.value)
    }

    @Test
    fun `response stats Input tokens row stays raw for OpenAI compatible providers`() {
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mOpenAi1,
                rows = listOf(TelemetryTestFixtures.telOpenAi1),
            )
        val sheet = mapper.mapResponseStats(summary)

        // OpenAI: cache read is a subset of input; normalized equals raw input.
        assertEquals("1000", sheet.metricRows.first { it.label == "Input tokens" }.value)
    }

    @Test
    fun `response stats Input tokens row peaks across tool-loop hops`() {
        val messageId = TelemetryTestFixtures.mOpenAi1
        val hop1 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-hop-1"),
                messageId = messageId,
                inputTokens = 20_000,
            )
        val hop2 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-hop-2"),
                messageId = messageId,
                inputTokens = 26_240,
            )
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = messageId,
                rows = listOf(hop1, hop2),
            )
        val sheet = mapper.mapResponseStats(summary)

        assertEquals("26240", sheet.metricRows.first { it.label == "Input tokens" }.value)
    }

    @Test
    fun `response stats cache read peaks on largest prompt hop and surfaces billing total`() {
        val messageId = MessageId("m-cache-ui")
        val hop1 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-cache-ui-1"),
                messageId = messageId,
                inputTokens = 10_129,
                cacheReadInputTokens = 7_552,
            )
        val hop2 =
            TelemetryTestFixtures.telOpenAi1.copy(
                id = TurnTelemetryId("tel-cache-ui-2"),
                messageId = messageId,
                inputTokens = 36_417,
                cacheReadInputTokens = 0,
            )
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = messageId,
                rows = listOf(hop1, hop2),
            )
        val sheet = mapper.mapResponseStats(summary)

        assertEquals("0", sheet.metricRows.first { it.label == "Cache read input tokens" }.value)
        assertEquals(
            "7552",
            sheet.metricRows.first { it.label == "Total cache read (all API calls)" }.value,
        )
    }
}
