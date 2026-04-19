package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Utility for formatting and truncating tool results specifically for local (native) models
 * where context windows are limited.
 */
object NativeToolResultFormatter {

    /**
     * Truncates large text fields in tool results to prevent context window overflow.
     * Focused on 'content' (search) and 'raw_content' (extract) fields.
     *
     * Calculates a strict budget based on the context window and used tokens,
     * using accurate token counting via [TokenCounter] when provided.
     */
    fun truncateToolResult(
        resultJson: String,
        contextWindowTokens: Int,
        estimatedUsedTokens: Int,
        bufferTokens: Int = 1000,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
        modelId: String? = null,
    ): String {
        return try {
            val availableTokens = contextWindowTokens - estimatedUsedTokens - bufferTokens
            if (availableTokens <= 0) {
                return """{"error": "cannot read page, context window too full"}"""
            }

            val payload = Json.parseToJsonElement(resultJson).jsonObject
            val results = payload["results"]?.jsonArray ?: return resultJson
            if (results.isEmpty()) return resultJson

            // Divide available tokens across results, then use token counter
            // for precise truncation. If modelId is unavailable, fall back to the
            // standard 4-chars-per-token BPE estimate.
            val maxTokensPerResult = maxOf(10, availableTokens / results.size)
            val maxCharsPerResult = maxOf(100, maxTokensPerResult * 4)  // fallback BPE estimate

            var truncated = false
            val newResults = buildJsonArray {
                for (itemElement in results) {
                    val item = itemElement.jsonObject
                    val newItemMap = item.toMutableMap()

                    // Truncate 'content' (search) or 'raw_content' (extract)
                    listOf("content", "raw_content").forEach { field ->
                        val original = item[field]?.jsonPrimitive?.content ?: ""
                        // Use tokenCounter for precise truncation when modelId is available,
                        // otherwise fall back to the BPE-estimated char limit.
                        val shouldTruncate = if (modelId != null) {
                            tokenCounter.countTokens(original, modelId) > maxTokensPerResult
                        } else {
                            original.length > maxCharsPerResult
                        }
                        if (shouldTruncate) {
                            // Truncate to the char-based limit (still needed even with precise
                            // counting, since we can't remove tokens precisely from strings).
                            newItemMap[field] = JsonPrimitive(original.take(maxCharsPerResult) + "... (truncated for context)")
                            truncated = true
                        }
                    }
                    add(JsonObject(newItemMap))
                }
            }

            if (truncated) {
                val newPayload = buildJsonObject {
                    payload.forEach { (k, v) ->
                        if (k == "results") put(k, newResults)
                        else put(k, v)
                    }
                }
                newPayload.toString()
            } else {
                resultJson
            }
        } catch (e: Exception) {
            resultJson
        }
    }

    /**
     * Truncates a tool result string to fit within the context window for API models.
     * Unlike [truncateToolResult] which understands Tavily result structure,
     * this method handles any string result by truncating it to fit within the
     * available token budget.
     *
     * Returns the original string if it fits, or a truncated version with a marker.
     * If [availableTokens] is <= 0, returns a minimal error JSON.
     */
    fun truncateForApiContext(
        resultJson: String,
        availableTokens: Int,
        tokenCounter: TokenCounter = JTokkitTokenCounter,
        modelId: String? = null,
    ): String {
        if (availableTokens <= 0) {
            return """{"error": "cannot read page, context window too full"}"""
        }
        val resultTokens = tokenCounter.countTokens(resultJson, modelId)
        if (resultTokens <= availableTokens) {
            return resultJson
        }
        // Truncate to the available token budget using BPE estimate.
        // We can't precisely remove tokens from strings, so we truncate by
        // character count and add a truncation marker.
        val maxChars = maxOf(100, availableTokens * 4)
        return resultJson.take(maxChars) + "... (truncated for context)"
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
                    val array = Json.parseToJsonElement(rawUrls).jsonArray
                    array.map { it.jsonPrimitive.content }
                } catch (e: Exception) {
                    listOf(rawUrls.trim(' ', '"', '\''))
                }
            }
            else -> listOf(rawUrls.trim(' ', '"', '\''))
        }
    }
}