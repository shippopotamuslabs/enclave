package com.browntowndev.enclave.domain.util

import com.browntowndev.enclave.domain.model.chat.MessageId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WebSearchResultParserTest {
    @Test
    fun `parse handles standard web search results`() {
        val json =
            """
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
        val json =
            """
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

    @Test
    fun `XPRE-09 WebSearchResultParser parses XThreadResponse JSON and populates WebSearchSource`() {
        val json =
            """
            {
              "requested_post": {
                "id": "123",
                "text": "Target post content",
                "user_name": "John Doe",
                "username": "johndoe",
                "url": "https://x.com/johndoe/status/123",
                "impact_score": 10.0
              },
              "ancestor_posts": [
                {
                  "id": "122",
                  "text": "Parent post content",
                  "user_name": "Jane Smith",
                  "username": "janesmith",
                  "url": "https://x.com/janesmith/status/122",
                  "impact_score": 5.0
                }
              ],
              "data": [
                {
                  "id": "122",
                  "text": "Parent post content",
                  "user_name": "Jane Smith",
                  "username": "janesmith",
                  "url": "https://x.com/janesmith/status/122",
                  "impact_score": 5.0
                },
                {
                  "id": "123",
                  "text": "Target post content",
                  "user_name": "John Doe",
                  "username": "johndoe",
                  "url": "https://x.com/johndoe/status/123",
                  "impact_score": 10.0
                }
              ],
              "anchor_tweet_id": "123"
            }
            """.trimIndent()
        val messageId = MessageId("msg_assistant_1")
        val sources = WebSearchResultParser.parse(messageId, json)

        assertEquals(2, sources.size)
        assertEquals("Jane Smith", sources[0].title)
        assertEquals("https://x.com/janesmith/status/122", sources[0].url)
        assertEquals("Parent post content", sources[0].content)
        assertEquals(5.0, sources[0].score)

        assertEquals("John Doe", sources[1].title)
        assertEquals("https://x.com/johndoe/status/123", sources[1].url)
        assertEquals("Target post content", sources[1].content)
        assertEquals(10.0, sources[1].score)
    }
}
