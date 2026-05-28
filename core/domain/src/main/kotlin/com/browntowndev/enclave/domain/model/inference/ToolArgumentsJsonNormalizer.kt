package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Coerces common model output mistakes (e.g. `"false"` strings for boolean fields)
 * to match the tool JSON schema before kotlinx.serialization decoding.
 */
internal object ToolArgumentsJsonNormalizer {
    fun normalize(
        schema: ToolSchema,
        argumentsJson: String,
    ): String {
        val root =
            runCatching {
                kotlinx.serialization.json.Json
                    .parseToJsonElement(argumentsJson)
                    .jsonObject
            }.getOrElse {
                return argumentsJson
            }
        return normalizeObject(schema.properties, schema.required, root).toString()
    }

    private fun normalizeObject(
        properties: JsonObject,
        required: List<String>,
        obj: JsonObject,
    ): JsonObject =
        buildJsonObject {
            obj.forEach { (key, value) ->
                if (key !in required && value is JsonPrimitive && value.isString) {
                    val content = value.content.trim()
                    if (content.isEmpty() || content.equals("null", ignoreCase = true)) {
                        return@forEach
                    }
                }
                val propSchema = properties[key]?.jsonObject
                put(key, normalizeValue(propSchema, value))
            }
        }

    private fun normalizeValue(
        propSchema: JsonObject?,
        value: JsonElement,
    ): JsonElement {
        if (propSchema == null || value !is JsonPrimitive || !value.isString) {
            return value
        }
        return when (propSchema.resolveSchemaType()) {
            "boolean" ->
                when (value.content.trim().lowercase()) {
                    "true" -> JsonPrimitive(true)
                    "false" -> JsonPrimitive(false)
                    else -> value
                }
            else -> value
        }
    }

    private fun JsonObject.resolveSchemaType(): String? {
        val typeElement = this["type"] ?: return null
        return when (typeElement) {
            is JsonPrimitive -> typeElement.content
            is JsonArray ->
                typeElement
                    .mapNotNull { (it as? JsonPrimitive)?.content }
                    .firstOrNull { it != "null" }
            else -> null
        }
    }
}
