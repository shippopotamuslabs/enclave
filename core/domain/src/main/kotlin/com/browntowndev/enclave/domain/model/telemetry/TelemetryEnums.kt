package com.browntowndev.enclave.domain.model.telemetry

enum class TelemetryProviderKey {
    OPENAI,
    ANTHROPIC,
    LITERT,
    LLAMA,
    OPENROUTER,
    GOOGLE,
    XAI,
    OTHER,
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
