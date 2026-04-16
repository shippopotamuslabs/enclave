package com.browntowndev.pocketcrew.domain.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

class ToolEnvelopeParserExtractTest {

    @Test
    fun `requireSupportedTool accepts tavily_extract`() {
        // Should not throw
        ToolEnvelopeParser.requireSupportedTool("tavily_extract")
    }

    @Test
    fun `buildExtractArgumentsJson serializes urls and defaults`() {
        val json = ToolEnvelopeParser.buildExtractArgumentsJson(
            urls = listOf("https://example.com"),
            extractDepth = null,
            format = null,
        )

        assertTrue(json.contains("\"urls\""), "JSON should contain 'urls' key")
        assertTrue(json.contains("https://example.com"), "JSON should contain the URL")
        assertTrue(json.contains("\"extract_depth\":\"basic\""), "extract_depth should default to 'basic'")
        assertTrue(json.contains("\"format\":\"markdown\""), "format should default to 'markdown'")
    }

    @Test
    fun `buildExtractArgumentsJson serializes custom extract_depth and format`() {
        val json = ToolEnvelopeParser.buildExtractArgumentsJson(
            urls = listOf("https://example.com", "https://example.org"),
            extractDepth = "advanced",
            format = "text",
        )

        assertTrue(json.contains("https://example.com"), "JSON should contain first URL")
        assertTrue(json.contains("https://example.org"), "JSON should contain second URL")
        assertTrue(json.contains("\"extract_depth\":\"advanced\""), "extract_depth should be 'advanced'")
        assertTrue(json.contains("\"format\":\"text\""), "format should be 'text'")
    }

    @Test
    fun `extractRequiredUrls parses urls array from argumentsJson`() {
        val urls = ToolEnvelopeParser.extractRequiredUrls(
            "{\"urls\":[\"https://a.com\",\"https://b.com\"]}"
        )

        assertEquals(listOf("https://a.com", "https://b.com"), urls)
    }

    @Test
    fun `extractRequiredUrls throws on missing urls`() {
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractRequiredUrls("{}")
        }
    }

    @Test
    fun `extractRequiredUrls throws on empty urls`() {
        assertFailsWith<IllegalArgumentException> {
            ToolEnvelopeParser.extractRequiredUrls("{\"urls\":[]}")
        }
    }
}