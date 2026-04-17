package com.browntowndev.pocketcrew.domain.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeToolResultFormatterTest {

    @Test
    fun `truncateToolResult should truncate long content in search results`() {
        val longContent = "A".repeat(8000)
        val resultJson = """
            {
                "results": [
                    {
                        "title": "Search Result",
                        "url": "https://example.com",
                        "content": "$longContent",
                        "score": 0.99
                    }
                ]
            }
        """.trimIndent()

        // 1000 tokens context, 800 tokens used, 100 buffer = 100 available = 400 chars
        val truncated = NativeToolResultFormatter.truncateToolResult(
            resultJson = resultJson,
            contextWindowTokens = 1000,
            estimatedUsedTokens = 800,
            bufferTokens = 100
        )
        
        val payload = Json.parseToJsonElement(truncated).jsonObject
        val item = payload["results"]!!.jsonArray[0].jsonObject
        val content = item["content"]!!.jsonPrimitive.content
        
        assertTrue(content.length <= 400 + 100, "Content should be truncated to available budget")
        assertTrue(content.endsWith("... (truncated for context)"), "Content should have truncation suffix")
        assertEquals("Search Result", item["title"]!!.jsonPrimitive.content, "Title should remain untouched")
    }

    @Test
    fun `truncateToolResult should truncate raw_content in extract results`() {
        val longContent = "B".repeat(10000)
        val resultJson = """
            {
                "results": [
                    {
                        "url": "https://example.com/page",
                        "raw_content": "$longContent"
                    }
                ]
            }
        """.trimIndent()

        // 500 tokens context, 400 used, 50 buffer = 50 available = 200 chars
        val truncated = NativeToolResultFormatter.truncateToolResult(
            resultJson = resultJson,
            contextWindowTokens = 500,
            estimatedUsedTokens = 400,
            bufferTokens = 50
        )
        
        val payload = Json.parseToJsonElement(truncated).jsonObject
        val item = payload["results"]!!.jsonArray[0].jsonObject
        val content = item["raw_content"]!!.jsonPrimitive.content
        
        assertTrue(content.length <= 200 + 100, "Raw content should be truncated")
        assertTrue(content.endsWith("... (truncated for context)"), "Content should have truncation suffix")
    }

    @Test
    fun `truncateToolResult should return error if no tokens available`() {
        val resultJson = """{"results":[{"content":"some content"}]}"""
        
        // 1000 context, 950 used, 100 buffer = -50 available
        val truncated = NativeToolResultFormatter.truncateToolResult(
            resultJson = resultJson,
            contextWindowTokens = 1000,
            estimatedUsedTokens = 950,
            bufferTokens = 100
        )
        
        assertEquals("""{"error": "cannot read page, context window too full"}""", truncated)
    }

    @Test
    fun `truncateToolResult should distribute budget among multiple results`() {
        val resultJson = """
            {
                "results": [
                    {"content": "${"A".repeat(1000)}"},
                    {"content": "${"B".repeat(1000)}"},
                    {"content": "${"C".repeat(1000)}"}
                ]
            }
        """.trimIndent()

        // 500 context, 400 used, 50 buffer = 50 tokens available = 200 chars
        // 200 chars / 3 results = 66 chars per result. 
        // Our maxCharsPerResult uses maxOf(100, ...) so it should be 100 chars.
        val truncated = NativeToolResultFormatter.truncateToolResult(
            resultJson = resultJson,
            contextWindowTokens = 500,
            estimatedUsedTokens = 400,
            bufferTokens = 50
        )
        
        val payload = Json.parseToJsonElement(truncated).jsonObject
        val results = payload["results"]!!.jsonArray
        assertEquals(3, results.size)
        
        for (i in 0 until 3) {
            val content = results[i].jsonObject["content"]!!.jsonPrimitive.content
            assertTrue(content.length <= 100 + 100, "Result $i should be truncated to floor budget of 100")
        }
    }

    @Test
    fun `truncateToolResult should not touch small results`() {
        val resultJson = """{"results":[{"content":"short and sweet"}]}"""
        
        // 2000 context, 0 used, 1000 buffer = 1000 tokens = 4000 chars
        val truncated = NativeToolResultFormatter.truncateToolResult(
            resultJson = resultJson,
            contextWindowTokens = 2000,
            estimatedUsedTokens = 0
        )
        
        val payload = Json.parseToJsonElement(truncated).jsonObject
        val item = payload["results"]!!.jsonArray[0].jsonObject
        assertEquals("short and sweet", item["content"]!!.jsonPrimitive.content, "Small results should preserve content")
    }

    @Test
    fun `parseUrls should handle comma-separated string`() {
        val input = "https://google.com, https://openai.com"
        val result = NativeToolResultFormatter.parseUrls(input)
        assertEquals(2, result.size)
        assertEquals("https://google.com", result[0])
        assertEquals("https://openai.com", result[1])
    }

    @Test
    fun `parseUrls should handle JSON-style array string`() {
        val input = "[\"https://google.com\", \"https://openai.com\"]"
        val result = NativeToolResultFormatter.parseUrls(input)
        assertEquals(2, result.size)
        assertEquals("https://google.com", result[0])
        assertEquals("https://openai.com", result[1])
    }

    @Test
    fun `parseUrls should handle single URL`() {
        val input = "https://google.com"
        val result = NativeToolResultFormatter.parseUrls(input)
        assertEquals(1, result.size)
        assertEquals("https://google.com", result[0])
    }
}
