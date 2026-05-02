package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure

/**
 * Marker interface for tool parameters.
 */
interface ToolParameters

/**
 * Annotation to provide descriptions for tool parameters.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class ToolParam(val description: String = "", val required: Boolean = true)

data class ToolSchema(
    val properties: JsonObject,
    val required: List<String>
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", properties)
        if (required.isNotEmpty()) {
            putJsonArray("required") {
                required.forEach { add(JsonPrimitive(it)) }
            }
        }
    }

    override fun toString(): String = toJsonObject().toString()
}

object ToolSchemaGenerator {
    fun <T : ToolParameters> generateSchema(klass: KClass<T>): ToolSchema {
        return ToolSchema(JsonObject(generateProperties(klass)), getRequired(klass))
    }

    private fun generateProperties(klass: KClass<*>): Map<String, JsonElement> {
        val properties = mutableMapOf<String, JsonElement>()
        klass.memberProperties.forEach { prop ->
            val toolParam = prop.findAnnotation<ToolParam>() ?: prop.javaField?.getAnnotation(ToolParam::class.java)
            val name = prop.findAnnotation<SerialName>()?.value 
                ?: prop.javaField?.getAnnotation(SerialName::class.java)?.value
                ?: prop.name
            
            properties[name] = buildJsonObject {
                val propClass = prop.returnType.jvmErasure
                val type = mapType(propClass)
                
                if (toolParam != null && toolParam.description.isNotBlank()) {
                    put("description", toolParam.description)
                }

                when (type) {
                    "array" -> {
                        put("type", "array")
                        val itemTypeArg = prop.returnType.arguments.firstOrNull()?.type?.jvmErasure
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
        if (klass.isSealed) {
            val discriminator = klass.findAnnotation<JsonClassDiscriminator>()?.discriminator ?: "type"
            return buildJsonObject {
                putJsonArray("oneOf") {
                    klass.sealedSubclasses.forEach { subclass ->
                        add(buildJsonObject {
                            put("type", "object")
                            val subProperties = generateProperties(subclass).toMutableMap()
                            
                            // Add discriminator
                            val serialName = subclass.findAnnotation<SerialName>()?.value 
                                ?: subclass.simpleName ?: ""
                            
                            subProperties[discriminator] = buildJsonObject {
                                put("type", "string")
                                putJsonArray("enum") { add(JsonPrimitive(serialName)) }
                            }
                            
                            put("properties", JsonObject(subProperties))
                            val req = getRequired(subclass).toMutableList()
                            if (!req.contains(discriminator)) req.add(discriminator)
                            putJsonArray("required") {
                                req.forEach { add(JsonPrimitive(it)) }
                            }
                        })
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

    private fun getRequired(klass: KClass<*>): List<String> {
        return klass.memberProperties.filter { prop ->
            val toolParam = prop.findAnnotation<ToolParam>() ?: prop.javaField?.getAnnotation(ToolParam::class.java)
            toolParam?.required != false
        }.map { prop ->
            prop.findAnnotation<SerialName>()?.value 
                ?: prop.javaField?.getAnnotation(SerialName::class.java)?.value
                ?: prop.name
        }
    }

    private fun mapType(klass: KClass<*>): String {
        return when (klass) {
            String::class -> "string"
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            List::class -> "array"
            else -> if (klass.java.isEnum) "string" else "object"
        }
    }
}
