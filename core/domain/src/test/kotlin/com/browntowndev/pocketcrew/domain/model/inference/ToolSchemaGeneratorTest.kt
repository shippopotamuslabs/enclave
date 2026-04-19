package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolSchemaGeneratorTest {

    @Serializable
    enum class TestEnum {
        VALUE_A, VALUE_B
    }

    @Serializable
    data class ComplexParams(
        @ToolParam(description = "A string field")
        val stringField: String,
        @ToolParam(description = "An int field", required = false)
        val intField: Int = 42,
        @ToolParam(description = "A list of strings")
        val listField: List<String>,
        @ToolParam(description = "An enum field")
        val enumField: TestEnum
    ) : ToolParameters

    @Test
    fun `generateSchema builds correct JSON Schema for complex data class`() {
        val schema = ToolSchemaGenerator.generateSchema(ComplexParams::class).toJsonObject()

        assertEquals("object", schema["type"]?.jsonPrimitive?.content)
        val properties = schema["properties"]?.jsonObject!!

        // String field
        val stringProp = properties["stringField"]?.jsonObject!!
        assertEquals("string", stringProp["type"]?.jsonPrimitive?.content)
        assertEquals("A string field", stringProp["description"]?.jsonPrimitive?.content)

        // Int field
        val intProp = properties["intField"]?.jsonObject!!
        assertEquals("integer", intProp["type"]?.jsonPrimitive?.content)
        assertEquals("An int field", intProp["description"]?.jsonPrimitive?.content)

        // List field
        val listProp = properties["listField"]?.jsonObject!!
        assertEquals("array", listProp["type"]?.jsonPrimitive?.content)
        assertEquals("string", listProp["items"]?.jsonObject?.get("type")?.jsonPrimitive?.content)

        // Enum field
        val enumProp = properties["enumField"]?.jsonObject!!
        assertEquals("string", enumProp["type"]?.jsonPrimitive?.content)
        val enumValues = enumProp["enum"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertTrue(enumValues?.contains("VALUE_A") == true)
        assertTrue(enumValues?.contains("VALUE_B") == true)

        // Required fields
        val required = schema["required"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertTrue(required?.contains("stringField") == true)
        assertTrue(required?.contains("listField") == true)
        assertTrue(required?.contains("enumField") == true)
        assertFalse(required?.contains("intField") == true, "Optional field should not be in required list")
    }

    @Serializable
    data class SimpleParams(val query: String) : ToolParameters

    @Test
    fun `generateSchema handles data class without annotations`() {
        val schema = ToolSchemaGenerator.generateSchema(SimpleParams::class).toJsonObject()
        val properties = schema["properties"]?.jsonObject!!
        
        assertTrue(properties.containsKey("query"))
        assertEquals("string", properties["query"]?.jsonObject?.get("type")?.jsonPrimitive?.content)
        assertFalse(properties["query"]?.jsonObject?.containsKey("description") == true)
    }
}
