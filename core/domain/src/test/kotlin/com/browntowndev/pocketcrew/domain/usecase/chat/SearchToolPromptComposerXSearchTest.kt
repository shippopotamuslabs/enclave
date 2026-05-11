package com.browntowndev.pocketcrew.domain.usecase.chat

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchToolPromptComposerXSearchTest {

    private val composer = SearchToolPromptComposer()

    @Test
    fun `compose includes X search contract when requested`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeXSearchTool = true
        )

        assertTrue(composed.contains("search_x_recent"))
        assertTrue(composed.contains("X (TWITTER) SEARCH MANDATE"))
    }

    @Test
    fun `compose excludes X search contract when not requested`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeXSearchTool = false
        )

        assertFalse(composed.contains("search_x_recent"))
    }

    @Test
    fun `X search contract coexists with web search contract`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = true,
            includeXSearchTool = true
        )

        assertTrue(composed.contains("web_search"))
        assertTrue(composed.contains("search_x_recent"))
    }

    @Test
    fun `compose includes LiteRT X search format for LITE_RT_NATIVE strategy`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeXSearchTool = true,
            strategy = ToolCallStrategy.LITE_RT_NATIVE
        )

        assertTrue(composed.contains("call:search_x_recent"))
    }
}
