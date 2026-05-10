package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object ArtifactBlockUtil {
    private val json = Json { ignoreUnknownKeys = true }

    fun getVisibleText(block: JsonElement): String {
        return try {
            val obj = block.jsonObject
            val type = obj["type"]?.jsonPrimitive?.content ?: return ""
            when (type) {
                "heading" -> obj["text"]?.jsonPrimitive?.content ?: ""
                "paragraph" -> obj["text"]?.jsonPrimitive?.content ?: ""
                "list" -> (obj["items"] as? JsonArray)?.joinToString("\n") { it.jsonPrimitive.content } ?: ""
                "table" -> {
                    val header = (obj["header"] as? JsonArray)?.joinToString(" | ") { it.jsonPrimitive.content } ?: ""
                    val rows = (obj["rows"] as? JsonArray)?.joinToString("\n") { row ->
                        (row as? JsonArray)?.joinToString(" | ") { it.jsonPrimitive.content } ?: ""
                    } ?: ""
                    "$header\n$rows"
                }
                "code" -> obj["text"]?.jsonPrimitive?.content ?: ""
                "math" -> obj["latex"]?.jsonPrimitive?.content ?: ""
                else -> ""
            }
        } catch (_: Exception) {
            ""
        }
    }
}
