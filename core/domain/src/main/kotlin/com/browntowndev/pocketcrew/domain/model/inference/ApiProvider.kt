package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Supported API providers for BYOK (Bring Your Own Key) configuration.
 */
enum class ApiProvider(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    OPENROUTER("OpenRouter"),
    XAI("xAI"),
    GOOGLE("Google");

    fun defaultBaseUrl(): String? = when (this) {
        GOOGLE -> "https://generativelanguage.googleapis.com"
        OPENROUTER -> "https://openrouter.ai/api/v1"
        XAI -> "https://api.x.ai/v1"
        else -> null
    }
}
