package com.browntowndev.pocketcrew.domain.util

import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSearchResultParserTest {

    @Test
    fun `parse handles standard web search results`() {
        val json = """
            {
              "results": [
                {
                  "title": "Example",
                  "url": "https://example.com",
                  "content": "Example content",
                  "score": 0.95
                }
              ]
            }
        """.trimIndent()
        val messageId = MessageId("msg1")
        val sources = WebSearchResultParser.parse(messageId, json)

        assertEquals(1, sources.size)
        assertEquals("Example", sources[0].title)
        assertEquals("https://example.com", sources[0].url)
        assertEquals("Example content", sources[0].content)
        assertEquals(0.95, sources[0].score)
    }

    @Test
    fun `parse handles flattened X search results array`() {
        val json = """
            [
              {
                "id": "123",
                "text": "Tweet text",
                "user_name": "Tester",
                "url": "https://x.com/tester/status/123",
                "impact_score": 5.5
              }
            ]
        """.trimIndent()
        val messageId = MessageId("msg1")
        val sources = WebSearchResultParser.parse(messageId, json)

        assertEquals(1, sources.size)
        assertEquals("Tester", sources[0].title)
        assertEquals("https://x.com/tester/status/123", sources[0].url)
        assertEquals("Tweet text", sources[0].content)
        assertEquals(5.5, sources[0].score)
    }

    @Test
    fun `parse handles empty results`() {
        val json = "[]"
        val messageId = MessageId("msg1")
        val sources = WebSearchResultParser.parse(messageId, json)
        assertEquals(0, sources.size)
    }

    @Test
    fun `parse handles invalid json gracefully`() {
        val json = "invalid"
        val messageId = MessageId("msg1")
        val sources = WebSearchResultParser.parse(messageId, json)
        assertEquals(0, sources.size)
    }
}
