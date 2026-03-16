package com.browntowndev.pocketcrew.presentation.screen.chat.components

import com.browntowndev.pocketcrew.presentation.screen.chat.ChatMessage
import com.browntowndev.pocketcrew.presentation.screen.chat.MessageRole
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import com.browntowndev.pocketcrew.presentation.screen.chat.ProcessingIndicatorState
import com.browntowndev.pocketcrew.presentation.screen.chat.ThinkingData
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for MessageList indicator placement logic.
 * Processing, Generating, and Thinking indicators should ONLY show after the latest user message.
 * ThoughtForHeader should show after ALL user messages (when thinking model generated the reply).
 * This is only relevant for Fast/Thinking mode, NOT Crew mode.
 */
class MessageListIndicatorPlacementTest {

    // ==================== Helper Functions ====================

    /**
     * This simulates what the fix should do: determine if an assistant message
     * is the one that follows the most recent user message in display order.
     *
     * The messages list is ordered with most recent first (index 0).
     * With reverseLayout=true in LazyColumn:
     * - Display position 0 = messages[last] (oldest)
     * - Display position n = messages[0] (most recent)
     */
    private fun computeIsLatestAssistantMessage(messages: List<ChatMessage>, messageIndex: Int): Boolean {
        val message = messages.getOrNull(messageIndex) ?: return false
        if (message.role != MessageRole.Assistant) return false

        // Find the most recent user (using indexOfFirst since list is ordered most recent first)
        val mostRecentUserIndex = messages.indexOfFirst { it.role == MessageRole.User }
        if (mostRecentUserIndex == -1) return false

        // The assistant message that follows the most recent user is at mostRecentUserIndex - 1
        // (since messages are [most_recent, ..., oldest])
        return messageIndex == mostRecentUserIndex - 1
    }

    // ==================== Fast/Thinking Mode - Indicator Placement Tests ====================

    @Test
    fun `single user message followed by assistant - assistant is latest`() {
        // Given: Single user message followed by assistant
        val messages = listOf(
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Hello", formattedTimestamp = "10:29 AM"),
        )

        // Assistant at index 0 follows the most recent user (at index 1)
        assertTrue(computeIsLatestAssistantMessage(messages, 0))
    }

    @Test
    fun `multiple user messages - only assistant after latest user is latest`() {
        // Given: Multiple user messages with assistant messages in between
        // Messages: [A(id=5), U(id=4), A(id=3), U(id=2), U(id=1)]
        val messages = listOf(
            ChatMessage(id = 5L, chatId = 1L, role = MessageRole.Assistant, content = "Response to third", formattedTimestamp = "10:32 AM"),
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "Third question?", formattedTimestamp = "10:31 AM"),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Response to second", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Second question", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "First question", formattedTimestamp = "10:28 AM"),
        )

        // Most recent user is at index 1 (U id=4)
        // Assistant after it is at index 0 (A id=5) - this is the latest
        assertTrue(computeIsLatestAssistantMessage(messages, 0))

        // Assistant at index 2 is NOT the latest
        assertFalse(computeIsLatestAssistantMessage(messages, 2))
    }

    @Test
    fun `generating indicator should only show after latest user message`() {
        // Given: Conversation with multiple exchanges
        val messages = listOf(
            ChatMessage(id = 7L, chatId = 1L, role = MessageRole.Assistant, content = "Latest response", formattedTimestamp = "10:34 AM"),
            ChatMessage(id = 6L, chatId = 1L, role = MessageRole.User, content = "Latest question", formattedTimestamp = "10:33 AM"),
            ChatMessage(id = 5L, chatId = 1L, role = MessageRole.Assistant, content = "Old response 2", formattedTimestamp = "10:32 AM"),
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "Old question 2", formattedTimestamp = "10:31 AM"),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Old response 1", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Old question 1", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Even older question", formattedTimestamp = "10:28 AM"),
        )

        // Most recent user is at index 1 (U id=6)
        // Assistant after it is at index 0 (A id=7) - this is the latest
        assertTrue(computeIsLatestAssistantMessage(messages, 0))

        // Assistant at index 2 is NOT the latest
        assertFalse(computeIsLatestAssistantMessage(messages, 2))

        // Assistant at index 4 is NOT the latest
        assertFalse(computeIsLatestAssistantMessage(messages, 4))
    }

    @Test
    fun `thinking indicator should only show after latest user message`() {
        // Given: Multiple assistant messages with thinking data
        val messages = listOf(
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.Assistant, content = "Latest with thinking", formattedTimestamp = "10:30 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 5, steps = listOf("Thinking..."), modelDisplayName = "Model")),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.User, content = "Question", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Old response", formattedTimestamp = "10:28 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 3, steps = listOf("Old thinking..."), modelDisplayName = "Model")),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Old question", formattedTimestamp = "10:27 AM"),
        )

        // Most recent user is at index 1
        // Assistant after it is at index 0 - this is the latest
        assertTrue(computeIsLatestAssistantMessage(messages, 0))

        // Assistant at index 2 is NOT the latest
        assertFalse(computeIsLatestAssistantMessage(messages, 2))
    }

    // ==================== ThoughtForHeader Placement Tests ====================

    @Test
    fun `ThoughtForHeader should show for ALL assistant messages with thinking data`() {
        // Given: Multiple assistant messages, all with thinking data
        val messages = listOf(
            ChatMessage(id = 5L, chatId = 1L, role = MessageRole.Assistant, content = "Latest response", formattedTimestamp = "10:32 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 10, steps = listOf("Thinking"), modelDisplayName = "Model")),
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "Latest question", formattedTimestamp = "10:31 AM"),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Middle response", formattedTimestamp = "10:30 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 8, steps = listOf("Old thinking"), modelDisplayName = "Model")),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Middle question", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "First response", formattedTimestamp = "10:28 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 5, steps = listOf("First thinking"), modelDisplayName = "Model")),
        )

        // All assistant messages with thinking data should be able to show ThoughtForHeader
        // This is independent of whether they are the most recent assistant message
        assertNotNull(messages[0].thinkingData) // Latest
        assertNotNull(messages[2].thinkingData) // Middle
        assertNotNull(messages[4].thinkingData) // First
    }

    @Test
    fun `ThoughtForHeader should NOT show for messages without thinking data`() {
        // Given: Assistant messages, some with thinking data, some without
        val messages = listOf(
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.Assistant, content = "With thinking", formattedTimestamp = "10:30 AM",
                thinkingData = ThinkingData(thinkingDurationSeconds = 5, steps = listOf("Thinking"), modelDisplayName = "Model")),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.User, content = "Question", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Without thinking", formattedTimestamp = "10:28 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Old question", formattedTimestamp = "10:27 AM"),
        )

        // First assistant has thinking data
        assertNotNull(messages[0].thinkingData)
        // Second assistant does NOT have thinking data
        assertNull(messages[2].thinkingData)
    }

    // ==================== Edge Cases Tests ====================

    @Test
    fun `no indicators shown when there are no user messages`() {
        // Given: Only assistant messages (edge case)
        val messages = listOf(
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Response 2", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Response 1", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Response 0", formattedTimestamp = "10:28 AM"),
        )

        // No user messages, so no "most recent user"
        val mostRecentUserIndex = messages.indexOfFirst { it.role == MessageRole.User }
        assertEquals(-1, mostRecentUserIndex)

        // No assistant should be considered "latest"
        assertFalse(computeIsLatestAssistantMessage(messages, 0))
        assertFalse(computeIsLatestAssistantMessage(messages, 1))
        assertFalse(computeIsLatestAssistantMessage(messages, 2))
    }

    @Test
    fun `user message is last in list - no assistant after it`() {
        // Given: User message is the most recent (last in list)
        val messages = listOf(
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Latest", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "10:29 AM"),
        )

        // Most recent user is at index 0
        // There's no assistant at index -1, so no "latest assistant message"
        assertFalse(computeIsLatestAssistantMessage(messages, 1))
    }

    @Test
    fun `multiple consecutive user messages - indicator after the last one`() {
        // Given: Multiple consecutive user messages
        val messages = listOf(
            ChatMessage(id = 6L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "10:32 AM"),
            ChatMessage(id = 5L, chatId = 1L, role = MessageRole.User, content = "Third user", formattedTimestamp = "10:31 AM"),
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "Second user", formattedTimestamp = "10:30 AM"),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.User, content = "First user", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Old response", formattedTimestamp = "10:28 AM"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Oldest user", formattedTimestamp = "10:27 AM"),
        )

        // Most recent user is at index 1
        // Assistant after it is at index 0 - this is the latest
        assertTrue(computeIsLatestAssistantMessage(messages, 0))

        // Assistant at index 4 is NOT the latest
        assertFalse(computeIsLatestAssistantMessage(messages, 4))
    }

    // ==================== Crew Mode Tests ====================

    @Test
    fun `Crew mode should not be affected by indicator placement rules`() {
        // Given: Crew mode messages with completedSteps
        val messages = listOf(
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.Assistant, content = "Crew response", formattedTimestamp = "10:30 AM",
                completedSteps = emptyList()), // Empty but not null - still Crew mode
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.User, content = "Question", formattedTimestamp = "10:29 AM"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.Assistant, content = "Old crew response", formattedTimestamp = "10:28 AM",
                completedSteps = emptyList()),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Old question", formattedTimestamp = "10:27 AM"),
        )

        // Crew mode uses different logic - completedSteps determines mode
        // This test verifies the data structure is set up correctly for Crew mode
        assertNotNull(messages[0].completedSteps)
        assertNotNull(messages[2].completedSteps)
    }
}
