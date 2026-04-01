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
     * Supports Unicode characters for non-English search queries.
     */
    fun sanitize(query: String): String {
        if (query.isBlank()) return ""
        
        // Remove special characters while preserving Unicode word characters, spaces and underscores.
        // We use explicit Unicode properties \p{L} (Letters), \p{N} (Numbers), and \p{M} (Marks)
        // instead of (?U)\w to avoid PatternSyntaxException on Android.
        val sanitized = query.replace(FtsConstants.invalidCharsRegex, " ")
            .replace(FtsConstants.multipleSpacesRegex, " ")
            .trim()
            
        if (sanitized.isEmpty()) return ""
        
        // Filter out SQLite FTS reserved keywords that could cause syntax errors if standalone.
        val keywordsSet = FtsConstants.reservedKeywords
        val words = sanitized.split(" ").filter { it.uppercase() !in keywordsSet }
        
        if (words.isEmpty()) return ""
        
        // Append wildcard to EVERY word for better prefix matching (e.g., "kot cor" -> "kot* cor*")
        return words.joinToString(" ") { "$it*" }
    }
}
