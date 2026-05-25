package com.browntowndev.enclave.domain.model.telemetry

enum class TelemetryProviderKey {
    OPENAI,
    ANTHROPIC,
    LITERT,
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
    LOCAL_PRICING_TABLE,
    NOT_AVAILABLE,
}
