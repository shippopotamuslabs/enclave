package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

object TelemetrySummaryBuilder {
    private const val MONTH_END_HOUR = 23
    private const val MONTH_END_MINUTE = 59
    private const val MONTH_END_SECOND = 59
    private const val MONTH_END_NANOS = 999_000_000

    fun buildMessageSummary(
        messageId: MessageId,
        rows: List<TurnTelemetry>,
    ): MessageTelemetrySummary {
        if (rows.isEmpty()) {
            return MessageTelemetrySummary(
                messageId = messageId,
                requestCount = 0,
                providerLabel = null,
                modelLabel = null,
                totalCostUsd = null,
                costStatus = TelemetryCostStatus.UNAVAILABLE,
                averageTtftMs = null,
                averageOutputTokensPerSecond = null,
                totalInputTokens = null,
                totalOutputTokens = null,
                totalReasoningTokens = null,
                totalCacheReadInputTokens = null,
                totalCacheWriteInputTokens = null,
                cacheHitRatePercent = null,
                requestRows = emptyList(),
            )
        }
        val costRows = rows.filter { it.costUsd != null }
        val totalCost =
            costRows
                .mapNotNull { it.costUsd }
                .fold(BigDecimal.ZERO) { acc, value -> acc + value }
                .takeIf { costRows.isNotEmpty() }
        return MessageTelemetrySummary(
            messageId = messageId,
            requestCount = rows.size,
            providerLabel = singleProviderLabel(rows.map { it.providerLabel }),
            modelLabel = singleModelLabel(rows.map { it.modelLabel }),
            totalCostUsd = totalCost,
            costStatus = aggregateCostStatus(rows),
            averageTtftMs = averageTtft(rows),
            averageOutputTokensPerSecond = weightedOutputSpeed(rows),
            totalInputTokens = sumOrNull(rows) { it.inputTokens },
            totalOutputTokens = sumOrNull(rows) { it.outputTokens },
            totalReasoningTokens = sumOrNull(rows) { it.reasoningTokens },
            totalCacheReadInputTokens = sumOrNull(rows) { it.cacheReadInputTokens },
            totalCacheWriteInputTokens = sumOrNull(rows) { it.cacheWriteInputTokens ?: it.cacheCreationInputTokens },
            cacheHitRatePercent = cacheHitRate(rows),
            requestRows = rows,
        )
    }

    fun buildChatSummary(
        chatId: ChatId,
        rows: List<TurnTelemetry>,
        assistantTurnCount: Int = rows.map { it.messageId }.distinct().size,
    ): ChatTelemetrySummary {
        val messageIds = rows.map { it.messageId }.distinct()
        val costSum =
            rows
                .mapNotNull { it.costUsd }
                .fold(BigDecimal.ZERO) { acc, value -> acc + value }
        return ChatTelemetrySummary(
            chatId = chatId,
            turnCount = assistantTurnCount,
            telemetryBackedTurnCount = messageIds.size,
            missingTelemetryTurnCount = (assistantTurnCount - messageIds.size).coerceAtLeast(0),
            totalCostUsd = costSum,
            averageTtftMs = averageTtft(rows),
            averageOutputTokensPerSecond = weightedOutputSpeed(rows),
            totalInputTokens = rows.mapNotNull { it.inputTokens }.sum(),
            totalOutputTokens = rows.mapNotNull { it.outputTokens }.sum(),
            totalReasoningTokens = rows.mapNotNull { it.reasoningTokens }.sum(),
            totalCacheReadInputTokens = rows.mapNotNull { it.cacheReadInputTokens }.sum(),
            totalCacheWriteInputTokens = rows.mapNotNull { it.cacheWriteInputTokens }.sum(),
            cacheHitRatePercent = cacheHitRate(rows),
            providerBreakdown = providerBreakdown(rows),
            modelBreakdown = modelBreakdown(rows),
        )
    }

    fun buildUsageSpendSummary(
        period: UsagePeriod,
        rows: List<TurnTelemetry>,
        now: Instant,
        zoneId: ZoneId,
    ): UsageSpendSummary {
        val filtered = filterByPeriod(rows, period, now, zoneId)
        val costRows = filtered.filter { it.costUsd != null }
        val totalCost =
            costRows
                .mapNotNull { it.costUsd }
                .fold(BigDecimal.ZERO) { acc, value -> acc + value }
        val messageIds = filtered.map { it.messageId }.distinct()
        val missingCostTurnCount =
            messageIds.count { messageId ->
                filtered.filter { it.messageId == messageId }.none { it.costUsd != null }
            }
        return UsageSpendSummary(
            period = period,
            periodStartUtcMs = periodStartMs(period, now, zoneId),
            periodEndUtcMs = periodEndMs(period, now, zoneId),
            totalCostUsd = totalCost,
            telemetryBackedTurnCount = messageIds.size,
            missingCostTurnCount = missingCostTurnCount,
            providerBreakdown =
                providerBreakdown(filtered).map { row ->
                    ProviderUsageSpendRow(
                        providerLabel = row.providerLabel,
                        totalCostUsd = row.totalCostUsd,
                        turnCount = row.turnCount,
                    )
                },
        )
    }

    private fun filterByPeriod(
        rows: List<TurnTelemetry>,
        period: UsagePeriod,
        now: Instant,
        zoneId: ZoneId,
    ): List<TurnTelemetry> =
        when (period) {
            UsagePeriod.LIFETIME -> rows
            UsagePeriod.CURRENT_MONTH -> {
                val start = periodStartMs(period, now, zoneId)!!
                val end = periodEndMs(period, now, zoneId)!!
                rows.filter { row ->
                    val completed = row.completedAtMs ?: return@filter false
                    completed in start..end
                }
            }
        }

    private fun periodStartMs(
        period: UsagePeriod,
        now: Instant,
        zoneId: ZoneId,
    ): Long? =
        when (period) {
            UsagePeriod.LIFETIME -> null
            UsagePeriod.CURRENT_MONTH ->
                YearMonth
                    .from(now.atZone(zoneId))
                    .atDay(1)
                    .atStartOfDay(zoneId)
                    .toInstant()
                    .toEpochMilli()
        }

    private fun periodEndMs(
        period: UsagePeriod,
        now: Instant,
        zoneId: ZoneId,
    ): Long? =
        when (period) {
            UsagePeriod.LIFETIME -> null
            UsagePeriod.CURRENT_MONTH ->
                YearMonth
                    .from(now.atZone(zoneId))
                    .atEndOfMonth()
                    .atTime(MONTH_END_HOUR, MONTH_END_MINUTE, MONTH_END_SECOND, MONTH_END_NANOS)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
        }

    private fun providerBreakdown(rows: List<TurnTelemetry>): List<ProviderTelemetryBreakdown> =
        rows
            .groupBy { it.providerLabel }
            .map { (providerLabel, providerRows) ->
                ProviderTelemetryBreakdown(
                    providerLabel = providerLabel,
                    totalCostUsd =
                        providerRows
                            .mapNotNull { it.costUsd }
                            .fold(BigDecimal.ZERO) { acc, value -> acc + value }
                            .takeIf { providerRows.any { it.costUsd != null } },
                    turnCount = providerRows.map { it.messageId }.distinct().size,
                    requestCount = providerRows.size,
                )
            }

    private fun modelBreakdown(rows: List<TurnTelemetry>): List<ModelTelemetryBreakdown> =
        rows
            .groupBy { it.modelLabel }
            .map { (modelLabel, modelRows) ->
                ModelTelemetryBreakdown(
                    modelLabel = modelLabel,
                    totalCostUsd =
                        modelRows
                            .mapNotNull { it.costUsd }
                            .fold(BigDecimal.ZERO) { acc, value -> acc + value }
                            .takeIf { modelRows.any { it.costUsd != null } },
                    outputTokens = modelRows.mapNotNull { it.outputTokens }.sum(),
                    turnCount = modelRows.map { it.messageId }.distinct().size,
                    requestCount = modelRows.size,
                )
            }

    private fun singleProviderLabel(labels: List<String>): String? =
        when (labels.distinct().size) {
            0 -> null
            1 -> labels.first()
            else -> "Multiple providers"
        }

    private fun singleModelLabel(labels: List<String>): String? =
        when (labels.distinct().size) {
            0 -> null
            1 -> labels.first()
            else -> "Multiple models"
        }

    private fun aggregateCostStatus(rows: List<TurnTelemetry>): TelemetryCostStatus {
        val costRows = rows.filter { it.costUsd != null }
        if (costRows.isEmpty()) return TelemetryCostStatus.UNAVAILABLE
        return when {
            costRows.all { it.costStatus == TelemetryCostStatus.EXACT } -> TelemetryCostStatus.EXACT
            costRows.any { it.costStatus == TelemetryCostStatus.ESTIMATED } -> TelemetryCostStatus.ESTIMATED
            else -> TelemetryCostStatus.UNAVAILABLE
        }
    }

    private fun averageTtft(rows: List<TurnTelemetry>): Long? {
        val samples =
            rows.mapNotNull { row ->
                val first = row.firstOutputTokenAtMs ?: return@mapNotNull null
                first - row.requestStartedAtMs
            }
        if (samples.isEmpty()) return null
        return samples.average().toLong()
    }

    private fun weightedOutputSpeed(rows: List<TurnTelemetry>): Double? {
        var totalOutput = 0
        var totalDurationMs = 0L
        rows.forEach { row ->
            val output = row.outputTokens ?: return@forEach
            val first = row.firstOutputTokenAtMs ?: return@forEach
            val completed = row.completedAtMs ?: return@forEach
            val durationMs = completed - first
            if (durationMs <= 0L) return@forEach
            totalOutput += output
            totalDurationMs += durationMs
        }
        if (totalDurationMs <= 0L || totalOutput == 0) return null
        val seconds = totalDurationMs.toDouble() / 1000.0
        return (totalOutput / seconds).toBigDecimal().setScale(1, RoundingMode.HALF_UP).toDouble()
    }

    /**
     * Denominator for cache-hit %.
     * OpenAI/Google report [TurnTelemetry.inputTokens] as total prompt tokens (cache read is a subset).
     * Anthropic reports non-cached [TurnTelemetry.inputTokens] separately from [TurnTelemetry.cacheReadInputTokens].
     */
    internal fun inputDenominatorForCacheHitRate(row: TurnTelemetry): Int? {
        val input = row.inputTokens ?: return null
        val cacheRead = row.cacheReadInputTokens ?: 0
        return when (row.providerKey) {
            TelemetryProviderKey.ANTHROPIC -> input + cacheRead + (row.cacheCreationInputTokens ?: 0)
            else -> input
        }
    }

    private fun cacheHitRate(rows: List<TurnTelemetry>): Double? {
        var inputDenominatorTotal = 0
        var cacheReadTotal = 0
        var hasInput = false
        rows.forEach { row ->
            val denominator = inputDenominatorForCacheHitRate(row) ?: return@forEach
            hasInput = true
            inputDenominatorTotal += denominator
            cacheReadTotal += row.cacheReadInputTokens ?: 0
        }
        if (!hasInput || inputDenominatorTotal == 0) return null
        return (cacheReadTotal.toDouble() / inputDenominatorTotal.toDouble() * 100.0)
            .toBigDecimal()
            .setScale(1, RoundingMode.HALF_UP)
            .toDouble()
    }

    private fun sumOrNull(
        rows: List<TurnTelemetry>,
        selector: (TurnTelemetry) -> Int?,
    ): Int? {
        if (rows.none { selector(it) != null }) return null
        return rows.mapNotNull(selector).sum()
    }
}
