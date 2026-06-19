package com.browntowndev.enclave.domain.model.telemetry

/**
 * Input- and cache-token rollups for telemetry summaries (peak vs row-sum).
 */
internal object TelemetryInputTokenMetrics {
    fun sumNormalizedInputTokens(rows: List<TurnTelemetry>): Int? {
        if (rows.none { it.inputTokens != null }) return null
        return rows.mapNotNull { TelemetrySummaryBuilder.normalizedInputTokens(it) }.sum()
    }

    fun peakNormalizedInputTokens(rows: List<TurnTelemetry>): Int? {
        if (rows.none { it.inputTokens != null }) return null
        return rows.mapNotNull { TelemetrySummaryBuilder.normalizedInputTokens(it) }.maxOrNull()
    }

    fun sumOfPerMessagePeakNormalizedInput(rows: List<TurnTelemetry>): Int =
        rows
            .groupBy { it.messageId }
            .values
            .sumOf { messageRows ->
                messageRows.mapNotNull { TelemetrySummaryBuilder.normalizedInputTokens(it) }.maxOrNull() ?: 0
            }

    fun sumCacheReadInputTokens(rows: List<TurnTelemetry>): Int? = sumOrNull(rows) { it.cacheReadInputTokens }

    fun sumCacheWriteInputTokens(rows: List<TurnTelemetry>): Int? =
        sumOrNull(rows) { it.cacheWriteInputTokens ?: it.cacheCreationInputTokens }

    /**
     * Cache read on the API hop with peak prompt size for each message. Aligns with
     * [peakNormalizedInputTokens] so UI rows are comparable (subset of that hop's input).
     */
    fun peakAlignedCacheReadInputTokens(rows: List<TurnTelemetry>): Int? {
        val reads = peakPromptRowPerMessage(rows).mapNotNull { it.cacheReadInputTokens }
        if (reads.isEmpty()) return null
        return reads.sum()
    }

    fun sumOfPerMessagePeakAlignedCacheRead(rows: List<TurnTelemetry>): Int =
        peakPromptRowPerMessage(rows).sumOf { it.cacheReadInputTokens ?: 0 }

    /**
     * Cache write on the API hop with peak prompt size for each message.
     */
    fun peakAlignedCacheWriteInputTokens(rows: List<TurnTelemetry>): Int? {
        val writes =
            peakPromptRowPerMessage(rows).mapNotNull { row ->
                row.cacheWriteInputTokens ?: row.cacheCreationInputTokens
            }
        if (writes.isEmpty()) return null
        return writes.sum()
    }

    fun sumOfPerMessagePeakAlignedCacheWrite(rows: List<TurnTelemetry>): Int =
        peakPromptRowPerMessage(rows).sumOf { row ->
            row.cacheWriteInputTokens ?: row.cacheCreationInputTokens ?: 0
        }

    private fun peakPromptRowPerMessage(rows: List<TurnTelemetry>): List<TurnTelemetry> =
        rows
            .groupBy { it.messageId }
            .values
            .mapNotNull { messageRows -> messageRows.maxByOrNull { normalizedInputOrMin(it) } }

    private fun normalizedInputOrMin(row: TurnTelemetry): Int =
        TelemetrySummaryBuilder.normalizedInputTokens(row) ?: Int.MIN_VALUE

    private fun sumOrNull(
        rows: List<TurnTelemetry>,
        selector: (TurnTelemetry) -> Int?,
    ): Int? {
        if (rows.none { selector(it) != null }) return null
        return rows.mapNotNull(selector).sum()
    }
}
