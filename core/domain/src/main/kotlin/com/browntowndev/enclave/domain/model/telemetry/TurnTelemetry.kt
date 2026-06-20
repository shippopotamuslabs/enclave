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
    /**
     * The upstream provider that actually served the request, when discoverable. For OpenRouter
     * this is the `openrouter-provider` response header (e.g. "Alibaba", "SiliconFlow"); for
     * direct providers it is null. Distinct from [providerLabel], which is the configured service.
     *
     * In-memory only — not persisted to Room. Used for live-session cache-hit triage so a low
     * cache hit can be attributed to a cold replica (serving provider switched) vs. a prefix break.
     */
    val servingProvider: String? = null,
    /**
     * The provider-assigned response id (e.g. OpenRouter's `gen-…`), for correlating in-app
     * telemetry rows with the provider dashboard. In-memory only.
     */
    val providerResponseId: String? = null,
    val clientBuild: TelemetryClientBuild,
) {
    /**
     * Returns a copy of this row with any null token/cost/timing fields filled in from [other].
     * Non-null fields on this row are preserved (this row wins); [other] only fills gaps.
     *
     * Used by [TelemetrySummaryMerger] to reconcile persisted Room rows (which may have null
     * reasoning/cache columns if written before a schema migration or before the final usage
     * chunk was applied) against in-memory rows captured during the turn.
     *
     * Keep field coverage in sync with
     * [com.browntowndev.enclave.core.data.mapper.telemetry.MessageInferenceTelemetryEntity.coalesceTokenFieldsWith].
     */
    fun coalesceTokenFieldsWith(other: TurnTelemetry): TurnTelemetry = copy(
        firstOutputTokenAtMs = firstOutputTokenAtMs ?: other.firstOutputTokenAtMs,
        completedAtMs = completedAtMs ?: other.completedAtMs,
        inputTokens = inputTokens ?: other.inputTokens,
        outputTokens = outputTokens ?: other.outputTokens,
        reasoningTokens = reasoningTokens ?: other.reasoningTokens,
        cachedInputTokens = cachedInputTokens ?: other.cachedInputTokens,
        cacheReadInputTokens = cacheReadInputTokens ?: other.cacheReadInputTokens,
        cacheWriteInputTokens = cacheWriteInputTokens ?: other.cacheWriteInputTokens,
        cacheCreationInputTokens = cacheCreationInputTokens ?: other.cacheCreationInputTokens,
        costUsd = costUsd ?: other.costUsd,
    )
}
