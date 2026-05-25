package com.browntowndev.enclave.domain.model.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import java.math.BigDecimal

data class TurnTelemetry(
    val id: TurnTelemetryId,
    val messageId: MessageId,
    val chatId: ChatId,
    val sequenceIndex: Int,
    val providerKey: TelemetryProviderKey,
    val providerLabel: String,
    val modelId: String,
    val modelLabel: String,
    val sourceKind: TelemetrySourceKind,
    val requestStartedAtMs: Long,
    val firstOutputTokenAtMs: Long?,
    val completedAtMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val reasoningTokens: Int?,
    val cachedInputTokens: Int?,
    val cacheReadInputTokens: Int?,
    val cacheWriteInputTokens: Int?,
    val cacheCreationInputTokens: Int?,
    val costUsd: BigDecimal?,
    val costStatus: TelemetryCostStatus,
    val costSource: TelemetryCostSource,
    val clientBuild: TelemetryClientBuild,
)
