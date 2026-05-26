package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import java.math.BigDecimal

data class OpenAiTelemetryUsageInput(
    val rowId: String,
    val messageId: String,
    val chatId: String,
    val sequenceIndex: Int,
    val providerLabel: String,
    val modelId: String,
    val modelLabel: String,
    val requestStartedAtMs: Long,
    val firstOutputTokenAtMs: Long?,
    val completedAtMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val reasoningTokens: Int?,
    val cacheReadInputTokens: Int?,
    val cacheWriteInputTokens: Int? = null,
    val costUsd: BigDecimal?,
    val savedPricing: ApiModelSavedPricing? = null,
    val clientBuild: TelemetryClientBuild,
)

data class AnthropicTelemetryUsageInput(
    val rowId: String,
    val messageId: String,
    val chatId: String,
    val sequenceIndex: Int,
    val modelId: String,
    val modelLabel: String,
    val requestStartedAtMs: Long,
    val firstOutputTokenAtMs: Long?,
    val completedAtMs: Long?,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val reasoningTokens: Int?,
    val cacheCreationInputTokens: Int?,
    val savedPricing: ApiModelSavedPricing? = null,
    val clientBuild: TelemetryClientBuild,
)
