package com.browntowndev.enclave.domain.model.inference

data class ApiReasoningPolicy(
    val controlStyle: ApiReasoningControlStyle,
    val supportedEfforts: List<ApiReasoningEffort>,
    val defaultEffort: ApiReasoningEffort,
)

data class ApiModelParameterSupport(
    val reasoningPolicy: ApiReasoningPolicy,
    val supportsMaxTokens: Boolean = true,
    val supportsTemperature: Boolean = true,
    val supportsTopP: Boolean = true,
    val supportsTopK: Boolean = true,
    val supportsMinP: Boolean = true,
    val supportsFrequencyPenalty: Boolean = true,
    val supportsPresencePenalty: Boolean = true,
    val supportsReasoningEffort: Boolean = true,
) {
    companion object {
        val DEFAULT =
            ApiModelParameterSupport(
                reasoningPolicy =
                    ApiReasoningPolicy(
                        controlStyle = ApiReasoningControlStyle.STANDARD,
                        supportedEfforts =
                            listOf(
                                ApiReasoningEffort.LOW,
                                ApiReasoningEffort.MEDIUM,
                                ApiReasoningEffort.HIGH,
                                ApiReasoningEffort.XHIGH,
                            ),
                        defaultEffort = ApiReasoningEffort.LOW,
                    ),
            )
    }
}

enum class ApiReasoningControlStyle {
    STANDARD,
    XAI_MULTI_AGENT,
}

@Suppress("TooManyFunctions")
object ApiProviderModelPolicy {
    private val standardReasoningEfforts =
        listOf(
            ApiReasoningEffort.LOW,
            ApiReasoningEffort.MEDIUM,
            ApiReasoningEffort.HIGH,
            ApiReasoningEffort.XHIGH,
        )

    private val xaiMultiAgentEfforts =
        listOf(
            ApiReasoningEffort.LOW,
            ApiReasoningEffort.MEDIUM,
            ApiReasoningEffort.HIGH,
            ApiReasoningEffort.XHIGH,
        )

    fun supportsModelDiscovery(provider: ApiProvider): Boolean =
        provider == ApiProvider.OPENAI ||
            provider == ApiProvider.ANTHROPIC ||
            provider == ApiProvider.GOOGLE ||
            provider == ApiProvider.OPENROUTER ||
            provider == ApiProvider.XAI ||
            provider == ApiProvider.FAL

    fun isXaiMultiAgentModel(modelId: String): Boolean = modelId.matchesXaiMultiAgent()

    fun isXaiGrok420ReasoningModel(modelId: String): Boolean = modelId.matchesXaiGrok420Reasoning()

    fun isXaiGrok4ReasoningFamily(modelId: String): Boolean = modelId.matchesXaiGrok4ReasoningFamily()

    fun isXaiGrok4NonReasoningFamily(modelId: String): Boolean = modelId.matchesXaiGrok4NonReasoningFamily()

    fun isXaiGrok3MiniModel(modelId: String): Boolean = modelId.matchesXaiGrok3Mini()

    fun isXaiGrok3Model(modelId: String): Boolean = modelId.matchesXaiGrok3()

    fun isXaiReasoningModel(modelId: String): Boolean = modelId.matchesXaiReasoningModel()

    fun isXaiGrok420Family(modelId: String): Boolean = modelId.matchesXaiGrok420Family()

    fun isXaiChatReasoningContentModel(modelId: String): Boolean = modelId.matchesXaiChatReasoningContentModel()

    fun isOpenAiReasoningModel(modelId: String): Boolean {
        val normalized = modelId.substringAfterLast('/')
        return normalized.startsWith("o1") ||
            normalized.startsWith("o3") ||
            normalized.endsWith("-nano")
    }

    /**
     * Anthropic ephemeral prompt-cache breakpoints are only valid on documented Claude 3.5+ / 3.7 / 4 families.
     * Strips an optional `anthropic/` OpenRouter-style prefix before matching.
     */
    fun supportsAnthropicPromptCaching(modelId: String): Boolean {
        val normalized = modelId.substringAfter('/').lowercase()
        return ANTHROPIC_PROMPT_CACHE_MODEL_PREFIXES.any { normalized.startsWith(it) }
    }

    fun parameterSupport(
        provider: ApiProvider,
        modelId: String,
    ): ApiModelParameterSupport {
        val reasoningPolicy = reasoningPolicy(provider = provider, modelId = modelId)
        if (provider == ApiProvider.ANTHROPIC) {
            return ApiModelParameterSupport(
                reasoningPolicy = reasoningPolicy,
                supportsTemperature = false,
                supportsTopP = false,
                supportsTopK = false,
                supportsMinP = false,
                supportsFrequencyPenalty = false,
                supportsPresencePenalty = false,
                supportsReasoningEffort = true,
            )
        }

        if (provider == ApiProvider.GOOGLE) {
            return ApiModelParameterSupport(
                reasoningPolicy = reasoningPolicy,
                supportsTopK = true,
                supportsMinP = false,
                supportsFrequencyPenalty = false,
                supportsPresencePenalty = false,
                supportsReasoningEffort = false,
            )
        }

        if (provider == ApiProvider.OPENAI && isOpenAiReasoningModel(modelId)) {
            return ApiModelParameterSupport(
                reasoningPolicy = reasoningPolicy,
                supportsTemperature = false,
                supportsTopP = false,
                supportsMinP = false,
                supportsFrequencyPenalty = false,
                supportsPresencePenalty = false,
                supportsReasoningEffort = true,
            )
        }

        if (provider != ApiProvider.XAI) {
            return ApiModelParameterSupport(reasoningPolicy = reasoningPolicy)
        }

        // xAI only accepts user-controlled reasoning effort on the grok-3-mini, grok-4.3, and multi-agent families.
        val reasoningEffortSupported =
            when {
                isXaiGrok3MiniModel(modelId) -> true
                isXaiMultiAgentModel(modelId) -> true
                modelId.startsWith("grok-4.3") -> true
                else -> false
            }

        return ApiModelParameterSupport(
            reasoningPolicy = reasoningPolicy,
            supportsMaxTokens = !isXaiMultiAgentModel(modelId) && !isXaiGrok420Family(modelId) && !modelId.startsWith("grok-4.3"),
            supportsTopK = false,
            supportsMinP = false,
            supportsFrequencyPenalty = !isXaiReasoningModel(modelId),
            supportsPresencePenalty = !isXaiReasoningModel(modelId),
            supportsReasoningEffort = reasoningEffortSupported,
        )
    }

    fun reasoningPolicy(
        provider: ApiProvider,
        modelId: String,
    ): ApiReasoningPolicy =
        if (provider == ApiProvider.XAI && isXaiMultiAgentModel(modelId)) {
            ApiReasoningPolicy(
                controlStyle = ApiReasoningControlStyle.XAI_MULTI_AGENT,
                supportedEfforts = xaiMultiAgentEfforts,
                defaultEffort = ApiReasoningEffort.LOW,
            )
        } else if (provider == ApiProvider.XAI && modelId.startsWith("grok-4.3")) {
            ApiReasoningPolicy(
                controlStyle = ApiReasoningControlStyle.STANDARD,
                supportedEfforts =
                    listOf(
                        ApiReasoningEffort.NONE,
                        ApiReasoningEffort.LOW,
                        ApiReasoningEffort.MEDIUM,
                        ApiReasoningEffort.HIGH,
                    ),
                defaultEffort = ApiReasoningEffort.LOW,
            )
        } else {
            ApiReasoningPolicy(
                controlStyle = ApiReasoningControlStyle.STANDARD,
                supportedEfforts = standardReasoningEfforts,
                defaultEffort = ApiReasoningEffort.LOW,
            )
        }

    private fun String.matchesXaiMultiAgent(): Boolean = startsWith("grok-4.20-multi-agent")

    private fun String.matchesXaiGrok420Reasoning(): Boolean = startsWith("grok-4.20-reasoning")

    private fun String.matchesXaiGrok4ReasoningFamily(): Boolean {
        if (!startsWith("grok-4")) {
            return false
        }
        val isExcludedFamily =
            startsWith("grok-4.20-multi-agent") ||
                startsWith("grok-4.20-reasoning") ||
                startsWith("grok-4-fast-non-reasoning") ||
                startsWith("grok-4-1-fast-non-reasoning")
        if (isExcludedFamily) {
            return false
        }
        return contains("reasoning") || this == "grok-4" || startsWith("grok-4-") || startsWith("grok-4.3")
    }

    private fun String.matchesXaiGrok4NonReasoningFamily(): Boolean =
        startsWith("grok-4-fast-non-reasoning") ||
            startsWith("grok-4-1-fast-non-reasoning")

    private fun String.matchesXaiGrok3Mini(): Boolean = startsWith("grok-3-mini")

    private fun String.matchesXaiGrok3(): Boolean = this == "grok-3" || startsWith("grok-3-")

    private fun String.matchesXaiReasoningModel(): Boolean =
        matchesXaiMultiAgent() ||
            matchesXaiGrok420Reasoning() ||
            matchesXaiGrok4ReasoningFamily() ||
            matchesXaiGrok3() ||
            matchesXaiGrok3Mini()

    private fun String.matchesXaiGrok420Family(): Boolean = startsWith("grok-4.20-")

    private fun String.matchesXaiChatReasoningContentModel(): Boolean = matchesXaiGrok3Mini() || startsWith("grok-code-fast-1")

    /**
     * Prefixes for model IDs that support Anthropic `cache_control` (see Anthropic prompt caching docs).
     * Date-suffixed snapshot IDs (e.g. `claude-sonnet-4-20250514`) match via prefix.
     */
    private val ANTHROPIC_PROMPT_CACHE_MODEL_PREFIXES =
        listOf(
            "claude-sonnet-4-",
            "claude-opus-4-",
            "claude-haiku-4-",
            "claude-3-7-sonnet-",
            "claude-3-5-sonnet-",
            "claude-3-5-haiku-",
            "claude-3-opus-",
            "claude-3-sonnet-20240229",
            "claude-3-sonnet-20241022",
            "claude-3-haiku-20240307",
        )

    fun isFalTtsModelCandidate(modelId: String): Boolean {
        val lower = modelId.lowercase()
        return (lower.contains("tts") || lower.contains("speech")) &&
            !lower.contains("stable-audio") &&
            !lower.contains("ace-step") &&
            !lower.contains("elevenlabs")
    }

    fun isFalMusicModelCandidate(modelId: String): Boolean {
        val lower = modelId.lowercase()
        val isTtsOrSpeech = lower.contains("tts") || lower.contains("speech") || lower.contains("elevenlabs")
        val hasMusicHints =
            lower.contains("stable-audio") ||
                lower.contains("ace-step") ||
                lower.contains("music") ||
                lower.contains("audio")
        return hasMusicHints && !isTtsOrSpeech
    }
}
