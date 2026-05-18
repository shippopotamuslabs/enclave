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
    fun `extractLocalToolEnvelope parses DSML web search tool call`() {
        val rawText =
            """
            < | DSML | tool_calls>< | DSML | invoke name="web_search">
            < | DSML | parameter name="query" string="true">digg.com xAI completes training Grok V9</ | DSML | parameter>
            </ | DSML | invoke></ | DSML | tool_calls>
            """.trimIndent()

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)

        assertNotNull(envelope)
        assertEquals("web_search", envelope.toolName)
        assertEquals("""{"query":"digg.com xAI completes training Grok V9"}""", envelope.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope parses DSML web extract urls array`() {
        val rawText =
            """
            I found it.
            < | DSML | tool_calls>< | DSML | invoke name="web_extract">
            < | DSML | parameter name="extract_depth" string="true">advanced</ | DSML | parameter>
            < | DSML | parameter name="format" string="true">markdown</ | DSML | parameter>
            < | DSML | parameter name="urls" string="false">["https://example.com/a","https://example.com/b"]</ | DSML | parameter>
            </ | DSML | invoke></ | DSML | tool_calls>
            """.trimIndent()

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)

        assertNotNull(envelope)
        assertEquals("web_extract", envelope.toolName)
        assertEquals(
            """{"urls":["https://example.com/a","https://example.com/b"],"extract_depth":"advanced","format":"markdown"}""",
            envelope.argumentsJson,
        )
        assertEquals("I found it.\n", envelope.visiblePrefix)
    }

    @Test
    fun `extractLocalToolEnvelope parses DSML append blocks without widening integer values`() {
        val rawText =
            """
            < | DSML | tool_calls>< | DSML | invoke name="append_blocks">
            < | DSML | parameter name="artifactId" string="true">V8B4OgY3SsNCOApkcyVwZtBK7IF3</ | DSML | parameter>
            < | DSML | parameter name="blocks" string="false">[{"type":"heading","level":1,"text":"Research Document"}]</ | DSML | parameter>
            < | DSML | parameter name="isComplete" string="false">false</ | DSML | parameter>
            </ | DSML | invoke></ | DSML | tool_calls>
            """.trimIndent()

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(rawText)

        assertNotNull(envelope)
        assertEquals("append_blocks", envelope.toolName)
        assertEquals(
            """{"artifactId":"V8B4OgY3SsNCOApkcyVwZtBK7IF3","blocks":[{"type":"heading","level":1,"text":"Research Document"}],"isComplete":false}""",
            envelope.argumentsJson,
        )
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

    // -----------------------------------------------------------------------
    // TOOL-PARSE-001 \u2014 TP-01 through TP-20 (TDD Red phase)
    // -----------------------------------------------------------------------

    @Test
    fun `TP-01 extractLocalToolEnvelopes parses Mistral single tool call`() {
        val text = """[TOOL_CALLS] [{"name": "web_search", "arguments": {"query": "kotlin coroutines"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"kotlin coroutines"}""", result[0].argumentsJson)
        assertEquals("", result[0].visiblePrefix)
        assertEquals("", result[0].visibleSuffix)
    }

    @Test
    fun `TP-02 extractLocalToolEnvelopes parses Mistral two-element tool call array`() {
        val text = """[TOOL_CALLS] [{"name": "web_search", "arguments": {"query": "rust async"}}, {"name": "web_extract", "arguments": {"urls": ["https://doc.rust-lang.org"]}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(2, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"rust async"}""", result[0].argumentsJson)
        assertEquals("web_extract", result[1].toolName)
        assert(result[1].argumentsJson.contains(""""urls":["https://doc.rust-lang.org"]"""))
        assert(result[1].argumentsJson.contains(""""extract_depth":"basic""""))
    }

    @Test
    fun `TP-03 extractLocalToolEnvelopes captures visible text before TOOL CALLS sentinel`() {
        val text = "I will search now.\n[TOOL_CALLS] [{\"name\": \"web_search\", \"arguments\": {\"query\": \"android 15\"}}]"
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("I will search now.", result[0].visiblePrefix)
        assertEquals("", result[0].visibleSuffix)
    }

    @Test
    fun `TP-04 extractLocalToolEnvelopes throws when TOOL CALLS sentinel is present but array is absent`() {
        val text = "[TOOL_CALLS] not_json_here"
        val ex = assertFailsWith<IllegalStateException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }
        assertEquals("Malformed tool_call envelope", ex.message)
    }

    @Test
    fun `TP-05 extractLocalToolEnvelopes normalizes parameters key inside tool_call`() {
        val text = """<tool_call>{"name":"web_search","parameters":{"query":"llama 3.2 benchmarks"}}</tool_call>"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"llama 3.2 benchmarks"}""", result[0].argumentsJson)
    }

    @Test
    fun `TP-06 extractLocalToolEnvelopes normalizes parameters key in raw JSON format`() {
        val text = """{"name":"web_search","parameters":{"query":"mistral nemo benchmarks"}}"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"mistral nemo benchmarks"}""", result[0].argumentsJson)
    }

    @Test
    fun `TP-07 extractRequiredUrls succeeds when urls are nested under parameters key`() {
        val argumentsJson = """{"parameters":{"urls":["https://example.com/a","https://example.com/b"]}}"""
        val result = ToolEnvelopeParser.extractRequiredUrls(argumentsJson)

        assertEquals(listOf("https://example.com/a", "https://example.com/b"), result)
    }

    @Test
    fun `TP-08 extractLocalToolEnvelopes normalizes parameters to arguments in Mistral format`() {
        val text = """[TOOL_CALLS] [{"name":"web_search","parameters":{"query":"test"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"test"}""", result[0].argumentsJson)
    }

    @Test
    fun `TP-09 extractLocalToolEnvelopes parses tool_call containing a JSON array with one object`() {
        val text = """<tool_call>[{"name": "web_search", "arguments": {"query": "android compose"}}]</tool_call>"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"android compose"}""", result[0].argumentsJson)
    }

    @Test
    fun `TP-10 extractLocalToolEnvelopes parses tool_call containing a JSON array with two objects`() {
        val text = """<tool_call>[{"name":"web_search","arguments":{"query":"q1"}},{"name":"web_extract","arguments":{"urls":["https://a.com"]}}]</tool_call>"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(2, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"q1"}""", result[0].argumentsJson)
        assertEquals("web_extract", result[1].toolName)
        assert(result[1].argumentsJson.contains(""""urls":["https://a.com"]"""))
    }

    @Test
    fun `TP-11 hasLocalToolContract returns true when system prompt contains DSML and a tool name`() {
        val systemPrompt = "Use DSML format to call tools. web_search is available."
        val result = ToolEnvelopeParser.hasLocalToolContract(systemPrompt)
        assertEquals(true, result)
    }

    @Test
    fun `TP-12 hasLocalToolContract returns true when system prompt contains invoke name= and a tool name`() {
        val systemPrompt = """Call tools using <invoke name="web_search"> syntax. web_search finds information."""
        val result = ToolEnvelopeParser.hasLocalToolContract(systemPrompt)
        assertEquals(true, result)
    }

    @Test
    fun `TP-13 extractLocalToolEnvelopes returns empty list when text has no envelope markers`() {
        val text = "The weather in London is cloudy today."
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        assertEquals(emptyList<ToolEnvelopeParser.LocalToolEnvelope>(), result)
    }

    @Test
    fun `TP-14 extractLocalToolEnvelopes returns one-element list for standard tool_call JSON object`() {
        val text = """<tool_call>{"name":"web_search","arguments":{"query":"pocket crew android"}}</tool_call>"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"pocket crew android"}""", result[0].argumentsJson)
    }

    @Test
    fun `TP-15 extractLocalToolEnvelopes returns two-element list for Mistral two-call array`() {
        val text = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"q1"}},{"name":"web_search","arguments":{"query":"q2"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(2, result.size)
        assertEquals("""{"query":"q1"}""", result[0].argumentsJson)
        assertEquals("""{"query":"q2"}""", result[1].argumentsJson)
    }

    @Test
    fun `TP-16 extractLocalToolEnvelope singular returns first element from Mistral multi-call text`() {
        val text = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"first"}},{"name":"web_search","arguments":{"query":"second"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelope(text)

        assertNotNull(result)
        assertEquals("web_search", result.toolName)
        assertEquals("""{"query":"first"}""", result.argumentsJson)
    }

    @Test
    fun `TP-17 mutation defense extractLocalToolEnvelopes with Mistral format for search_chat_history`() {
        val text = """[TOOL_CALLS] [{"name":"search_chat_history","arguments":{"query":"important meeting"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("search_chat_history", result[0].toolName)
        assert(result[0].argumentsJson.contains(""""queries":["important meeting"]"""))
    }

    @Test
    fun `TP-18 extractLocalToolEnvelopes parses web_extract with parameters key and extracts urls correctly`() {
        val text = """<tool_call>{"name":"web_extract","parameters":{"urls":["https://example.com"]}}</tool_call>"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_extract", result[0].toolName)
        assert(result[0].argumentsJson.contains(""""urls":["https://example.com"]"""))
        assert(result[0].argumentsJson.contains(""""extract_depth":"basic""""))
        assert(result[0].argumentsJson.contains(""""format":"markdown""""))
    }

    @Test
    fun `TP-19 extractLocalToolEnvelopes Mistral multi-call second element has its own tool name and arguments`() {
        val text = """[TOOL_CALLS] [{"name":"web_search","arguments":{"query":"news"}},{"name":"search_chat_history","arguments":{"query":"old chats"}}]"""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(2, result.size)
        assertEquals("search_chat_history", result[1].toolName)
        assert(result[1].argumentsJson.contains(""""queries":["old chats"]"""))
    }

    @Test
    fun `TP-20 extractLocalToolEnvelopes captures visible text around tool_call array payload`() {
        val text = """Let me look that up. <tool_call>[{"name":"web_search","arguments":{"query":"android arch"}}]</tool_call> Done."""
        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("Let me look that up. ", result[0].visiblePrefix)
        assertEquals(" Done.", result[0].visibleSuffix)
    }

    @Test
    fun `robust DSML parses fullwidth delimiter and repaired urls array`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_extract">
            <｜｜DSML｜｜parameter name="urls" string="false">["[https://example.com"]</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_extract", result[0].toolName)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
    }

    @Test
    fun `robust DSML parses three invokes in one tool calls block`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls>
            <｜｜DSML｜｜invoke name="web_extract">
            <｜｜DSML｜｜parameter name="urls" string="false">["https://a.example"]</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke>
            <｜｜DSML｜｜invoke name="web_extract">
            <｜｜DSML｜｜parameter name="urls" string="false">["https://b.example"]</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke>
            <｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">kotlin parser repair</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke>
            </｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(3, result.size)
        assertEquals("web_extract", result[0].toolName)
        assertEquals("web_extract", result[1].toolName)
        assertEquals("web_search", result[2].toolName)
        assertEquals("""{"query":"kotlin parser repair"}""", result[2].argumentsJson)
    }

    @Test
    fun `robust DSML repairs trailing comma in JSON-like parameter`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_extract">
            <｜｜DSML｜｜parameter name="urls" string="false">["[https://example.com",]</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
    }

    @Test
    fun `robust DSML extracts markdown fenced tool calls with prose`() {
        val text =
            """
            I will call the tool now.

            ```xml
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">android api 36</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            ```

            Done.
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("web_search", result[0].toolName)
        assertEquals("""{"query":"android api 36"}""", result[0].argumentsJson)
        assertEquals("I will call the tool now.\n\n", result[0].visiblePrefix)
        assertEquals("\n\nDone.", result[0].visibleSuffix)
    }

    @Test
    fun `robust DSML coerces string true array when schema expects urls array`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_extract">
            <｜｜DSML｜｜parameter name="urls" string="true">["[https://example.com"]</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
    }

    @Test
    fun `robust DSML rejects missing closing parameter tag`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">android
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalStateException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Malformed DSML tool envelope", ex.message)
    }

    @Test
    fun `robust DSML rejects missing closing invoke tag`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">android</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalStateException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Malformed DSML tool envelope", ex.message)
    }

    @Test
    fun `robust DSML rejects empty parameter body for required query`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">   </｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Tool argument 'query' is required", ex.message)
    }

    @Test
    fun `robust DSML rejects unknown tool name`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="unknown_tool">
            <｜｜DSML｜｜parameter name="query" string="true">android</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Unsupported tool: unknown_tool", ex.message)
    }

    @Test
    fun `robust DSML rejects unknown parameter name`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">android</｜｜DSML｜｜parameter>
            <｜｜DSML｜｜parameter name="extra" string="true">ignored</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Unknown parameter 'extra' for tool 'web_search'", ex.message)
    }

    @Test
    fun `robust DSML rejects missing required parameter`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="unused" string="true">android</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Unknown parameter 'unused' for tool 'web_search'", ex.message)
    }

    @Test
    fun `robust DSML rejects duplicate parameter name`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">first</｜｜DSML｜｜parameter>
            <｜｜DSML｜｜parameter name="query" string="true">second</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val ex = assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelopes(text)
        }

        assertEquals("Duplicate parameter 'query' for tool 'web_search'", ex.message)
    }

    @Test
    fun `robust DSML preserves nested brackets escaped quotes and newlines in string parameters`() {
        val text =
            """
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">find [docs] with \"quoted\" phrase
            next line</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals("""{"query":"find [docs] with \"quoted\" phrase\nnext line"}""", result[0].argumentsJson)
    }

    @Test
    fun `robust DSML parses multiple tool call blocks in one response`() {
        val text =
            """
            First block.
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">first query</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            Second block.
            <｜｜DSML｜｜tool_calls><｜｜DSML｜｜invoke name="web_search">
            <｜｜DSML｜｜parameter name="query" string="true">second query</｜｜DSML｜｜parameter>
            </｜｜DSML｜｜invoke></｜｜DSML｜｜tool_calls>
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(2, result.size)
        assertEquals("""{"query":"first query"}""", result[0].argumentsJson)
        assertEquals("""{"query":"second query"}""", result[1].argumentsJson)
    }

    @Test
    fun `robust provider parser repairs Mistral web extract urls array`() {
        val text =
            """
            [TOOL_CALLS] [{"name":"web_extract","arguments":{"urls":["[https://example.com",]}}]
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
    }

    @Test
    fun `robust provider parser repairs tool call web extract urls array`() {
        val text =
            """
            ```json
            <tool_call>{"name":"web_extract","arguments":{"urls":["[https://example.com",]}}</tool_call>
            ```
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
    }

    @Test
    fun `robust provider parser repairs raw JSON web extract urls array with prose`() {
        val text =
            """
            I will fetch that.
            {"name":"web_extract","arguments":{"urls":["[https://example.com",]}}
            Done.
            """.trimIndent()

        val result = ToolEnvelopeParser.extractLocalToolEnvelopes(text)

        assertEquals(1, result.size)
        assertEquals(
            """{"urls":["https://example.com"],"extract_depth":"basic","format":"markdown"}""",
            result[0].argumentsJson,
        )
        assertEquals("I will fetch that.\n", result[0].visiblePrefix)
        assertEquals("\nDone.", result[0].visibleSuffix)
    }
}
