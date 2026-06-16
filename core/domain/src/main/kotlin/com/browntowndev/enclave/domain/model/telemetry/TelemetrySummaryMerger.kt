package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.MessageId

object TelemetrySummaryMerger {
    fun mergeTelemetryRows(
        persisted: List<TurnTelemetry>,
        inMemory: List<TurnTelemetry>,
    ): List<TurnTelemetry> {
        if (inMemory.isEmpty()) return persisted
        val persistedIds = persisted.map { it.id }.toSet()
        return persisted + inMemory.filter { it.id !in persistedIds }
    }

    fun mergeMessageSummary(
        persisted: MessageTelemetrySummary,
        messageId: MessageId,
        inMemoryRows: List<TurnTelemetry>,
    ): MessageTelemetrySummary {
        val scopedInMemory = inMemoryRows.filter { it.messageId == messageId }
        return when {
            persisted.requestCount > 0 -> persisted
            scopedInMemory.isNotEmpty() ->
                TelemetrySummaryBuilder.buildMessageSummary(messageId, scopedInMemory)
            else -> persisted
        }
    }

    fun mergeChatSummary(
        persisted: ChatTelemetrySummary,
        inMemoryRows: List<TurnTelemetry>,
    ): ChatTelemetrySummary {
        val mergedRows = mergeTelemetryRows(persisted.rows, inMemoryRows)
        return if (mergedRows.size == persisted.rows.size) {
            persisted
        } else {
            TelemetrySummaryBuilder.buildChatSummary(
                chatId = persisted.chatId,
                rows = mergedRows,
                assistantTurnCount = persisted.turnCount,
            )
        }
    }
}
