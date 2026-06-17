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
    val totalInputTokensNormalized: Int?,
    val totalOutputTokens: Int?,
    val totalReasoningTokens: Int?,
    val totalCacheReadInputTokens: Int?,
    val totalCacheWriteInputTokens: Int?,
    val cacheHitRatePercent: Double?,
    val requestRows: List<TurnTelemetry>,
)
