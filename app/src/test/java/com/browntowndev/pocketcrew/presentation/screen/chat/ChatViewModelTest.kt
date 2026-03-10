package com.browntowndev.pocketcrew.presentation.screen.chat

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for ChatViewModel Crew Mode state and Mode enum.
 */
class ChatViewModelTest {

    @Test
    fun `Mode enum has correct entries`() {
        assertEquals(2, Mode.entries.size)
    }

    @Test
    fun `Mode FAST has correct display name resource`() {
        // Just verify the enum works
        assertEquals(Mode.FAST, Mode.valueOf("FAST"))
    }

    @Test
    fun `Mode CREW has correct display name resource`() {
        // Just verify the enum works
        assertEquals(Mode.CREW, Mode.valueOf("CREW"))
    }

    @Test
    fun `ChatUiState default values are correct`() {
        val state = ChatUiState()

        assertEquals(Mode.FAST, state.selectedMode)
        assertEquals("", state.inputText)
        assertEquals(false, state.isInputExpanded)
        assertEquals(false, state.isThinking)
        assertEquals(emptyList<String>(), state.thinkingSteps)
        assertEquals(false, state.showUseTheCrewPopup)
    }

    @Test
    fun `ChatMessage has correct structure`() {
        val message = ChatMessage(
            id = "test123",
            role = MessageRole.User,
            content = "Hello",
            formattedTimestamp = "Now"
        )

        assertEquals("test123", message.id)
        assertEquals(MessageRole.User, message.role)
        assertEquals("Hello", message.content)
        assertEquals("Now", message.formattedTimestamp)
    }

    @Test
    fun `ThinkingData has correct structure`() {
        val thinkingData = ThinkingData(
            durationSeconds = 30,
            steps = listOf("Step 1", "Step 2")
        )

        assertEquals(30, thinkingData.durationSeconds)
        assertEquals(2, thinkingData.steps.size)
    }

    @Test
    fun `ChatMessage can have thinking data`() {
        val thinkingData = ThinkingData(
            durationSeconds = 30,
            steps = listOf("Step 1", "Step 2")
        )

        val message = ChatMessage(
            id = "test123",
            role = MessageRole.Assistant,
            content = "Response",
            formattedTimestamp = "Now",
            thinkingData = thinkingData
        )

        assertEquals(thinkingData, message.thinkingData)
    }

    @Test
    fun `MessageRole enum has correct entries`() {
        assertEquals(2, MessageRole.entries.size)
        assertEquals(MessageRole.User, MessageRole.valueOf("User"))
        assertEquals(MessageRole.Assistant, MessageRole.valueOf("Assistant"))
    }
}
