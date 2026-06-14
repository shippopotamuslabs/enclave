package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.telemetry.TelemetryTestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelemetrySummaryBuilderTest {
    @Test
    fun `cache hit rate uses total input for OpenAI compatible providers`() {
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mOpenAi1,
                rows = listOf(TelemetryTestFixtures.telOpenAi1),
            )

        assertEquals(40.0, summary.cacheHitRatePercent)
    }

    @Test
    fun `cache hit rate adds cache read to Anthropic input denominator`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-cache"),
                inputTokens = 7_787,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = null,
            )
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = MessageId("m-anthropic-cache"),
                rows = listOf(row),
            )

        assertEquals(67.3, summary.cacheHitRatePercent)
    }

    @Test
    fun `cache hit rate adds cache read and cache creation to Anthropic input denominator`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-cache-creation"),
                inputTokens = 7_787,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = MessageId("m-anthropic-cache-creation"),
                rows = listOf(row),
            )

        assertEquals(59.7, summary.cacheHitRatePercent)
    }

    @Test
    fun `mixed provider chat rollup keeps OpenAI fixture expectation`() {
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = TelemetryTestFixtures.chat1Rows,
            )

        assertEquals(19.0, summary.cacheHitRatePercent)
    }

    @Test
    fun `buildChatSummary totalCacheWriteInputTokens falls back to cacheCreationInputTokens for Anthropic rows`() {
        val anthropicRow =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-write"),
                chatId = ChatId("chat-anthropic-write"),
                cacheWriteInputTokens = null,
                cacheCreationInputTokens = 500,
            )
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = ChatId("chat-anthropic-write"),
                rows = listOf(anthropicRow),
            )

        // cacheWriteInputTokens is null for Anthropic, should fall back to cacheCreationInputTokens
        assertEquals(500, summary.totalCacheWriteInputTokens)
    }

    @Test
    fun `buildChatSummary totalCacheReadInputTokens sums Anthropic cache read tokens`() {
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-read"),
                chatId = ChatId("chat-anthropic-read"),
                cacheReadInputTokens = 8000,
                cacheCreationInputTokens = 200,
            )
        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = ChatId("chat-anthropic-read"),
                rows = listOf(row),
            )

        assertEquals(8000, summary.totalCacheReadInputTokens)
    }
}
