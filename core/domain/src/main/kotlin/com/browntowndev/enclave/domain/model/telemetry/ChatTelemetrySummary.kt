package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import java.math.BigDecimal

data class ProviderTelemetryBreakdown(
    val providerLabel: String,
    val totalCostUsd: BigDecimal?,
    val turnCount: Int,
    val requestCount: Int,
)

data class ModelTelemetryBreakdown(
    val modelLabel: String,
    val totalCostUsd: BigDecimal?,
    val outputTokens: Int,
    val turnCount: Int,
    val requestCount: Int,
)

data class ChatTelemetrySummary(
    val chatId: ChatId,
    val turnCount: Int,
    val telemetryBackedTurnCount: Int,
    val missingTelemetryTurnCount: Int,
    val totalCostUsd: BigDecimal,
    val averageTtftMs: Long?,
    val averageOutputTokensPerSecond: Double?,
    val totalInputTokens: Int,
    /**
     * Sum of per-message peak normalized prompt size. Tool-loop assistant turns emit one telemetry
     * row per API hop, each reporting the full prompt for that request; summing those rows
     * double-counts overlapping context. For each distinct [TurnTelemetry.messageId] we take
     * max([TelemetrySummaryBuilder.normalizedInputTokens]) and sum those peaks so chat-level
     * "Input tokens" reflects total context exposure across turns without overlap inflation.
     */
    val peakInputTokensNormalized: Int,
    /** Sum of [TelemetrySummaryBuilder.normalizedInputTokens] across every API row (billing rollup). */
    val totalInputTokensNormalized: Int,
    val totalOutputTokens: Int,
    val totalReasoningTokens: Int,
    /**
     * Sum of per-message peak-aligned cache read (cache read on the largest prompt hop per message).
     * Not comparable to summing cache read across all tool-loop API calls.
     */
    val peakCacheReadInputTokens: Int,
    /** Sum of [TurnTelemetry.cacheReadInputTokens] across every API row (billing rollup). */
    val totalCacheReadInputTokens: Int,
    val peakCacheWriteInputTokens: Int,
    val totalCacheWriteInputTokens: Int,
    val cacheHitRatePercent: Double?,
    val providerBreakdown: List<ProviderTelemetryBreakdown>,
    val modelBreakdown: List<ModelTelemetryBreakdown>,
    val rows: List<TurnTelemetry> = emptyList(),
)
