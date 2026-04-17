package com.browntowndev.pocketcrew.core.data.util

private object FtsConstants {
    val invalidCharsRegex = Regex("""[^\p{L}\p{N}\p{M}\s_]""")
    val multipleSpacesRegex = Regex("""\s+""")
    val reservedKeywords = setOf("AND", "OR", "NOT", "NEAR")
}

object FtsSanitizer {
    /**
     * Sanitizes a search query for SQLite FTS MATCH clauses.
     * Removes special characters that cause FTS parsing errors and appends wildcards to each term.
     * Strips FTS boolean operators (AND, OR, NOT, NEAR) to prevent syntax errors from user input.
     * Supports Unicode characters for non-English search queries.
     */
    fun sanitize(query: String): String {
        if (query.isBlank()) return ""

        val sanitized = query.replace(FtsConstants.invalidCharsRegex, " ")
            .replace(FtsConstants.multipleSpacesRegex, " ")
            .trim()

        if (sanitized.isEmpty()) return ""

        val keywordsSet = FtsConstants.reservedKeywords
        val words = sanitized.split(" ").filter { it.uppercase() !in keywordsSet }

        if (words.isEmpty()) return ""

        return words.joinToString(" ") { "$it*" }
    }

    /**
     * Sanitizes multiple search queries and joins them with FTS OR operator.
     * Used by tool executors that accept multiple query variants to cast a wider net.
     *
     * Each query is individually sanitized (stripping special chars, adding wildcards),
     * then joined with " OR " so FTS matches any of them.
     *
     * For example: ["cow", "cow photo", "moo"] -> "cow* OR cow* photo* OR moo*"
     */
    fun sanitizeOrQuery(queries: List<String>): String {
        val sanitizedQueries = queries
            .map { sanitize(it) }
            .filter { it.isNotBlank() }

        if (sanitizedQueries.isEmpty()) return ""

        return sanitizedQueries.joinToString(" OR ")
    }
}
