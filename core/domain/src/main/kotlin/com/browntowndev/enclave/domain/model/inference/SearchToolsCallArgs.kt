package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SearchToolsCallArgs(
    val query: String,
    val limit: Int,
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(argumentsJson: String): SearchToolsCallArgs {
            val args =
                runCatching { json.parseToJsonElement(argumentsJson).jsonObject }
                    .getOrDefault(buildJsonObject {})
            val query = args.readStringField("capability").ifEmpty { args.readStringField("query") }
            val limit = args.readIntField("limit") ?: ToolSearchThresholds.DEFAULT_SEARCH_LIMIT
            return SearchToolsCallArgs(query = query, limit = limit)
        }

        private fun JsonObject.readStringField(field: String): String =
            runCatching { this[field]?.jsonPrimitive?.content.orEmpty() }
                .getOrDefault("")

        private fun JsonObject.readIntField(field: String): Int? =
            runCatching {
                val primitive = this[field]?.jsonPrimitive ?: return@runCatching null
                primitive.content.toIntOrNull()
                    ?: primitive.content.toDoubleOrNull()?.toInt()
            }.getOrNull()
    }
}
