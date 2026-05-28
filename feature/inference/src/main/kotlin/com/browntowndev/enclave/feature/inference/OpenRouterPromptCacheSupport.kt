package com.browntowndev.enclave.feature.inference

import com.openai.core.JsonValue
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.responses.ResponseInputText

internal object OpenRouterPromptCacheSupport {
    private const val CACHE_CONTROL_PROPERTY = "cache_control"

    fun supportsExplicitCacheControl(modelId: String): Boolean {
        val normalized = modelId.lowercase()
        return normalized.startsWith("anthropic/") ||
            normalized.contains("claude") ||
            normalized.startsWith("google/") ||
            normalized.contains("gemini")
    }

    fun stableHistoryTailIndex(stableMessageCount: Int): Int? =
        if (stableMessageCount > 0) {
            stableMessageCount - 1
        } else {
            null
        }

    fun cacheControlValue(): JsonValue = JsonValue.from(mapOf("type" to "ephemeral"))

    fun cachedSystemTextPart(text: String): ChatCompletionContentPartText =
        ChatCompletionContentPartText
            .builder()
            .text(text)
            .putAdditionalProperty(CACHE_CONTROL_PROPERTY, cacheControlValue())
            .build()

    fun cachedUserTextPart(text: String): ChatCompletionContentPart = ChatCompletionContentPart.ofText(cachedSystemTextPart(text))

    fun cachedResponseText(text: String): ResponseInputText =
        ResponseInputText
            .builder()
            .text(text)
            .putAdditionalProperty(CACHE_CONTROL_PROPERTY, cacheControlValue())
            .build()
}
