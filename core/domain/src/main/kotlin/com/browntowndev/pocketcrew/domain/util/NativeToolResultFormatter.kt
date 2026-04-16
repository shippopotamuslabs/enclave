package com.browntowndev.pocketcrew.domain.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility for formatting and truncating tool results specifically for local (native) models
 * where context windows are limited.
 */
object NativeToolResultFormatter {

    /**
     * Truncates large text fields in tool results to prevent context window overflow.
     * Focused on 'content' (search) and 'raw_content' (extract) fields.
     *
     * Calculates a strict budget based on the context window and estimated used tokens.
     */
    fun truncateToolResult(
        resultJson: String,
        contextWindowTokens: Int,
        estimatedUsedTokens: Int,
        bufferTokens: Int = 1000,
        charsPerToken: Int = 4
    ): String {
        return try {
            val availableTokens = contextWindowTokens - estimatedUsedTokens - bufferTokens
            if (availableTokens <= 0) {
                return """{"error": "cannot read page, context window too full"}"""
            }

            val payload = JSONObject(resultJson)
            val results = payload.optJSONArray("results") ?: return resultJson
            if (results.length() == 0) return resultJson

            val availableChars = availableTokens * charsPerToken
            val maxCharsPerResult = maxOf(100, availableChars / results.length())

            var truncated = false
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue

                // Truncate 'content' (search) or 'raw_content' (extract)
                listOf("content", "raw_content").forEach { field ->
                    val original = item.optString(field, "")
                    if (original.length > maxCharsPerResult) {
                        item.put(field, original.take(maxCharsPerResult) + "... (truncated for context)")
                        truncated = true
                    }
                }
            }

            if (truncated) {
                payload.toString()
            } else {
                resultJson
            }
        } catch (e: Exception) {
            resultJson
        }
    }

    /**
     * Parses URLs from model output, handling both comma-separated strings and JSON-like arrays.
     */
    fun parseUrls(rawUrls: String): List<String> {
        return when {
            rawUrls.contains(",") -> {
                rawUrls.split(",")
                    .map { it.trim(' ', '"', '\'', '[', ']') }
                    .filter { it.startsWith("http") }
            }
            rawUrls.startsWith("[") -> {
                try {
                    val array = JSONArray(rawUrls)
                    (0 until array.length()).map { array.getString(it) }
                } catch (e: Exception) {
                    listOf(rawUrls.trim(' ', '"', '\''))
                }
            }
            else -> listOf(rawUrls.trim(' ', '"', '\''))
        }
    }
}
