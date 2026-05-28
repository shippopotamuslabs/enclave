package com.browntowndev.enclave.domain.util

/**
 * Guards pre-retrieval web search so the embedding classifier does not fire on
 * in-app actions (artifacts, tool routing, follow-up clarifications).
 */
object SearchIntentPolicy {
    /** Minimum classifier probability to run pre-retrieval search. */
    const val PRE_RETRIEVAL_THRESHOLD: Float = 0.70f

    private val nonSearchPatterns =
        listOf(
            // Artifact / document generation (not live web lookup)
            Regex(
                """\b(create|make|write|generate|draft|produce)\b.{0,60}\b(document|artifact|pdf|report|markdown|docx)\b""",
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
            // Explicit tool routing
            Regex(
                """\b(use|using)\s+(the\s+)?[\w\s]{0,40}\btool\b""",
                RegexOption.IGNORE_CASE,
            ),
            // Clarifications / corrections in an ongoing turn
            Regex(
                """\bno[,.]?\s+i mean\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(that'?s|this is) not what i meant\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\bi want this (as|to be)\b""",
                RegexOption.IGNORE_CASE,
            ),
            // Local coding / editing tasks
            Regex(
                """\b(refactor|debug|unit test|proofread|translate)\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(format|convert)\s+(this|it|that)\s+(as|to)\b""",
                RegexOption.IGNORE_CASE,
            ),
        )

    fun isHeuristicNonSearchPrompt(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return true
        return nonSearchPatterns.any { it.containsMatchIn(trimmed) }
    }

    private val concurrentResearchPatterns =
        listOf(
            Regex(
                """\b(research|look up|find out|investigate)\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(latest|current|today|recent|breaking)\b""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """\b(news|headlines|mortgage rates|stock price)\b""",
                RegexOption.IGNORE_CASE,
            ),
        )

    fun hasConcurrentResearchIntent(prompt: String): Boolean {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return false
        return concurrentResearchPatterns.any { it.containsMatchIn(trimmed) }
    }
}
