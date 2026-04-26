package com.browntowndev.pocketcrew.core.ui.component.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class StreamableMarkdownTextTest {
    @Test
    fun `markdownRenderIdentity stays stable while streaming content changes`() {
        val partial = markdownRenderIdentity(
            markdown = "partial response",
            isStreaming = true,
        )
        val complete = markdownRenderIdentity(
            markdown = "partial response with final text",
            isStreaming = true,
        )

        assertEquals(partial, complete)
    }

    @Test
    fun `markdownRenderIdentity changes when streaming state changes`() {
        val streaming = markdownRenderIdentity(
            markdown = "final response",
            isStreaming = true,
        )
        val complete = markdownRenderIdentity(
            markdown = "final response",
            isStreaming = false,
        )

        assertNotEquals(streaming, complete)
    }

    @Test
    fun `markdownRenderIdentity changes when complete markdown content changes`() {
        val firstComplete = markdownRenderIdentity(
            markdown = "first final response",
            isStreaming = false,
        )
        val secondComplete = markdownRenderIdentity(
            markdown = "second final response",
            isStreaming = false,
        )

        assertNotEquals(firstComplete, secondComplete)
    }
}
