package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.chat.completions.ChatCompletionChunk
import com.openai.models.responses.Response
import com.openai.models.responses.ResponseUsage

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
)

internal fun accumulateOpenAiStreamUsage(
    event: ChatCompletionChunk,
    state: OpenAiStreamUsageAccumulator,
) {
    if (!event.usage().isPresent) return
    val usage = event.usage().get()
    state.inputTokens = usage.promptTokens().toInt()
    state.outputTokens = usage.completionTokens().toInt()
    if (usage.completionTokensDetails().isPresent) {
        val details = usage.completionTokensDetails().get()
        if (details.reasoningTokens().isPresent) {
            state.reasoningTokens = details.reasoningTokens().get().toInt()
        }
    }
    if (usage.promptTokensDetails().isPresent) {
        val details = usage.promptTokensDetails().get()
        if (details.cachedTokens().isPresent) {
            state.cacheReadInputTokens = details.cachedTokens().get().toInt()
        }
    }
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
    state.inputTokens = usage.inputTokens().toInt()
    state.outputTokens = usage.outputTokens().toInt()
    val reasoning = usage.outputTokensDetails().reasoningTokens()
    if (reasoning > 0L) {
        state.reasoningTokens = reasoning.toInt()
    }
    state.cacheReadInputTokens = usage.inputTokensDetails().cachedTokens().toInt()
}
