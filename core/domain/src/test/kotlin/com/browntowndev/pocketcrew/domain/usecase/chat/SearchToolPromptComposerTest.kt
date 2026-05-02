package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.memory.Memory
import com.browntowndev.pocketcrew.domain.model.memory.MemoryCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
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
                """ActionResult{"name":"tavily_web_search","arguments":{"""
            )
        )
        assertEquals("Be concise.", basePrompt)
    }

    @Test
    fun `compose can include the image inspect contract when requested`() {
        val composed = composer.compose("Be concise.", includeImageInspectTool = true)

        assertTrue(composed.contains("tavily_web_search"))
        assertTrue(composed.contains("attached_image_inspect"))
    }

    @Test
    fun `compose only includes image inspect when search is disabled but image tool is requested`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = true
        )

        assertFalse(composed.contains("tavily_web_search"), "Should not contain search contract")
        assertTrue(composed.contains("attached_image_inspect"), "Should contain image inspect contract")
        assertTrue(composed.contains("Be concise."), "Should contain base prompt")
    }

    @Test
    fun `compose uses LiteRT native format when strategy is LITE_RT_NATIVE`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = true,
            strategy = ToolCallStrategy.LITE_RT_NATIVE
        )

        assertTrue(composed.contains("call:tavily_web_search{query: <|\"|>...<|\"|>}"))
        assertTrue(composed.contains("tavily_extract. Note that 'urls' must be a SINGLE string"))
        assertTrue(composed.contains("April 16, 2026"))
        // tool_call may appear in strict rules
    }

    @Test
    fun `compose uses LiteRT native format for both search and image tools`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = true,
            includeImageInspectTool = true,
            strategy = ToolCallStrategy.LITE_RT_NATIVE
        )

        assertTrue(composed.contains("call:tavily_web_search"))
        assertTrue(composed.contains("call:attached_image_inspect"))
        assertTrue(composed.contains("STRICT EXECUTION RULES:"))
        assertTrue(composed.contains("Use exactly the 'call:tool_name{...}' format"))
        assertTrue(composed.contains("NEVER wrap the tool call in special tokens"))
    }

    @Test
    fun `compose returns empty tool contract for SDK_NATIVE strategy`() {
        val basePrompt = "API Model Prompt"
        val composed = composer.compose(
            baseSystemPrompt = basePrompt,
            includeSearchTool = true,
            includeImageInspectTool = true,
            strategy = ToolCallStrategy.SDK_NATIVE
        )

        assertEquals(basePrompt, composed)
        assertFalse(composed.contains("mandate", ignoreCase = true))
        assertFalse(composed.contains("tool_call"))
    }

    @Test
    fun `compose with no tools enabled returns only base prompt`() {
        val basePrompt = "Base only."
        val composed = composer.compose(
            baseSystemPrompt = basePrompt,
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = false
        )

        assertEquals(basePrompt, composed)
    }

    @Test
    fun `compose includes memory tools contract by default`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true
        )

        assertTrue(composed.contains("search_chat_history"))
        assertTrue(composed.contains("search_chat"))
        assertFalse(composed.contains("tavily_web_search"))
    }

    @Test
    fun `compose includes LiteRT memory tools format for LITE_RT_NATIVE strategy`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true,
            strategy = ToolCallStrategy.LITE_RT_NATIVE
        )

        assertTrue(composed.contains("call:search_chat_history"))
        assertTrue(composed.contains("call:search_chat"))
    }

    @Test
    fun `compose includes current chat ID in memory tools contract when provided`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true,
            currentChatId = "chat-123"
        )

        assertTrue(composed.contains("chat-123"))
        assertTrue(composed.contains("search_chat"))
        assertTrue(composed.contains("chat_id"))
    }

    @Test
    fun `compose omits current chat ID when not provided`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true,
            currentChatId = null
        )

        assertFalse(composed.contains("The current chat ID is"))
    }

    @Test
    fun `compose includes get_message_context in JSON_XML_ENVELOPE memory tools contract`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true
        )

        assertTrue(composed.contains("get_message_context"))
        assertTrue(composed.contains("message_id"))
    }

    @Test
    fun `compose includes get_message_context in LITE_RT_NATIVE memory tools contract`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeSearchTool = false,
            includeImageInspectTool = false,
            includeMemoryTools = true,
            strategy = ToolCallStrategy.LITE_RT_NATIVE
        )

        assertTrue(composed.contains("call:get_message_context"))
        assertTrue(composed.contains("message_id"))
    }

    @Test
    fun `compose includes manage_memories in memory tools contract`() {
        val composed = composer.compose(
            baseSystemPrompt = "Be concise.",
            includeMemoryTools = true
        )

        assertTrue(composed.contains("manage_memories"))
        assertTrue(composed.contains("save, update, delete, or search long-term memories"))
    }

    @Test
    fun `compose formats core memories correctly`() {
        val memories = listOf(
            Memory(category = MemoryCategory.CORE_IDENTITY, content = "I am a helpful assistant."),
            Memory(category = MemoryCategory.PREFERENCES, content = "User prefers short answers.")
        )
        val composed = composer.compose(
            baseSystemPrompt = "Base",
            coreMemories = memories
        )

        assertTrue(composed.contains("# ABOUT THE USER:"))
        assertTrue(composed.contains("## Identity & Background:"))
        assertTrue(composed.contains("- I am a helpful assistant."))
        assertTrue(composed.contains("## Preferences & Style:"))
        assertTrue(composed.contains("- User prefers short answers."))
    }

    @Test
    fun `compose formats retrieved memories correctly`() {
        val memories = listOf(
            Memory(category = MemoryCategory.FACTS, content = "The sky is blue.")
        )
        val composed = composer.compose(
            baseSystemPrompt = "Base",
            retrievedMemories = memories
        )

        assertTrue(composed.contains("# RELEVANT FACTS & CONTEXT:"))
        assertTrue(composed.contains("The following information was retrieved from your long-term memory"))
        assertTrue(composed.contains("- The sky is blue."))
    }
}
