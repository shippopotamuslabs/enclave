package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolPromptComposerTest {

    private val composer = SearchToolPromptComposer()

    @Test
    fun `compose appends the canonical local contract without mutating the base prompt`() {
        val basePrompt = "Be concise."

        val composed = composer.compose(basePrompt)

        assertTrue(composed.contains("Be concise."))
        assertTrue(
            composed.contains(
                """<tool_call>{"name":"tavily_web_search","arguments":{"query":"..."}}</tool_call>"""
            )
        )
        assertEquals("Be concise.", basePrompt)
    }
}
