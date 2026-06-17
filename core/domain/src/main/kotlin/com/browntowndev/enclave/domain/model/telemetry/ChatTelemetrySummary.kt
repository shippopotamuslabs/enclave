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
    val totalInputTokensNormalized: Int,
    val totalOutputTokens: Int,
    val totalReasoningTokens: Int,
    val totalCacheReadInputTokens: Int,
    val totalCacheWriteInputTokens: Int,
    val cacheHitRatePercent: Double?,
    val providerBreakdown: List<ProviderTelemetryBreakdown>,
    val modelBreakdown: List<ModelTelemetryBreakdown>,
    val rows: List<TurnTelemetry> = emptyList(),
)
