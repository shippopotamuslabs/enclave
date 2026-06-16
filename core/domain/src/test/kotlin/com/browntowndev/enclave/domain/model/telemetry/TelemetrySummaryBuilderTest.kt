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

    @Test
    fun `normalizedInputTokens adds cache read and creation for Anthropic rows`() {
        // Anthropic reports non-cached input separately from cache read/creation. The normalized total
        // must add them back so the figure is comparable to OpenAI's total-prompt semantics.
        val row =
            TelemetryTestFixtures.telAnthropic1.copy(
                inputTokens = 7_787,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )

        assertEquals(7_787 + 16_000 + 3_000, TelemetrySummaryBuilder.normalizedInputTokens(row))
    }

    @Test
    fun `normalizedInputTokens leaves OpenAI input unchanged`() {
        // OpenAI reports cache read as a subset of the total prompt; input is already the full count.
        val row = TelemetryTestFixtures.telOpenAi1

        assertEquals(1_000, TelemetrySummaryBuilder.normalizedInputTokens(row))
    }

    @Test
    fun `normalizedInputTokens returns null when provider did not report input`() {
        val row = TelemetryTestFixtures.telLitert1.copy(inputTokens = null)

        assertEquals(null, TelemetrySummaryBuilder.normalizedInputTokens(row))
    }

    @Test
    fun `buildMessageSummary totalInputTokensNormalized sums normalized per-row input`() {
        val anthropicRow =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-norm"),
                inputTokens = 800,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )

        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = MessageId("m-anthropic-norm"),
                rows = listOf(anthropicRow),
            )

        // Raw input stays the provider-reported non-cached value; normalized adds cache read + creation.
        assertEquals(800, summary.totalInputTokens)
        assertEquals(800 + 16_000 + 3_000, summary.totalInputTokensNormalized)
    }

    @Test
    fun `buildChatSummary totalInputTokensNormalized mixes Anthropic and OpenAI semantics`() {
        val anthropicRow =
            TelemetryTestFixtures.telAnthropic1.copy(
                messageId = MessageId("m-anthropic-mix"),
                chatId = ChatId("chat-mix"),
                inputTokens = 800,
                cacheReadInputTokens = 16_000,
                cacheCreationInputTokens = 3_000,
            )
        val openAiRow =
            TelemetryTestFixtures.telOpenAi1.copy(
                messageId = MessageId("m-openai-mix"),
                chatId = ChatId("chat-mix"),
                inputTokens = 1_000,
                cacheReadInputTokens = 400,
            )

        val summary =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = ChatId("chat-mix"),
                rows = listOf(anthropicRow, openAiRow),
            )

        // Anthropic 800+16000+3000 = 19800; OpenAI 1000 (cache read already inside). Total 20800.
        assertEquals(800 + 1_000, summary.totalInputTokens)
        assertEquals(19_800 + 1_000, summary.totalInputTokensNormalized)
    }

    @Test
    fun `mergeTelemetryRows dedupes by row id not message id`() {
        val persisted = listOf(TelemetryTestFixtures.telAgent1)
        val inMemory = listOf(TelemetryTestFixtures.telAgent2)

        val merged = TelemetrySummaryMerger.mergeTelemetryRows(persisted, inMemory)

        assertEquals(2, merged.size)
        assertEquals(listOf(TelemetryTestFixtures.telAgent1.id, TelemetryTestFixtures.telAgent2.id), merged.map { it.id })
    }

    @Test
    fun `mergeMessageSummary scopes in-memory rows to clicked message`() {
        val persisted =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mLegacy1,
                rows = emptyList(),
            )

        val merged =
            TelemetrySummaryMerger.mergeMessageSummary(
                persisted = persisted,
                messageId = TelemetryTestFixtures.mLegacy1,
                inMemoryRows = listOf(TelemetryTestFixtures.telOpenAi1),
            )

        assertEquals(0, merged.requestCount)
    }

    @Test
    fun `mergeChatSummary rebuilds when in-memory row ids are not yet persisted`() {
        val persisted =
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = TelemetryTestFixtures.chat1,
                rows = listOf(TelemetryTestFixtures.telOpenAi1),
                assistantTurnCount = 2,
            )
        val inFlightRow =
            TelemetryTestFixtures.telAgent1.copy(
                messageId = MessageId("m-inflight"),
                inputTokens = 2_500,
            )

        val merged =
            TelemetrySummaryMerger.mergeChatSummary(
                persisted = persisted,
                inMemoryRows = listOf(inFlightRow),
            )

        assertEquals(3_500, merged.totalInputTokens)
    }

    @Test
    fun `buildMessageSummary returns null normalized input when no row reports input tokens`() {
        val summary =
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = TelemetryTestFixtures.mLitert1,
                rows = listOf(TelemetryTestFixtures.telLitert1),
            )

        assertEquals(null, summary.totalInputTokens)
        assertEquals(null, summary.totalInputTokensNormalized)
    }
}
