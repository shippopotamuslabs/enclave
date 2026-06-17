package com.browntowndev.enclave.domain.model.chat

internal object ChatInferenceUserErrorSanitizer {
    const val REDACTED_SECRET = "[redacted]"
    private const val MAX_DETAIL_CHARS = 500
    private const val MAX_TITLE_CHARS = 500

    private val OPENAI_STYLE_KEY = Regex("""sk-[A-Za-z0-9_-]{8,}""", RegexOption.IGNORE_CASE)
    private val ANTHROPIC_KEY = Regex("""sk-ant-[A-Za-z0-9_-]{8,}""", RegexOption.IGNORE_CASE)
    private val GOOGLE_API_KEY = Regex("""AIza[A-Za-z0-9_-]{20,}""")
    private val XAI_API_KEY = Regex("""xai-[A-Za-z0-9_-]{8,}""", RegexOption.IGNORE_CASE)
    private val ZAI_API_KEY = Regex("""\b[a-f0-9]{8,}\.[A-Za-z0-9_-]{8,}\b""")
    private val JSON_API_KEY = Regex("""(?i)"api_key"\s*:\s*"[^"]+"""")
    private val BASIC_AUTH_URL = Regex("""https?://[^\s/]+:[^\s@]+@[^\s]+""")

    private val SECRET_PATTERNS =
        listOf(
            OPENAI_STYLE_KEY,
            ANTHROPIC_KEY,
            GOOGLE_API_KEY,
            XAI_API_KEY,
            ZAI_API_KEY,
            JSON_API_KEY,
            BASIC_AUTH_URL,
            Regex("""Bearer\s+\S+""", RegexOption.IGNORE_CASE),
            Regex("""api[_-]?key[=:]\s*\S+""", RegexOption.IGNORE_CASE),
            Regex("""x-api-key[=:]\s*\S+""", RegexOption.IGNORE_CASE),
        )

    fun sanitizeUserFacingTitle(
        candidate: String?,
        fallback: String,
    ): String {
        if (candidate.isNullOrBlank()) return fallback
        val redacted = redactSecrets(candidate)
        if (looksLikeAgentInternalMessage(redacted) || looksLikeStackTrace(redacted)) {
            return fallback
        }
        return sanitizeProviderDetail(redacted)
            ?: redacted.take(MAX_TITLE_CHARS).takeIf { it.isNotBlank() }
            ?: fallback
    }

    fun sanitizeProviderDetail(candidate: String?): String? {
        if (candidate.isNullOrBlank()) return null
        val redacted = redactSecrets(candidate)
        if (redacted.contains(REDACTED_SECRET)) {
            return null
        }
        val cleaned = ChatInferenceSdkMetadata.stripProviderDetailMetadata(redacted)

        if (isUnsafeProviderDetail(cleaned)) {
            return null
        }

        return cleaned.take(MAX_DETAIL_CHARS)
    }

    fun looksLikeAgentInternalMessage(message: String): Boolean =
        message.contains("invalid state transition", ignoreCase = true) ||
            message.contains("context reference not found", ignoreCase = true) ||
            message.contains("tool repair failed", ignoreCase = true) ||
            (
                message.contains("compaction failed", ignoreCase = true) &&
                    !message.contains("exceeds hard ceiling", ignoreCase = true) &&
                    !message.contains("projected prompt", ignoreCase = true)
            )

    fun looksLikeStackTrace(message: String): Boolean =
        message.contains("\tat ") ||
            message.lineSequence().any { line ->
                ChatInferenceSdkMetadata.STACK_TRACE_LINE.containsMatchIn(line)
            }

    private fun isUnsafeProviderDetail(cleaned: String): Boolean =
        cleaned.isBlank() ||
            looksLikeStackTrace(cleaned) ||
            looksLikeInternalMetadata(cleaned) ||
            looksLikeAgentInternalMessage(cleaned) ||
            OPENAI_STYLE_KEY.containsMatchIn(cleaned)

    private fun looksLikeInternalMetadata(message: String): Boolean =
        message.contains("provider_code=", ignoreCase = true) ||
            message.contains("error_type=", ignoreCase = true) ||
            message.startsWith("{code=", ignoreCase = true)

    private fun redactSecrets(value: String): String {
        var redacted = value
        SECRET_PATTERNS.forEach { pattern ->
            redacted = redacted.replace(pattern, REDACTED_SECRET)
        }
        return redacted
    }
}
