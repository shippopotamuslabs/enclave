package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.MessageId
import java.math.BigDecimal

data class MessageTelemetrySummary(
    val messageId: MessageId,
    val requestCount: Int,
    val providerLabel: String?,
    val modelLabel: String?,
    val totalCostUsd: BigDecimal?,
    val costStatus: TelemetryCostStatus,
    val averageTtftMs: Long?,
    val averageOutputTokensPerSecond: Double?,
    val totalInputTokens: Int?,
    /** Peak normalized prompt size across tool-loop hops (largest single request). */
    val totalInputTokensNormalized: Int?,
    /** Sum of normalized prompt tokens across every API row (billing rollup). */
    val billingInputTokensNormalized: Int?,
    val totalOutputTokens: Int?,
    val totalReasoningTokens: Int?,
    /** Peak-input hop cache read (subset of peak prompt); not a sum across tool-loop hops. */
    val peakCacheReadInputTokens: Int?,
    /** Sum of cache read across every API row (billing rollup). */
    val totalCacheReadInputTokens: Int?,
    /** Peak-input hop cache write; not a sum across tool-loop hops. */
    val peakCacheWriteInputTokens: Int?,
    /** Sum of cache write across every API row (billing rollup). */
    val totalCacheWriteInputTokens: Int?,
    val cacheHitRatePercent: Double?,
    val requestRows: List<TurnTelemetry>,
)
