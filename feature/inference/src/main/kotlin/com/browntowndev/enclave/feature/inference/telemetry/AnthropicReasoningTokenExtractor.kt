package com.browntowndev.enclave.feature.inference.telemetry

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.Usage

/**
 * Extracts reasoning/thinking token counts from Anthropic-compatible responses.
 *
 * Real Anthropic responses report thinking overhead only via streamed `thinking` content blocks
 * (not in `usage`), so [Usage] exposes no `reasoningTokens()` accessor. Some Anthropic-compatible
 * third-party gateways return reasoning tokens as **non-standard** usage fields, surfaced through
 * the Stainless [Usage._additionalProperties] escape hatch.
 *
 * Non-standard gateways have been observed emitting reasoning tokens in two shapes:
 *  - top-level `usage.reasoning_tokens`, and
 *  - nested `usage.completion_tokens_details.reasoning_tokens`.
 *
 * This extractor reads both defensively (nested wins when both are present) and never throws,
 * returning `null` for standard Anthropic responses whose additional properties are empty — so
 * the existing Anthropic flow is unchanged.
 *
 * Note: reasoning tokens parsed here are tracked as a separate telemetry column. They are NOT
 * folded into `output_tokens` on the Anthropic/Z.ai path (unlike the OpenAI-compatible path,
 * whose `completion_tokens` already includes reasoning). Billing them would be a one-line
 * change in [SavedApiModelPricingEstimator]; see the comment there.
 */
internal object AnthropicReasoningTokenExtractor {
    private const val TOP_LEVEL_KEY = "reasoning_tokens"
    private const val DETAILS_KEY = "completion_tokens_details"
    private const val DETAILS_NESTED_KEY = "reasoning_tokens"

    fun extract(usage: Usage): Int? = extract(usage._additionalProperties())

    fun extract(usage: MessageDeltaUsage): Int? = extract(usage._additionalProperties())

    private fun extract(props: Map<String, JsonValue>): Int? {
        val nested =
            props[DETAILS_KEY]
                ?.asObject()
                ?.orElse(null)
                ?.get(DETAILS_NESTED_KEY)
                ?.let(::tokenToInt)
        return nested ?: props[TOP_LEVEL_KEY]?.let(::tokenToInt)
    }

    private fun tokenToInt(value: JsonValue): Int? =
        value
            .asNumber()
            .map { it.toLong().coerceIn(0, Int.MAX_VALUE.toLong()).toInt() }
            .orElseGet {
                value.asString().map { it.toIntOrNull() }.orElse(null)
            }
}
