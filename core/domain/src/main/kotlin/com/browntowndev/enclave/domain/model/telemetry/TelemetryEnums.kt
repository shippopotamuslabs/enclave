package com.browntowndev.enclave.domain.model.telemetry

enum class TelemetryProviderKey {
    OPENAI,
    ANTHROPIC,
    LITERT,
    LLAMA,
    OPENROUTER,
    GOOGLE,
    XAI,
    ZAI,
    DEEPSEEK,
    OTHER,
    ;

    /**
     * Providers whose `input_tokens` usage field excludes cached prompt tokens (Anthropic-style).
     * Only real Anthropic reports this way — `input_tokens` excludes cache read/creation.
     * All other providers (including Z.ai, now on the OpenAI path) include cache-read inside
     * `input_tokens` / `prompt_tokens`, so cache-accounting logic must NOT add it back.
     */
    val reportsCacheExclusiveInput: Boolean
        get() = this == ANTHROPIC

    companion object {
        /**
         * Resolves a provider display label (as emitted in telemetry) to a [TelemetryProviderKey].
         * Falls back to [OTHER] for unrecognized labels. This is the canonical label→key resolver;
         * provider-specific normalizers should delegate here rather than re-implementing the map.
         */
        fun fromLabel(label: String): TelemetryProviderKey =
            when (label) {
                "OpenAI" -> OPENAI
                "Anthropic" -> ANTHROPIC
                "Z.ai", "ZAI" -> ZAI
                "LiteRT" -> LITERT
                "llama.cpp" -> LLAMA
                "OpenRouter" -> OPENROUTER
                "Google" -> GOOGLE
                "xAI" -> XAI
                "DeepSeek", "DEEPSEEK" -> DEEPSEEK
                else -> OTHER
            }
    }
}

enum class TelemetrySourceKind {
    REMOTE,
    LOCAL,
}

enum class TelemetryCostStatus {
    EXACT,
    ESTIMATED,
    UNAVAILABLE,
}

enum class TelemetryCostSource {
    PROVIDER,
    SAVED_MODEL_PRICING,
    NOT_AVAILABLE,
}
