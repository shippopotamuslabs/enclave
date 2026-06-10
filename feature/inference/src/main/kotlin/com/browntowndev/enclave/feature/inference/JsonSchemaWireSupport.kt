package com.browntowndev.enclave.feature.inference

import com.anthropic.core.JsonValue
import com.openai.core.JsonValue as OpenAiJsonValue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Shared kotlinx-json → provider wire map conversion for tool JSON schemas.
 * [JsonNull] maps to Kotlin `null`, not the string `"null"`.
 */
internal object JsonSchemaWireSupport {
    fun JsonObject.toWireMap(): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>()
        forEach { (key, value) -> map[key] = value.toWireValue() }
        return map
    }

    fun JsonElement.toWireValue(): Any? =
        when (this) {
            JsonNull -> null
            is JsonPrimitive -> toWirePrimitive()
            is JsonObject -> toWireMap()
            is JsonArray -> map { it.toWireValue() }
        }

    private fun JsonPrimitive.toWirePrimitive(): Any =
        if (isString) {
            content
        } else {
            content.toBooleanStrictOrNull()
                ?: content.toLongOrNull()
                ?: content.toDoubleOrNull()
                ?: content
        }

    fun JsonElement.toAnthropicJsonValue(): JsonValue =
        when (val wire = toWireValue()) {
            null -> JsonValue.from(null)
            is Map<*, *> -> JsonValue.from(wire)
            is List<*> -> JsonValue.from(wire)
            else -> JsonValue.from(wire)
        }

    fun JsonObject.toOpenAiJsonValue(): OpenAiJsonValue = OpenAiJsonValue.from(toWireMap())
}
