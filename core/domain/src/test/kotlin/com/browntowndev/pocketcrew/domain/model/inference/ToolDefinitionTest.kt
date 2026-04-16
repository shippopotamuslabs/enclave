package com.browntowndev.pocketcrew.domain.model.inference

import org.json.JSONObject
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
        val schema = JSONObject(ToolDefinition.TAVILY_EXTRACT.parametersJson)

        val properties = schema.getJSONObject("properties")
        assertTrue(properties.has("urls"), "Schema should have 'urls' property")

        val urlsProp = properties.getJSONObject("urls")
        assertEquals("array", urlsProp.getString("type"), "urls property should be type array")

        val required = schema.getJSONArray("required")
        val requiredList = (0 until required.length()).map { i -> required.getString(i) }
        assertTrue(requiredList.contains("urls"), "urls should be in required list")
    }

    @Test
    fun `tavily_extract parameters schema has required extract_depth and format properties`() {
        val schema = JSONObject(ToolDefinition.TAVILY_EXTRACT.parametersJson)

        val properties = schema.getJSONObject("properties")
        assertTrue(properties.has("extract_depth"), "Schema should have 'extract_depth' property")
        assertTrue(properties.has("format"), "Schema should have 'format' property")

        val required = schema.getJSONArray("required")
        val requiredList = (0 until required.length()).map { i -> required.getString(i) }
        assertTrue(requiredList.contains("extract_depth"), "extract_depth should be required for strict mode compatibility")
        assertTrue(requiredList.contains("format"), "format should be required for strict mode compatibility")
    }

    @Test
    fun `tavily_extract format enum includes markdown and text`() {
        val schema = JSONObject(ToolDefinition.TAVILY_EXTRACT.parametersJson)
        val properties = schema.getJSONObject("properties")
        val formatProp = properties.getJSONObject("format")
        val enum = formatProp.getJSONArray("enum")

        val enumValues = (0 until enum.length()).map { i -> enum.getString(i) }
        assertTrue(enumValues.contains("markdown"), "format enum should include 'markdown'")
        assertTrue(enumValues.contains("text"), "format enum should include 'text'")
    }

    @Test
    fun `tavily_extract extract_depth enum includes basic and advanced`() {
        val schema = JSONObject(ToolDefinition.TAVILY_EXTRACT.parametersJson)
        val properties = schema.getJSONObject("properties")
        val extractDepthProp = properties.getJSONObject("extract_depth")
        val enum = extractDepthProp.getJSONArray("enum")

        val enumValues = (0 until enum.length()).map { i -> enum.getString(i) }
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
    fun `search_chat_history parameters schema requires query`() {
        val schema = JSONObject(ToolDefinition.SEARCH_CHAT_HISTORY.parametersJson)
        val properties = schema.getJSONObject("properties")
        assertTrue(properties.has("query"), "Schema should have 'query' property")

        val required = schema.getJSONArray("required")
        val requiredList = (0 until required.length()).map { i -> required.getString(i) }
        assertTrue(requiredList.contains("query"), "query should be in required list")
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
        val schema = JSONObject(ToolDefinition.SEARCH_CHAT.parametersJson)
        val properties = schema.getJSONObject("properties")
        assertTrue(properties.has("chat_id"), "Schema should have 'chat_id' property")
        assertTrue(properties.has("query"), "Schema should have 'query' property")

        val required = schema.getJSONArray("required")
        val requiredList = (0 until required.length()).map { i -> required.getString(i) }
        assertTrue(requiredList.contains("chat_id"), "chat_id should be in required list")
        assertTrue(requiredList.contains("query"), "query should be in required list")
    }
}