package com.browntowndev.pocketcrew.core.data.mapper

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

object ApiModelMapper {
    fun serializeCustomHeaders(map: Map<String, String>): String {
        return Json.encodeToString(map)
    }

    fun deserializeCustomHeaders(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            Json.decodeFromString<Map<String, String>>(json)
        } catch (e: Exception) {
            // Return empty map if parsing fails
            emptyMap()
        }
    }
}
