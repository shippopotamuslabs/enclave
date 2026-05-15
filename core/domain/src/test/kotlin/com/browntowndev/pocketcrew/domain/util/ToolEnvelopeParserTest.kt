package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class ToolEnvelopeParserTest {

    @Test
    fun `extractLocalToolEnvelope parses GLM style tool calls`() {
        val rawText = """<tool_call> web_search <arg_key>query</arg_key> <arg_value>"weather in London"</arg_value> </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"weather in London"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses CDATA wrapped tool calls`() {
        val rawText = """<![CDATA[<tool> {"name": "web_search", "arguments": {"query": "weather in London"}} </tool>]]>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"weather in London"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses wrapped tool calls`() {
        val rawText = """<tool_call> {"name": "web_search", "arguments": {"query": "weather in London"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"weather in London"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses raw tool calls`() {
        val rawText = """{"name": "web_search", "arguments": {"query": "weather in London"}}"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"weather in London"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope handles mixed text with tool call`() {
        val rawText = """Searching now... <tool_call> {"name": "web_search", "arguments": {"query": "weather in London"}} </tool_call> Please wait."""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        
        assertNotNull(envelope)
        assertEquals("Searching now... ", envelope.visiblePrefix)
        assertEquals(" Please wait.", envelope.visibleSuffix)
    }

    @Test
    fun `extractLocalToolEnvelope throws on malformed envelope`() {
        val rawText = """<tool_call> {"name": "web_search"} </tool_call>""" // Missing arguments
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        }
    }

    @Test
    fun `buildArgumentsJson from map robustly encodes arguments`() {
        val map = mapOf("query" to "weather in London")
        val json = ToolEnvelopeParser.buildArgumentsJson("web_search", map)
        assertEquals("""{"query":"weather in London"}""", json)
    }

    @Test
    fun `buildArgumentsJson handles nested maps and lists`() {
        val map = mapOf(
            "urls" to listOf("http://example.com"),
            "extract_depth" to "basic",
            "format" to "markdown"
        )
        val json = ToolEnvelopeParser.buildArgumentsJson("web_extract", map)
        // WebExtractParams uses enums, so we test if it encodes correctly
        assertEquals("""{"urls":["http://example.com"],"extract_depth":"basic","format":"markdown"}""", json)
    }

    @Test
    fun `extractRequiredUrls parses JSON array`() {
        val json = """{"urls": ["url1", "url2"]}"""
        val urls = ToolEnvelopeParser.extractRequiredUrls(json)
        assertEquals(listOf("url1", "url2"), urls)
    }

    @Test
    fun `extractRequiredUrls fails on empty array`() {
        val json = """{"urls": []}"""
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractRequiredUrls(json)
        }
    }

    @Test
    fun `hasLocalToolContract returns true when tool names and tags are present`() {
        val systemPrompt = "You have access to web_search. Use <tool_call> tag."
        assertNotNull(ToolEnvelopeParser.hasLocalToolContract(systemPrompt))
        assertEquals(true, ToolEnvelopeParser.hasLocalToolContract(systemPrompt))
    }

    @Test
    fun `extractLocalToolEnvelope parses web_extract tool`() {
        val rawText = """<tool_call> {"name": "web_extract", "arguments": {"urls": ["http://test.com"]}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("web_extract", envelope.toolName)
        assertEquals("""{"urls":["http://test.com"],"extract_depth":"basic","format":"markdown"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses attached_image_inspect tool`() {
        val rawText = """<tool_call> {"name": "attached_image_inspect", "arguments": {"question": "what is this?"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("attached_image_inspect", envelope.toolName)
        assertEquals("""{"question":"what is this?"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses search_chat_history tool`() {
        val rawText = """<tool_call> {"name": "search_chat_history", "arguments": {"query": "find my notes"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("search_chat_history", envelope.toolName)
        assertEquals("""{"queries":["find my notes"]}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses search_chat tool`() {
        val rawText = """<tool_call> {"name": "search_chat", "arguments": {"chat_id": "123", "query": "hello"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("search_chat", envelope.toolName)
        assertEquals("""{"chat_id":"123","query":"hello"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses get_message_context tool`() {
        val rawText = """<tool_call> {"name": "get_message_context", "arguments": {"message_id": "msg1"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("get_message_context", envelope.toolName)
        assertEquals("""{"message_id":"msg1","before":5,"after":5}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses search_x_recent tool`() {
        val rawText = """<tool_call> {"name": "search_x_recent", "arguments": {"query": "trending"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("search_x_recent", envelope.toolName)
        assertEquals("""{"has_geo":false,"has_media":false,"has_images":false,"has_videos":false,"has_links":false,"language":"en","sort_order":"relevancy","query":"trending"}""", envelope.argumentsJson)
    }

    @Test
    fun `buildArgumentsJson robustly handles unknown tool by falling back to raw JSON`() {
        val map = mapOf("custom" to "value")
        val json = ToolEnvelopeParser.buildArgumentsJson("unknown_tool", map)
        assertEquals("""{"custom":"value"}""", json)
    }

    @Test
    fun `stubResultJson returns correctly escaped JSON`() {
        val query = "test \"quote\""
        val json = ToolEnvelopeParser.stubResultJson(query)
        assert(json.contains("test \\\"quote\\\""))
    }

    @Test
    fun `requireSupportedTool throws for unsupported tool`() {
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.requireSupportedTool("invalid")
        }
    }

    @Test
    fun `requireSupportedTool succeeds for supported tool`() {
        ToolEnvelopeParser.requireSupportedTool("web_search")
    }

    // --- FEAT-llm-tool-self-heal: envelope normalization (RED until parser heals common local-model corruption) ---

    @Test
    fun `extractLocalToolEnvelope self heals single quoted JSON inside tool_call`() {
        val rawText =
            """<tool_call> {'name': 'web_search', 'arguments': {'query': 'weather in London'}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"weather in London"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope self heals smart quotes in JSON string values`() {
        val rawText =
            """<tool_call> {"name": "web_search", "arguments": {"query": "cost of \u201cbread\u201d in Paris"}} </tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)
        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"cost of \"bread\" in Paris"}""", envelope.argumentsJson)
    }
}
