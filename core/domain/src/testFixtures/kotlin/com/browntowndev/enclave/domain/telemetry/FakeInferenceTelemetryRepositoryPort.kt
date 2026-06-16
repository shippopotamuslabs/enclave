package com.browntowndev.enclave.domain.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.telemetry.ChatTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.MessageTelemetrySummary
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySummaryBuilder
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.domain.port.repository.InferenceTelemetryRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class FakeInferenceTelemetryRepositoryPort(
    private val messageRows: Map<MessageId, List<TurnTelemetry>> = emptyMap(),
    private val chatRows: Map<ChatId, List<TurnTelemetry>> = emptyMap(),
    private val chatAssistantTurnCounts: Map<ChatId, Int> = emptyMap(),
    private val usageLifetime: UsageSpendSummary? = null,
    private val usageCurrentMonth: UsageSpendSummary? = null,
    private val currentDefaultProviderLabel: String = "xAI",
    private val currentDefaultModelLabel: String = "grok-4.3",
    private val mislabelBreakdownFromCurrentDefault: Boolean = false,
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-25T12:00:00Z"), ZoneId.of("UTC")),
    private val zoneId: ZoneId = ZoneId.of("America/New_York"),
) : InferenceTelemetryRepositoryPort {
    override fun observeMessageTelemetry(messageId: MessageId): Flow<MessageTelemetrySummary> =
        flowOf(
            TelemetrySummaryBuilder.buildMessageSummary(
                messageId = messageId,
                rows = messageRows[messageId].orEmpty(),
            ),
        )

    override fun observeChatTelemetry(chatId: ChatId): Flow<ChatTelemetrySummary> {
        val rows = chatRows[chatId].orEmpty()
        val summary =
            if (mislabelBreakdownFromCurrentDefault) {
                TelemetrySummaryBuilder.buildChatSummary(chatId = chatId, rows = emptyList()).copy(
                    providerBreakdown =
                        listOf(
                            com.browntowndev.enclave.domain.model.telemetry.ProviderTelemetryBreakdown(
                                providerLabel = currentDefaultProviderLabel,
                                totalCostUsd = rows.mapNotNull { it.costUsd }.fold(java.math.BigDecimal.ZERO) { a, b -> a + b },
                                turnCount = rows.map { it.messageId }.distinct().size,
                                requestCount = rows.size,
                            ),
                        ),
                )
            } else {
                TelemetrySummaryBuilder.buildChatSummary(
                    chatId = chatId,
                    rows = rows,
                    assistantTurnCount = chatAssistantTurnCounts[chatId] ?: rows.map { it.messageId }.distinct().size,
                )
            }
        return flowOf(summary)
    }

    override fun observeUsageSpendSummary(period: UsagePeriod): Flow<UsageSpendSummary> {
        val allRows = chatRows.values.flatten().ifEmpty { messageRows.values.flatten() }
        val built =
            when (period) {
                UsagePeriod.LIFETIME ->
                    usageLifetime
                        ?: TelemetrySummaryBuilder.buildUsageSpendSummary(
                            period = period,
                            rows = allRows,
                            now = clock.instant(),
                            zoneId = zoneId,
                        )
                UsagePeriod.CURRENT_MONTH ->
                    usageCurrentMonth
                        ?: TelemetrySummaryBuilder.buildUsageSpendSummary(
                            period = period,
                            rows = allRows,
                            now = clock.instant(),
                            zoneId = zoneId,
                        )
            }
        return flowOf(built)
    }
}
