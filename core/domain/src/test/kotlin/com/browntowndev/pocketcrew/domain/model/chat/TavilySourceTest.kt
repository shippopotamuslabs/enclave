package com.browntowndev.pocketcrew.domain.model.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TavilySourceTest {

    @Test
    fun `extracted defaults to false`() {
        val source = TavilySource(
            messageId = MessageId("msg-1"),
            title = "Test Source",
            url = "https://example.com",
            content = "Sample content",
            score = 0.9,
        )

        assertFalse(source.extracted, "extracted should default to false")
    }

    @Test
    fun `extracted can be set to true`() {
        val source = TavilySource(
            messageId = MessageId("msg-1"),
            title = "Test Source",
            url = "https://example.com",
            content = "Sample content",
            score = 0.9,
            extracted = true,
        )

        assertTrue(source.extracted, "extracted should be true when explicitly set")
    }
}