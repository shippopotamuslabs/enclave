package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ToolEnvelopeParserXSearchTest {

    @Test
    fun `requireSupportedTool accepts search_x_recent`() {
        ToolEnvelopeParser.requireSupportedTool(ToolDefinition.X_SEARCH.name)
    }

    @Test
    fun `extractLocalToolEnvelope parses search_x_recent raw envelope`() {
        val text = """Prefix,{"name":"search_x_recent","arguments":{"query":"AI news"}},Suffix"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("search_x_recent", envelope!!.toolName)
        
        // Assert that the JSON contains the query we provided and the expected defaults
        assertTrue(envelope.argumentsJson.contains(""""query":"AI news""""))
        assertTrue(envelope.argumentsJson.contains(""""language":"en""""))
        assertTrue(envelope.argumentsJson.contains(""""sort_order":"relevancy""""))
        
        assertEquals("Prefix", envelope.visiblePrefix)
        assertEquals("Suffix", envelope.visibleSuffix)
    }

    @Test
    fun `extractLocalToolEnvelope parses search_x_recent wrapped envelope`() {
        val text = """<tool_call>{"name":"search_x_recent","arguments":{"query":"AI trends"}}</tool_call>"""
        val envelope = ToolEnvelopeParser.extractLocalToolEnvelope(text)
        assertNotNull(envelope)
        assertEquals("search_x_recent", envelope!!.toolName)
        
        assertTrue(envelope.argumentsJson.contains(""""query":"AI trends""""))
        assertTrue(envelope.argumentsJson.contains(""""language":"en""""))
        assertTrue(envelope.argumentsJson.contains(""""sort_order":"relevancy""""))
    }

    @Test
    fun `missing query fails for search_x_recent tool`() {
        val text = """{"name":"search_x_recent","arguments":{}},"""
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractLocalToolEnvelope(text)
        }
    }
}
