package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

/**
 * Marker interface for tool parameters.
 */
interface ToolParameters {
    companion object {
        /**
         * Extracts top-level argument hints from a [ToolSchema]. Nested object schemas,
         * `oneOf`, `anyOf`, and `definitions` are intentionally excluded — the catalog
         * stays compact while full schemas live behind the disclosure tool.
         */
        fun extractArgumentHints(schema: ToolSchema): Pair<List<ToolArgumentHint>, List<ToolArgumentHint>> {
            val required = mutableListOf<ToolArgumentHint>()
            val optional = mutableListOf<ToolArgumentHint>()
            schema.properties.forEach { (name, element) ->
                val obj = element as? JsonObject ?: return@forEach
                val rawType = obj["type"]
                val type =
                    when (rawType) {
                        is JsonPrimitive -> rawType.content
                        is JsonArray ->
                            rawType
                                .mapNotNull { (it as? JsonPrimitive)?.content }
                                .firstOrNull { it != "null" }
                                ?: "string"
                        else -> "string"
                    }
                val repeated = type == "array"
                val hint = ToolArgumentHint(name = name, type = type, repeated = repeated)
                if (schema.required.contains(name)) {
                    required += hint
                } else {
                    optional += hint
                }
            }
            return required to optional
        }
    }
}

@kotlinx.serialization.Serializable
data class DiscloseToolSchemasParams(
    @ToolParam(description = "Deferred tool names whose full schemas should be disclosed.")
    val tool_names: List<String>,
) : ToolParameters

@kotlinx.serialization.Serializable
data class ReadContextReferenceParams(
    @ToolParam(description = "The ID of the context reference to retrieve.")
    val reference_id: String,
) : ToolParameters

/**
 * Annotation to provide descriptions for tool parameters.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolParam(
    val description: String = "",
    val required: Boolean = true,
)

data class ToolSchema(
    val properties: JsonObject,
    val required: List<String>,
    val fullSchemaOverride: JsonObject? = null,
) {
    fun toJsonObject(): JsonObject =
        fullSchemaOverride ?: buildJsonObject {
            put("type", "object")
            put("properties", properties)
            if (required.isNotEmpty()) {
                putJsonArray("required") {
                    required.forEach { add(JsonPrimitive(it)) }
                }
            }
        }

    override fun toString(): String = toJsonObject().toString()

    companion object {
        fun fromFullSchema(json: String): ToolSchema {
            val jsonObject =
                kotlinx.serialization.json.Json
                    .parseToJsonElement(json) as JsonObject
            return ToolSchema(
                properties = (jsonObject["properties"] ?: buildJsonObject { }) as JsonObject,
                required = (jsonObject["required"] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList(),
                fullSchemaOverride = jsonObject,
            )
        }
    }
}

object ToolSchemaGenerator {
    fun <T : ToolParameters> generateSchema(klass: KClass<T>): ToolSchema =
        ToolSchema(JsonObject(generateProperties(klass)), getRequired(klass))

    internal fun toolParamFor(
        klass: KClass<*>,
        prop: KProperty<*>,
    ): ToolParam? =
        prop.findAnnotation<ToolParam>()
            ?: prop.javaField?.getAnnotation(ToolParam::class.java)
            ?: klass.primaryConstructor
                ?.parameters
                ?.firstOrNull { it.name == prop.name }
                ?.findAnnotation<ToolParam>()

    private fun generateProperties(klass: KClass<*>): Map<String, JsonElement> {
        val properties = mutableMapOf<String, JsonElement>()
        klass.memberProperties.forEach { prop ->
            val toolParam = toolParamFor(klass, prop)
            val name =
                prop.findAnnotation<SerialName>()?.value
                    ?: prop.javaField?.getAnnotation(SerialName::class.java)?.value
                    ?: prop.name

            properties[name] =
                buildJsonObject {
                    val propClass = prop.returnType.jvmErasure
                    val type = mapType(propClass)

                    if (toolParam != null && toolParam.description.isNotBlank()) {
                        put("description", toolParam.description)
                    }

                    when (type) {
                        "array" -> {
                            put("type", "array")
                            val itemTypeArg =
                                prop.returnType.arguments
                                    .firstOrNull()
                                    ?.type
                                    ?.jvmErasure
                            if (itemTypeArg != null) {
                                putJsonObject("items") {
                                    val itemSchema = generateSchemaForClass(itemTypeArg)
                                    itemSchema.forEach { (key, value) -> put(key, value) }
                                }
                            }
                        }
                        "string" -> {
                            put("type", "string")
                            if (propClass.java.isEnum) {
                                putJsonArray("enum") {
                                    propClass.java.enumConstants?.forEach { enumValue ->
                                        add(JsonPrimitive(enumValue.toString()))
                                    }
                                }
                            }
                        }
                        "integer" -> put("type", "integer")
                        "number" -> put("type", "number")
                        "boolean" -> put("type", "boolean")
                        "object" -> {
                            val objSchema = generateSchemaForClass(propClass)
                            objSchema.forEach { (key, value) -> put(key, value) }
                        }
                    }
                }
        }
        return properties
    }

    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun generateSchemaForClass(klass: KClass<*>): JsonObject {
        // JsonElement is a sealed type whose concrete classes (e.g. JsonObject) expose
        // properties typed as JsonElement again, which would recurse until stack overflow.
        if (klass == JsonElement::class || klass == JsonObject::class || klass == JsonArray::class) {
            return buildJsonObject { put("type", "object") }
        }
        val type = mapType(klass)
        if (type != "object" && type != "array") {
            return buildJsonObject {
                put("type", type)
                if (klass.java.isEnum) {
                    putJsonArray("enum") {
                        klass.java.enumConstants?.forEach { enumValue ->
                            add(JsonPrimitive(enumValue.toString()))
                        }
                    }
                }
            }
        }
        if (klass.isSealed) {
            val discriminator = klass.findAnnotation<JsonClassDiscriminator>()?.discriminator ?: "type"
            return buildJsonObject {
                putJsonArray("oneOf") {
                    klass.sealedSubclasses.forEach { subclass ->
                        add(
                            buildJsonObject {
                                put("type", "object")
                                val subProperties = generateProperties(subclass).toMutableMap()

                                // Add discriminator
                                val serialName =
                                    subclass.findAnnotation<SerialName>()?.value
                                        ?: subclass.simpleName ?: ""

                                subProperties[discriminator] =
                                    buildJsonObject {
                                        put("type", "string")
                                        putJsonArray("enum") { add(JsonPrimitive(serialName)) }
                                    }

                                put("properties", JsonObject(subProperties))
                                val req = getRequired(subclass).toMutableList()
                                if (!req.contains(discriminator)) req.add(discriminator)
                                putJsonArray("required") {
                                    req.forEach { add(JsonPrimitive(it)) }
                                }
                            },
                        )
                    }
                }
            }
        } else {
            return buildJsonObject {
                put("type", "object")
                put("properties", JsonObject(generateProperties(klass)))
                val req = getRequired(klass)
                if (req.isNotEmpty()) {
                    putJsonArray("required") {
                        req.forEach { add(JsonPrimitive(it)) }
                    }
                }
            }
        }
    }

    private fun getRequired(klass: KClass<*>): List<String> =
        klass.memberProperties
            .filter { prop ->
                val toolParam = toolParamFor(klass, prop)
                toolParam?.required != false
            }.map { prop ->
                prop.findAnnotation<SerialName>()?.value
                    ?: prop.javaField?.getAnnotation(SerialName::class.java)?.value
                    ?: prop.name
            }

    private fun mapType(klass: KClass<*>): String =
        when (klass) {
            String::class -> "string"
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            List::class -> "array"
            else -> if (klass.java.isEnum) "string" else "object"
        }
}
