package com.browntowndev.pocketcrew.core.data.util

object FtsSanitizer {
    /**
     * Sanitizes a search query for SQLite FTS MATCH clauses.
     * Removes special characters that cause FTS parsing errors and appends wildcards to each term.
     * Supports Unicode characters for non-English search queries.
     */
    fun sanitize(query: String): String {
        if (query.isBlank()) return ""
        
        // Remove special characters while preserving Unicode word characters and spaces.
        // (?U) enables Unicode character classes.
        val sanitized = query.replace(Regex("""(?U)[^\w\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            
        if (sanitized.isEmpty()) return ""
        
        // Filter out SQLite FTS reserved keywords that could cause syntax errors if standalone.
        val keywords = setOf("AND", "OR", "NOT", "NEAR")
        val words = sanitized.split(" ").filter { it.uppercase() !in keywords }
        
        if (words.isEmpty()) return ""
        
        // Append wildcard to EVERY word for better prefix matching (e.g., "kot cor" -> "kot* cor*")
        return words.joinToString(" ") { "$it*" }
    }
}
