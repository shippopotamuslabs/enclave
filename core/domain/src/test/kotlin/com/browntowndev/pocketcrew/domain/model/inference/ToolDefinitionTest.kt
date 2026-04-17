package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolDefinitionTest {

    @Test
    fun `tavily_extract constant has correct name and description`() {
        assertEquals("tavily_extract", ToolDefinition.TAVILY_EXTRACT.name)
        assertTrue(
            ToolDefinition.TAVILY_EXTRACT.description.contains("extract", ignoreCase = true),
            "Description should mention extracting content from URLs"
        )
        assertTrue(
            ToolDefinition.TAVILY_EXTRACT.description.contains("URL", ignoreCase = true),
            "Description should mention URLs"
        )
    }

    @Test
    fun `tavily_extract parameters schema requires urls array`() {
        val schema = Json.parseToJsonElement(ToolDefinition.TAVILY_EXTRACT.parametersJson).jsonObject

        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("urls"), "Schema should have 'urls' property")

        val urlsProp = properties["urls"]!!.jsonObject
        assertEquals("array", urlsProp["type"]!!.jsonPrimitive.content, "urls property should be type array")

        val required = schema["required"]!!.jsonArray
        val requiredList = required.map { it.jsonPrimitive.content }
        assertTrue(requiredList.contains("urls"), "urls should be in required list")
    }

    @Test
    fun `tavily_extract parameters schema has required extract_depth and format properties`() {
        val schema = Json.parseToJsonElement(ToolDefinition.TAVILY_EXTRACT.parametersJson).jsonObject

        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("extract_depth"), "Schema should have 'extract_depth' property")
        assertTrue(properties.containsKey("format"), "Schema should have 'format' property")

        val required = schema["required"]!!.jsonArray
        val requiredList = required.map { it.jsonPrimitive.content }
        assertTrue(requiredList.contains("extract_depth"), "extract_depth should be required for strict mode compatibility")
        assertTrue(requiredList.contains("format"), "format should be required for strict mode compatibility")
    }

    @Test
    fun `tavily_extract format enum includes markdown and text`() {
        val schema = Json.parseToJsonElement(ToolDefinition.TAVILY_EXTRACT.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        val formatProp = properties["format"]!!.jsonObject
        val enum = formatProp["enum"]!!.jsonArray

        val enumValues = enum.map { it.jsonPrimitive.content }
        assertTrue(enumValues.contains("markdown"), "format enum should include 'markdown'")
        assertTrue(enumValues.contains("text"), "format enum should include 'text'")
    }

    @Test
    fun `tavily_extract extract_depth enum includes basic and advanced`() {
        val schema = Json.parseToJsonElement(ToolDefinition.TAVILY_EXTRACT.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        val extractDepthProp = properties["extract_depth"]!!.jsonObject
        val enum = extractDepthProp["enum"]!!.jsonArray

        val enumValues = enum.map { it.jsonPrimitive.content }
        assertTrue(enumValues.contains("basic"), "extract_depth enum should include 'basic'")
        assertTrue(enumValues.contains("advanced"), "extract_depth enum should include 'advanced'")
    }

    @Test
    fun `search_chat_history constant has correct name and description`() {
        assertEquals("search_chat_history", ToolDefinition.SEARCH_CHAT_HISTORY.name)
        assertTrue(
            ToolDefinition.SEARCH_CHAT_HISTORY.description.contains("past", ignoreCase = true),
            "Description should mention past conversations"
        )
    }

    @Test
    fun `search_chat_history parameters schema requires queries array`() {
        val schema = Json.parseToJsonElement(ToolDefinition.SEARCH_CHAT_HISTORY.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("queries"), "Schema should have 'queries' property")

        val queriesProp = properties["queries"]!!.jsonObject
        assertEquals("array", queriesProp["type"]!!.jsonPrimitive.content, "queries property should be type array")

        val required = schema["required"]!!.jsonArray
        val requiredList = required.map { it.jsonPrimitive.content }
        assertTrue(requiredList.contains("queries"), "queries should be in required list")
    }

    @Test
    fun `search_chat constant has correct name and description`() {
        assertEquals("search_chat", ToolDefinition.SEARCH_CHAT.name)
        assertTrue(
            ToolDefinition.SEARCH_CHAT.description.contains("chat", ignoreCase = true),
            "Description should mention chat"
        )
    }

    @Test
    fun `search_chat parameters schema requires chat_id and query`() {
        val schema = Json.parseToJsonElement(ToolDefinition.SEARCH_CHAT.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("chat_id"), "Schema should have 'chat_id' property")
        assertTrue(properties.containsKey("query"), "Schema should have 'query' property")

        val required = schema["required"]!!.jsonArray
        val requiredList = required.map { it.jsonPrimitive.content }
        assertTrue(requiredList.contains("chat_id"), "chat_id should be in required list")
        assertTrue(requiredList.contains("query"), "query should be in required list")
    }

    @Test
    fun `get_message_context constant has correct name and description`() {
        assertEquals("get_message_context", ToolDefinition.GET_MESSAGE_CONTEXT.name)
        assertTrue(
            ToolDefinition.GET_MESSAGE_CONTEXT.description.contains("context", ignoreCase = true),
            "Description should mention context"
        )
    }

    @Test
    fun `get_message_context parameters schema requires message_id`() {
        val schema = Json.parseToJsonElement(ToolDefinition.GET_MESSAGE_CONTEXT.parametersJson).jsonObject
        val properties = schema["properties"]!!.jsonObject
        assertTrue(properties.containsKey("message_id"), "Schema should have 'message_id' property")
        assertTrue(properties.containsKey("before"), "Schema should have 'before' property")
        assertTrue(properties.containsKey("after"), "Schema should have 'after' property")

        val required = schema["required"]!!.jsonArray
        val requiredList = required.map { it.jsonPrimitive.content }
        assertTrue(requiredList.contains("message_id"), "message_id should be in required list")
    }

    @Test
    fun `search_chat_history round-trip serialization`() {
        val params = SearchChatHistoryParams(queries = listOf("android", "kotlin"))
        val json = ToolDefinition.SEARCH_CHAT_HISTORY.encodeArguments(params)
        val decoded: SearchChatHistoryParams = ToolDefinition.SEARCH_CHAT_HISTORY.decodeArguments(json)

        assertEquals(params, decoded)
    }

    @Test
    fun `tavily_extract round-trip serialization`() {
        val params = TavilyExtractParams(
            urls = listOf("https://example.com"),
            extract_depth = ExtractDepth.advanced,
            format = ExtractFormat.markdown
        )
        val json = ToolDefinition.TAVILY_EXTRACT.encodeArguments(params)
        val decoded: TavilyExtractParams = ToolDefinition.TAVILY_EXTRACT.decodeArguments(json)

        assertEquals(params, decoded)
    }
}