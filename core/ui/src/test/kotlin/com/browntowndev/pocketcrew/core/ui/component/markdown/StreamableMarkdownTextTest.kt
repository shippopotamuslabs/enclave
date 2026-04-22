package com.browntowndev.pocketcrew.core.ui.component.markdown

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StreamableMarkdownTextTest {
    @Test
    fun `resolveMarkdownText returns markdown unchanged while streaming`() {
        val rendered = resolveMarkdownText("incoming text")

        assertEquals("incoming text", rendered)
    }

    @Test
    fun `resolveMarkdownText returns final markdown unchanged`() {
        val rendered = resolveMarkdownText("full final text")

        assertEquals("full final text", rendered)
    }
}
