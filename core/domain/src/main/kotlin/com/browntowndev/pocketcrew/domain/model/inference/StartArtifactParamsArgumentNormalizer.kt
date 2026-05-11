package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Remote models often send `start_artifact` arguments shaped like the persisted document
 * (`version`, `metadata`, `blocks`) instead of the flat tool schema. This merges those
 * fields into the shape expected by [StartArtifactParams] before decoding.
 */
object StartArtifactParamsArgumentNormalizer {
    private val json = Json { ignoreUnknownKeys = true }

    fun normalize(argumentsJson: String): String {
        val element = runCatching { json.parseToJsonElement(argumentsJson) }.getOrNull()
        if (element !is JsonObject) return argumentsJson

        val root = element.toMutableMap()
        val metadata = root["metadata"]?.jsonObject

        val title = root.stringOrBlank("title") ?: metadata.stringOrBlank("title")
        if (title != null) {
            root["title"] = JsonPrimitive(title)
        }

        val author = root.stringOrBlank("author") ?: metadata.stringOrBlank("author")
        if (author != null) {
            root["author"] = JsonPrimitive(author)
        }

        return JsonObject(root).toString()
    }

    private fun Map<String, JsonElement>?.stringOrBlank(key: String): String? {
        if (this == null) return null
        val elem = this[key] ?: return null
        if (elem is JsonNull) return null
        val prim = elem as? JsonPrimitive ?: return null
        return prim.content.takeIf { it.isNotBlank() }
    }
}
