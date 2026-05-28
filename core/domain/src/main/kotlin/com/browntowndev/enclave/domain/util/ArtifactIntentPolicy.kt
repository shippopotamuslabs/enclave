package com.browntowndev.enclave.domain.util

/**
 * Host-side heuristic for artifact / document generation tool activation.
 */
object ArtifactIntentPolicy {
    private val artifactPatterns =
        listOf(
            Regex(
                """\b(create|make|write|generate|draft|produce)\b.{0,60}\b(document|artifact|pdf|report|markdown|docx|word)\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(document|artifact)\s+(creation|tool|generator)\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(as an artifact|export (this|it|that)|artifact (export|tool))\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(append to the artifact|update the document)\b""",
                RegexOption.IGNORE_CASE,
            ),
        )

    fun needsArtifactTools(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return false
        return artifactPatterns.any { it.containsMatchIn(trimmed) }
    }
}
