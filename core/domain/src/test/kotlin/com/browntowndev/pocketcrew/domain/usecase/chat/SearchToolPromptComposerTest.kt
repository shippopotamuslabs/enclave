package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolPromptComposerTest {

    private val composer = SearchToolPromptComposer()

    @Test
    fun `compose appends the canonical search contract without mutating the base prompt`() {
        val basePrompt = "Be concise."

        val composed = composer.compose(basePrompt)

        assertTrue(composed.contains("Be concise."))
        assertTrue(
            composed.contains(
                """<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>"""
            )
        )
        assertFalse(composed.contains("attached_image_inspect"))
        assertEquals("Be concise.", basePrompt)
    }

    @Test
    fun `compose can include the image inspect contract when requested`() {
        val composed = composer.compose("Be concise.", includeImageInspectTool = true)

        assertTrue(composed.contains("tavily_web_search"))
        assertTrue(composed.contains("attached_image_inspect"))
    }
}
