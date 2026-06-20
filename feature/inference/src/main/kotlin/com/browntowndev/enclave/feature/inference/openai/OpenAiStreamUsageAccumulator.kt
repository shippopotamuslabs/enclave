package com.browntowndev.enclave.feature.inference.openai

import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.completions.CompletionUsage
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseUsage
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Mutable usage fields collected from OpenAI-compatible streaming chunks (xAI, OpenRouter, OpenAI).
 *
 * xAI often sends the final [ChatCompletionChunk.usage] on a chunk with an empty [choices] list;
 * callers must apply usage before bailing out on empty choices.
 */
internal data class OpenAiStreamUsageAccumulator(
    var inputTokens: Int? = null,
    var outputTokens: Int? = null,
    var reasoningTokens: Int? = null,
    var cacheReadInputTokens: Int? = null,
    var cacheWriteInputTokens: Int? = null,
    var costUsd: BigDecimal? = null,
)

private fun coalesceMax(
    existing: Int?,
    incoming: Int,
): Int? = if (existing == null) incoming else maxOf(existing, incoming)

internal fun accumulateOpenAiStreamUsage(
    event: ChatCompletionChunk,
    state: OpenAiStreamUsageAccumulator,
) {
    if (!event.usage().isPresent) return
    val usage = event.usage().get()
    state.inputTokens = coalesceMax(state.inputTokens, usage.promptTokens().toInt())
    state.outputTokens = coalesceMax(state.outputTokens, usage.completionTokens().toInt())
    if (usage.completionTokensDetails().isPresent) {
        val details = usage.completionTokensDetails().get()
        if (details.reasoningTokens().isPresent) {
            state.reasoningTokens =
                coalesceMax(state.reasoningTokens, details.reasoningTokens().get().toInt())
        }
    }
    if (usage.promptTokensDetails().isPresent) {
        val details = usage.promptTokensDetails().get()
        if (details.cachedTokens().isPresent) {
            state.cacheReadInputTokens =
                coalesceMax(state.cacheReadInputTokens, details.cachedTokens().get().toInt())
        }
        parseJsonValueToInt(details._additionalProperties()["cache_write_tokens"])?.let { writeTokens ->
            state.cacheWriteInputTokens = coalesceMax(state.cacheWriteInputTokens, writeTokens)
        }
    }
    parseJsonValueToInt(usage._additionalProperties()["cache_read_tokens"])?.let { readTokens ->
        state.cacheReadInputTokens = coalesceMax(state.cacheReadInputTokens, readTokens)
    }
    parseJsonValueToInt(usage._additionalProperties()["cache_write_tokens"])?.let { writeTokens ->
        state.cacheWriteInputTokens = coalesceMax(state.cacheWriteInputTokens, writeTokens)
    }
    parseOpenRouterUsageCost(usage)?.let { state.costUsd = it }
}

internal fun accumulateResponseStreamUsage(
    response: Response,
    state: OpenAiStreamUsageAccumulator,
) {
    if (!response.usage().isPresent) return
    accumulateResponseStreamUsage(response.usage().get(), state)
}

internal fun accumulateResponseStreamUsage(
    usage: ResponseUsage,
    state: OpenAiStreamUsageAccumulator,
) {
    state.inputTokens = coalesceMax(state.inputTokens, usage.inputTokens().toInt())
    state.outputTokens = coalesceMax(state.outputTokens, usage.outputTokens().toInt())
    val reasoning = usage.outputTokensDetails().reasoningTokens()
    if (reasoning > 0L) {
        state.reasoningTokens = coalesceMax(state.reasoningTokens, reasoning.toInt())
    }
    state.cacheReadInputTokens =
        coalesceMax(state.cacheReadInputTokens, usage.inputTokensDetails().cachedTokens().toInt())
    parseJsonValueToInt(usage.inputTokensDetails()._additionalProperties()["cache_write_tokens"])?.let { writeTokens ->
        state.cacheWriteInputTokens = coalesceMax(state.cacheWriteInputTokens, writeTokens)
    }
    parseJsonValueToInt(usage._additionalProperties()["cache_read_tokens"])?.let { readTokens ->
        state.cacheReadInputTokens = coalesceMax(state.cacheReadInputTokens, readTokens)
    }
    parseJsonValueToInt(usage._additionalProperties()["cache_write_tokens"])?.let { writeTokens ->
        state.cacheWriteInputTokens = coalesceMax(state.cacheWriteInputTokens, writeTokens)
    }
    parseOpenRouterUsageCost(usage)?.let { state.costUsd = it }
}

private fun parseOpenRouterUsageCost(usage: CompletionUsage): BigDecimal? = parseUsageCostJsonValue(usage._additionalProperties()["cost"])

private fun parseOpenRouterUsageCost(usage: ResponseUsage): BigDecimal? = parseUsageCostJsonValue(usage._additionalProperties()["cost"])

private fun parseUsageCostJsonValue(value: JsonValue?): BigDecimal? {
    if (value == null) return null
    val raw =
        runCatching { value.convert(Double::class.javaObjectType) }.getOrNull()
            ?: runCatching { value.convert(String::class.javaObjectType) }.getOrNull()?.toDoubleOrNull()
            ?: return null
    return BigDecimal.valueOf(raw).setScale(6, RoundingMode.HALF_UP)
}

private fun parseJsonValueToInt(value: JsonValue?): Int? {
    if (value == null) return null
    val raw =
        runCatching { value.convert(Double::class.javaObjectType) }.getOrNull()
            ?: runCatching { value.convert(String::class.javaObjectType) }.getOrNull()?.toDoubleOrNull()
            ?: return null
    return raw.toInt()
}

