package com.browntowndev.enclave.domain.util.telemetry

/**
 * Scrubs common PII patterns from telemetry text fields before persistence or remote upload.
 */
object TelemetryPiiScrubber {
    private val emailPattern =
        Regex("""[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}""")
    private val unixPathPattern =
        Regex("""/(?:home|Users|var|tmp|data|storage)[^\s"'\\]*""")
    private val creditCardPattern =
        Regex(
            """\b(?:(?:\d{4}[\s-]?){3}\d{1,4}|3[47]\d{2}[\s-]?\d{6}[\s-]?\d{5})\b""",
        )

    fun scrub(value: String?): String? {
        if (value.isNullOrBlank()) {
            return value
        }
        return value
            .replace(emailPattern, REDACTED_EMAIL)
            .replace(creditCardPattern, REDACTED_CARD)
            .replace(unixPathPattern, REDACTED_PATH)
    }

    const val REDACTED_EMAIL = "[REDACTED_EMAIL]"
    const val REDACTED_CARD = "[REDACTED_CARD]"
    const val REDACTED_PATH = "[REDACTED_PATH]"
}
