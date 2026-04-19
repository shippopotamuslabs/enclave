package com.browntowndev.pocketcrew.domain.model.inference

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
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
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
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
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
        val properties = mutableMapOf<String, JsonElement>()
        val required = mutableListOf<String>()

        klass.memberProperties.forEach { prop ->
            val toolParam = prop.findAnnotation<ToolParam>() ?: prop.javaField?.getAnnotation(ToolParam::class.java)
            val name = prop.name
            
            if (toolParam?.required != false) {
                required.add(name)
            }

            properties[name] = buildJsonObject {
                val type = mapType(prop)
                put("type", type)
                
                if (toolParam != null && toolParam.description.isNotBlank()) {
                    put("description", toolParam.description)
                }

                if (type == "array") {
                    val itemType = mapArrayType(prop)
                    putJsonObject("items") {
                        put("type", itemType)
                    }
                }

                // Handle Enums
                val propClass = prop.returnType.jvmErasure
                if (propClass.java.isEnum) {
                    putJsonArray("enum") {
                        propClass.java.enumConstants?.forEach { enumValue ->
                            add(JsonPrimitive(enumValue.toString()))
                        }
                    }
                }
            }
        }

        return ToolSchema(JsonObject(properties), required)
    }

    private fun mapType(prop: KProperty1<*, *>): String {
        return when (prop.returnType.jvmErasure) {
            String::class -> "string"
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            List::class -> "array"
            else -> if (prop.returnType.jvmErasure.java.isEnum) "string" else "object"
        }
    }

    private fun mapArrayType(prop: KProperty1<*, *>): String {
        val typeArg = prop.returnType.arguments.firstOrNull()?.type?.jvmErasure
        return when (typeArg) {
            String::class -> "string"
            Int::class, Long::class -> "integer"
            Float::class, Double::class -> "number"
            Boolean::class -> "boolean"
            else -> "string"
        }
    }
}
