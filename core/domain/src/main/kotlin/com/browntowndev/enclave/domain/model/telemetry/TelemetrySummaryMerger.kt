package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.MessageId

object TelemetrySummaryMerger {
    fun mergeTelemetryRows(
        persisted: List<TurnTelemetry>,
        inMemory: List<TurnTelemetry>,
    ): List<TurnTelemetry> {
        if (inMemory.isEmpty()) return persisted
        // When a persisted and in-memory row share the same ID, coalesce: the persisted row's
        // non-null fields win, but the in-memory row fills any null gaps (e.g. reasoning/cache
        // columns that weren't populated when the persisted row was written).
        val inMemoryById = inMemory.associateBy { it.id }
        val persistedIds = persisted.map { it.id }.toSet()
        return persisted.map { row ->
            inMemoryById[row.id]?.let { row.coalesceTokenFieldsWith(it) } ?: row
        } + inMemory.filter { it.id !in persistedIds }
    }

    fun mergeMessageSummary(
        persisted: MessageTelemetrySummary,
        messageId: MessageId,
        inMemoryRows: List<TurnTelemetry>,
    ): MessageTelemetrySummary {
        val scopedInMemory = inMemoryRows.filter { it.messageId == messageId }
        if (scopedInMemory.isEmpty()) return persisted
        val mergedRows = mergeTelemetryRows(persisted.requestRows, scopedInMemory)
        return TelemetrySummaryBuilder.buildMessageSummary(messageId, mergedRows)
    }

    fun mergeChatSummary(
        persisted: ChatTelemetrySummary,
        inMemoryRows: List<TurnTelemetry>,
    ): ChatTelemetrySummary {
        if (inMemoryRows.isEmpty()) return persisted
        val mergedRows = mergeTelemetryRows(persisted.rows, inMemoryRows)
        // Always rebuild — persisted rows may have null token fields that in-memory rows filled
        // via coalescing, so the persisted summary's pre-computed totals are stale.
        return TelemetrySummaryBuilder.buildChatSummary(
            chatId = persisted.chatId,
            rows = mergedRows,
            assistantTurnCount = persisted.turnCount,
        )
    }
}
