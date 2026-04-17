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

            // Divide available tokens across results, then convert to max chars per result
            val maxTokensPerResult = maxOf(10, availableTokens / results.size)
            // Estimate max chars from max tokens. JTokkit is a BPE encoder so 1 token ≈ 4 chars
            // on average for English text. We use this as a safe upper bound for truncation.
            val maxCharsPerResult = maxOf(100, maxTokensPerResult * 4)

            var truncated = false
            val newResults = buildJsonArray {
                for (itemElement in results) {
                    val item = itemElement.jsonObject
                    val newItemMap = item.toMutableMap()

                    // Truncate 'content' (search) or 'raw_content' (extract)
                    listOf("content", "raw_content").forEach { field ->
                        val original = item[field]?.jsonPrimitive?.content ?: ""
                        if (original.length > maxCharsPerResult) {
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