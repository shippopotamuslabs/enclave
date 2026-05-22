package com.browntowndev.enclave.core.data.mapper

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ApiModelMapper {
    fun serializeCustomHeaders(map: Map<String, String>): String = Json.encodeToString(map)

    fun deserializeCustomHeaders(json: String): Map<String, String> {
        if (json.isBlank()) return emptyMap()
        return try {
            Json.decodeFromString<Map<String, String>>(json)
        } catch (_: Exception) {
            // Return empty map if parsing fails
            emptyMap()
        }
    }
}
