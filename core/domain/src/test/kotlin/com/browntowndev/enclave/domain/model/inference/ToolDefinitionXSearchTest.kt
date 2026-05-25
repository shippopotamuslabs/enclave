package com.browntowndev.enclave.domain.model.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolDefinitionXSearchTest {
    @Test
    fun `X_SEARCH is registered in ALL_TOOLS`() {
        val xSearch = ToolDefinition.ALL_TOOLS.find { it.name == "search_x_recent" }
        assertTrue(xSearch != null, "X_SEARCH should be registered in ALL_TOOLS")
        assertEquals(ToolDefinition.X_SEARCH, xSearch)
    }

    @Test
    fun `X_SEARCH schema is well-formed`() {
        val schema = Json.parseToJsonElement(ToolDefinition.X_SEARCH.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("query"), "Schema should have 'query' property")
        assertEquals("string", properties["query"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val required = schema["required"]?.jsonArray
        assertTrue(required == null || required.none { it.jsonPrimitive.content == "query" }, "Query should be an optional property")
    }

    @Test
    fun `X_SEARCH parameters encode and decode round-trip`() {
        val params = XSearchParams(query = "test query")
        val json = ToolDefinition.X_SEARCH.encodeArguments(params)
        val decoded: XSearchParams = ToolDefinition.X_SEARCH.decodeArguments(json)

        assertEquals(params, decoded)
    }
}
