package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

object LiveVoiceGeminiSchemaMapper {
    fun toFunctionDeclaration(tool: ToolDefinition): JsonObject =
        buildJsonObject {
            put("name", tool.name)
            put("description", tool.description)
            put("parameters", tool.schema.toJsonObject().toGeminiSchemaObject())
        }
}

private val unsupportedSchemaKeys =
    setOf(
        "format",
        "default",
        "oneOf",
        "anyOf",
        "allOf",
        "\$ref",
        "additionalProperties",
    )

private fun JsonObject.toGeminiSchemaObject(): JsonObject = toGeminiSchemaElement().jsonObject

private fun JsonElement.toGeminiSchemaElement(): JsonElement =
    when (this) {
        is JsonObject -> {
            val typeStr = (this["type"] as? JsonPrimitive)?.content
            buildJsonObject {
                put("type", mapGeminiType(typeStr))
                forEach { (key, value) ->
                    if (key in unsupportedSchemaKeys) return@forEach
                    when (key) {
                        "type" -> Unit
                        "description" -> put(key, value)
                        "properties" ->
                            putJsonObject(key) {
                                value.jsonObject.forEach { (propKey, propValue) ->
                                    put(propKey, propValue.toGeminiSchemaElement())
                                }
                            }
                        "items" -> put(key, value.toGeminiSchemaElement())
                        "required" -> put(key, value)
                    }
                }
            }
        }
        else -> this
    }

private fun mapGeminiType(typeStr: String?): String =
    when (typeStr) {
        "string" -> "STRING"
        "integer" -> "INTEGER"
        "number" -> "NUMBER"
        "boolean" -> "BOOLEAN"
        "array" -> "ARRAY"
        "object" -> "OBJECT"
        else -> "STRING"
    }
