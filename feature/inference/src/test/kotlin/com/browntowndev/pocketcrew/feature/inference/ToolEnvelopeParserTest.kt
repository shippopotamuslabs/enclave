package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ToolEnvelopeParserTest {

    @Test
    fun `extractRequiredQuestion handles escaped JSON and robust parsing`() {
        val argumentsJson = """{"question":"What's the status? \"Ready\"."}"""
        val question = ToolEnvelopeParser.extractRequiredQuestion(argumentsJson)
        assertEquals("What's the status? \"Ready\".", question)
    }

    @Test
    fun `extractRequiredQuestion falls back to regex for malformed JSON`() {
        val argumentsJson = """{"question":"Partial JSON..."""
        // Regex should still pick it up if it matches the pattern
        val question = ToolEnvelopeParser.extractRequiredQuestion(argumentsJson)
        assertEquals("Partial JSON...", question)
    }

    // --- Generic local parser accepts valid search envelope ---

    @Test
    fun `generic local parser accepts valid search envelope`() {
        val text = """Some prefix,{"name":"tavily_web_search","arguments":{"query":"android news"}},"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("tavily_web_search", envelope!!.toolName)
        assertTrue(envelope.argumentsJson.contains("query"))
    }

    // --- Generic local parser accepts valid image-inspect envelope ---

    @Test
    fun `generic local parser accepts valid image-inspect envelope`() {
        val text = """Some prefix,{"name":"attached_image_inspect","arguments":{"question":"What color is the bicycle?"}},"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("attached_image_inspect", envelope!!.toolName)
        assertTrue(envelope.argumentsJson.contains("question"))
    }

    // --- Missing query fails for search tool ---

    @Test
    fun `missing query fails for search tool`() {
        val text = """{"name":"tavily_web_search","arguments":{}},"""
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelope(text)
        }
    }

    // --- Missing question fails for image-inspect tool ---

    @Test
    fun `missing question fails for image-inspect tool`() {
        val text = """{"name":"attached_image_inspect","arguments":{}},"""
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelope(text)
        }
    }

    // --- Malformed envelope still produces the same class of error ---

    @Test
    fun `malformed envelope still produces the same class of error`() {
        val text = """Some prefix,{"name":,"""
        assertFailsWith<IllegalStateException> {
            ToolEnvelopeParser.extractLocalToolEnvelope(text)
        }
    }

    // --- Local tool contract string contains the search example by default ---

    @Test
    fun `local tool contract string contains the search example by default`() {
        val contract = ToolPromptComposer.localToolContract(includeImageInspectTool = false)
        assertTrue(contract.contains("tavily_web_search"),
            "Contract should mention tavily_web_search")
        assertFalse(contract.contains("attached_image_inspect"),
            "Contract should not mention attached_image_inspect unless requested")
        assertTrue(contract.contains("query"),
            "Contract should show search query parameter")
    }

    @Test
    fun `local tool contract can include the image inspect example when requested`() {
        val contract = ToolPromptComposer.localToolContract(includeImageInspectTool = true)
        assertTrue(contract.contains("attached_image_inspect"),
            "Contract should mention attached_image_inspect")
        assertTrue(contract.contains("question"),
            "Contract should show image inspect question parameter")
    }

    // --- hasLocalToolContract detects both tools ---

    @Test
    fun `hasLocalToolContract detects both tools`() {
        val prompt = ToolPromptComposer().compose("Be helpful.", includeImageInspectTool = true)
        assertTrue(ToolEnvelopeParser.hasLocalToolContract(prompt))
    }

    // --- extractLocalToolEnvelope returns null when no envelope marker ---

    @Test
    fun `extractLocalToolEnvelope returns null when no envelope marker`() {
        val text = "Just a regular response with no tool call."
        assertNull(ToolEnvelopeParser.extractLocalToolEnvelope(text))
    }

    // --- visiblePrefix and visibleSuffix preserved for both tool types ---

    @Test
    fun `visiblePrefix and visibleSuffix preserved for image-inspect envelope`() {
        val text = """Before text,{"name":"attached_image_inspect","arguments":{"question":"What is this?"}},After text"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("Before text", envelope!!.visiblePrefix)
        assertEquals("After text", envelope.visibleSuffix)
    }

    @Test
    fun `visiblePrefix and visibleSuffix preserved for search envelope`() {
        val text = """Prefix,{"name":"tavily_web_search","arguments":{"query":"test query"}},Suffix"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("Prefix", envelope!!.visiblePrefix)
        assertEquals("Suffix", envelope.visibleSuffix)
    }

    @Test
    fun `extractLocalToolEnvelope preserves escaped quotes in search query`() {
        val text = """Prefix,{"name":"tavily_web_search","arguments":{"query":"android \"agent\" news"}},Suffix"""

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)

        assertNotNull(envelope)
        assertEquals("""{"query":"android \"agent\" news"}""", envelope!!.argumentsJson)
    }

    @Test
    fun `extractLocalToolEnvelope preserves escaped quotes in image inspect question`() {
        val text = """Prefix,{"name":"attached_image_inspect","arguments":{"question":"What does the sign say: \"STOP\"?"}},Suffix"""

        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)

        assertNotNull(envelope)
        assertEquals(
            """{"question":"What does the sign say: \"STOP\"?"}""",
            envelope!!.argumentsJson,
        )
    }

    @Test
    fun `extractLocalToolEnvelope parses CDATA tool envelope`() {
        val text = """Some prefix,<![CDATA[<tool>{"name":"tavily_web_search","arguments":{"query":"android news"}}</tool>]]>,Some suffix"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("tavily_web_search", envelope!!.toolName)
        assertEquals("""{"query":"android news"}""", envelope.argumentsJson)
        assertEquals("Some prefix,", envelope.visiblePrefix)
        assertEquals(",Some suffix", envelope.visibleSuffix)
    }

    @Test
    fun `extractLocalToolEnvelope parses original wrapped tool envelope`() {
        val text = """Some prefix,<tool_call>{"name":"tavily_web_search","arguments":{"query":"android news"}}</tool_call>,Some suffix"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("tavily_web_search", envelope!!.toolName)
        assertEquals("""{"query":"android news"}""", envelope.argumentsJson)
        assertEquals("Some prefix,", envelope.visiblePrefix)
        assertEquals(",Some suffix", envelope.visibleSuffix)
    }

    @Test
    fun `buildArgumentsJson supports image inspect argument maps`() {
        val json = ToolEnvelopeParser.buildArgumentsJson(
            mapOf("question" to "What is shown in this image?")
        )

        assertEquals("""{"question":"What is shown in this image?"}""", json)
    }
}
