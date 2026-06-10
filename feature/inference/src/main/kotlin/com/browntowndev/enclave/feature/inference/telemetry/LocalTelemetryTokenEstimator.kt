package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatMessage

internal fun estimateTokenCount(text: CharSequence): Int? =
    text.takeIf { it.isNotEmpty() }?.let { (it.length / 4).coerceAtLeast(1) }

internal data class LiteRtInputTelemetry(
    val inputTokens: Int?,
    val cacheReadInputTokens: Int?,
)

internal fun estimateLiteRtPromptTokens(
    history: List<ChatMessage>,
    currentPrompt: String,
    systemPrompt: String,
): Int {
    val combined =
        buildString {
            if (systemPrompt.isNotBlank()) {
                append(systemPrompt)
            }
            history.forEach { message ->
                append(message.content)
            }
            append(currentPrompt)
        }
    return estimateTokenCount(combined) ?: 0
}

/**
 * LiteRT [lastPrefillTokenCount] reports only newly prefilled tokens for the active turn.
 * Estimate total prompt size from conversation context and derive cache-read tokens as the
 * remainder when benchmark prefill is smaller than the estimated total.
 */
internal fun resolveLiteRtInputTelemetry(
    benchmarkPrefillTokens: Int?,
    estimatedTotalPromptTokens: Int,
): LiteRtInputTelemetry {
    val newPrefillTokens = benchmarkPrefillTokens?.takeIf { it > 0 }
    if (estimatedTotalPromptTokens <= 0 && newPrefillTokens == null) {
        return LiteRtInputTelemetry(inputTokens = null, cacheReadInputTokens = null)
    }

    val totalInputTokens =
        when {
            estimatedTotalPromptTokens > 0 && newPrefillTokens != null ->
                maxOf(estimatedTotalPromptTokens, newPrefillTokens)
            estimatedTotalPromptTokens > 0 -> estimatedTotalPromptTokens
            else -> newPrefillTokens
        }
    val cacheReadInputTokens =
        newPrefillTokens
            ?.let { prefill ->
                totalInputTokens
                    ?.let { total -> (total - prefill).takeIf { cached -> cached > 0 } }
            }
    return LiteRtInputTelemetry(
        inputTokens = totalInputTokens,
        cacheReadInputTokens = cacheReadInputTokens,
    )
}
