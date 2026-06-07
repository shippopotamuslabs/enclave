package com.browntowndev.enclave.domain.model.inference

/**
 * Supported API providers for BYOK (Bring Your Own Key) configuration.
 */
enum class ApiProvider(
    val displayName: String,
) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
    XAI("xAI"),
    GOOGLE("Google"),
    FAL("fal.ai"),
    ;

    fun defaultBaseUrl(): String? =
        when (this) {
            GOOGLE -> "https://generativelanguage.googleapis.com"
            OPENROUTER -> "https://openrouter.ai/api/v1"
            XAI -> "https://api.x.ai/v1"
            FAL -> "https://queue.fal.run"
            else -> null
        }

    val supportsChat: Boolean
        get() =
            when (this) {
                FAL -> false
                ANTHROPIC, OPENAI, OPENROUTER, XAI, GOOGLE -> true
            }

    /** Model list/detail APIs that include per-million token pricing for telemetry estimates. */
    val exposesDiscoveryModelPricing: Boolean
        get() =
            when (this) {
                OPENROUTER, XAI -> true
                ANTHROPIC, OPENAI, GOOGLE, FAL -> false
            }

    val supportsRealtimeVoice: Boolean
        get() =
            when (this) {
                OPENAI, GOOGLE, XAI -> true
                ANTHROPIC, OPENROUTER, FAL -> false
            }
}
