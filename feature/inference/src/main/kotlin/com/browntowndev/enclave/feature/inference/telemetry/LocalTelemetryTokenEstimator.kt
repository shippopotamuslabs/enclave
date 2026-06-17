package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions

private const val IMAGE_TOKENS_PER_URI = 576

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
    options: GenerationOptions? = null,
): Int {
    val templatedString =
        buildString {
            if (systemPrompt.isNotBlank()) {
                append("<start_of_turn>system\n")
                append(systemPrompt)
                append("<end_of_turn>\n")
            }
            if (options?.toolingEnabled == true && options.toolCatalog.isNotEmpty()) {
                append("<start_of_turn>system\n")
                options.toolCatalog.forEach { tool ->
                    append(tool.name)
                    append("\n")
                    append(tool.description)
                    append("\n")
                }
                append("<end_of_turn>\n")
            }
            if (!options?.volatileTurnContext.isNullOrBlank()) {
                append("<start_of_turn>user\n")
                append(options.volatileTurnContext)
                append("<end_of_turn>\n")
            }
            history.forEach { message ->
                val roleStr =
                    when (message.role) {
                        Role.USER -> "user"
                        Role.ASSISTANT -> "model"
                        Role.SYSTEM -> "system"
                    }
                append("<start_of_turn>$roleStr\n")
                append(message.content)
                append("<end_of_turn>\n")
            }
            append("<start_of_turn>user\n")
            append(currentPrompt)
            append("<end_of_turn>\n")
            append("<start_of_turn>model\n")
        }
    val baseTokenCount = (templatedString.length / 4).coerceAtLeast(1)
    val imageTokens = if (options != null) options.imageUris.size * IMAGE_TOKENS_PER_URI else 0
    return baseTokenCount + imageTokens
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
