package com.browntowndev.enclave.core.data.mapper.telemetry

internal object TelemetryPayloadRedactor {
    private val emailPattern =
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val unixPathPattern =
        Regex("""/(?:home|Users|var|tmp|data|storage)[^\s"'\\]*""")

    fun redact(value: String?): String? {
        if (value.isNullOrBlank()) {
            return value
        }
        return value
            .replace(emailPattern, "[REDACTED_EMAIL]")
            .replace(unixPathPattern, "[REDACTED_PATH]")
    }
}
