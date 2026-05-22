package com.browntowndev.enclave.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses [append_blocks] tool JSON returned by [com.browntowndev.enclave.domain.port.inference.ToolExecutorPort].
 *
 * Providers sometimes deliver the payload as a **JSON string** (double-encoded) at the root. The
 * [fullDocument] field may be either a nested JSON object or a **string** containing serialized JSON
 * (defensive parsing for alternate encodings).
 */
object AppendBlocksToolResultParser {
    private val json = Json { ignoreUnknownKeys = true }

    data class CompleteArtifact(
        val title: String,
        /** Canonical JSON string of the Rust `Document` object (version, metadata, blocks). */
        val documentJson: String,
    )

    @Suppress("ReturnCount")
    fun parseCompleteArtifact(resultJson: String): CompleteArtifact? {
        val payload = unwrapToJsonObject(resultJson) ?: return null
        if ((payload["status"] as? JsonPrimitive)?.content == "error") return null
        if (!isCompleteFlag(payload["isComplete"])) return null
        val title = (payload["title"] as? JsonPrimitive)?.content ?: "Document"
        val fullDocElement = payload["fullDocument"] ?: return null
        val fullDocObject = elementToJsonObject(fullDocElement) ?: return null
        return CompleteArtifact(title = title, documentJson = fullDocObject.toString())
    }

    private fun isCompleteFlag(element: JsonElement?): Boolean =
        when (val e = element) {
            is JsonPrimitive ->
                e.booleanOrNull == true ||
                    e.content.equals("true", ignoreCase = true) ||
                    e.content == "1"
            else -> false
        }

    private fun unwrapToJsonObject(raw: String): JsonObject? =
        try {
            var element: JsonElement = json.parseToJsonElement(raw)
            while (element is JsonPrimitive && element.isString) {
                element = json.parseToJsonElement(element.content)
            }
            element as? JsonObject
        } catch (_: Exception) {
            null
        }

    private fun elementToJsonObject(element: JsonElement): JsonObject? =
        when (element) {
            is JsonObject -> element
            is JsonPrimitive -> {
                if (!element.isString) return null
                try {
                    json.parseToJsonElement(element.content).jsonObject
                } catch (_: Exception) {
                    null
                }
            }
            else -> null
        }
}
