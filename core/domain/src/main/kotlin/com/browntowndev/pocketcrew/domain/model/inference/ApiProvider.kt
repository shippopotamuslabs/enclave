package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Supported API providers for BYOK (Bring Your Own Key) configuration.
 */
enum class ApiProvider(val displayName: String) {
    ANTHROPIC("Anthropic"),
    OPENAI("OpenAI"),
    GOOGLE("Google"),
    SELF_HOSTED("Self-Hosted"),
    SUBSCRIPTION("Subscription")
}