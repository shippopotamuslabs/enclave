package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.openai.core.JsonString
import com.openai.core.JsonValue

internal object DeepSeekRequestPolicy {
    @Suppress("FunctionOnlyReturningConstant")
    fun usesMaxTokensField(modelId: String): Boolean {
        // All DeepSeek direct API models use max_tokens on the wire.
        modelId.substringAfterLast('/')
        return USES_MAX_TOKENS_FIELD
    }

    fun thinkingJsonValue(): JsonValue = JsonString.of("""{"type":"enabled"}""")

    fun shouldOmitToolChoice(
        modelId: String,
        reasoningBudget: Int,
        toolsAvailable: Boolean,
    ): Boolean =
        toolsAvailable &&
            ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget)

    fun thinkingExtraBody(
        modelId: String,
        reasoningBudget: Int,
    ): Map<String, Any>? =
        if (ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget)) {
            mapOf("thinking" to mapOf("type" to "enabled"))
        } else {
            null
        }

    fun mapReasoningEffort(
        modelId: String,
        reasoningBudget: Int,
        effort: ApiReasoningEffort?,
    ): String? {
        if (!ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget)) {
            return null
        }
        return effort?.let(ApiProviderModelPolicy::deepSeekReasoningEffortForApi)
    }

    private const val USES_MAX_TOKENS_FIELD = true
}
