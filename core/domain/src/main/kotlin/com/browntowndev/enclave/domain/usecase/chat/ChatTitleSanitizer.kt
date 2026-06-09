package com.browntowndev.enclave.domain.usecase.chat

/**
 * Normalizes and quality-gates raw on-device title-generator output.
 *
 * On-device title models can still degenerate on path-like or code-heavy prompts,
 * echoing input fragments (for example `er/State/State/State/...`). When output fails
 * quality checks, callers should keep the deterministic fallback title from chat creation.
 */
object ChatTitleSanitizer {
    private const val MAX_TITLE_LENGTH = 60

    private val SPECIAL_TOKEN_REGEX = Regex("<[^>]*>")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val HTML_ENTITY_REGEX = Regex("#\\d+;")
    private val REPEATED_PATH_SEGMENT_REGEX = Regex("""(?i)([a-z0-9]{2,})/\1""")
    private val MODEL_UPDATE_ARTIFACT_REGEX = Regex("""\(\s*Update(?:\s*\d+)?\s*\)""", RegexOption.IGNORE_CASE)

    fun sanitize(raw: String?): String? {
        if (raw == null) return null
        val cleaned = clean(raw)
        if (cleaned.isBlank() || isLowQuality(cleaned)) return null
        return if (cleaned.length > MAX_TITLE_LENGTH) {
            cleaned.take(MAX_TITLE_LENGTH).trimEnd() + "…"
        } else {
            cleaned
        }
    }

    private fun clean(raw: String): String =
        raw
            .replace(SPECIAL_TOKEN_REGEX, " ")
            .replace(HTML_ENTITY_REGEX, "'")
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .trim('"', '\'', '“', '”', '‘', '’')
            .trim()

    private fun isLowQuality(cleaned: String): Boolean {
        val slashCount = cleaned.count { it == '/' }
        return cleaned.length < 2 ||
            slashCount >= 2 ||
            cleaned.endsWith('/') ||
            REPEATED_PATH_SEGMENT_REGEX.containsMatchIn(cleaned) ||
            MODEL_UPDATE_ARTIFACT_REGEX.containsMatchIn(cleaned) ||
            cleaned.count { it.isLetter() } < 2
    }
}
