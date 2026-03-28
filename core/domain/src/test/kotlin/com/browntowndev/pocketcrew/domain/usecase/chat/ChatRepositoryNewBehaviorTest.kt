package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.FakeChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for ChatRepository - NEW BEHAVIOR (pipeline state persistence for CREW resume).
 * 
 * These tests verify the new behavior where:
 * 1. getIncompleteCrewMessages returns messages that need pipeline state for resume
 * 2. Messages include all data needed to resume CREW pipeline (agent, step, content)
 * 
 * REF: TDD RED phase - these tests will FAIL until implementation is complete.
 */
class ChatRepositoryNewBehaviorTest {

    private lateinit var chatRepository: FakeChatRepository

    @BeforeEach
    fun setup() {
        chatRepository = FakeChatRepository()
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns messages with GENERATING state
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns GENERATING messages`() = runTest {
        // Given - create chat with messages
        val chatId = 1L
        
        // Create messages - one complete, one generating
        val completeMessage = Message(
            id = 1L,
            chatId = chatId,
            content = Content("Completed response"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE
        )
        
        val generatingMessage = Message(
            id = 2L,
            chatId = chatId,
            content = Content("Partial response..."),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(completeMessage, generatingMessage))
        
        // When - getIncompleteCrewMessages should filter to only GENERATING
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then - should only return the generating message
        // Note: This test will fail until getIncompleteCrewMessages is implemented
        assertTrue(incompleteMessages.isNotEmpty())
        assertEquals(1, incompleteMessages.size)
        assertEquals(2L, incompleteMessages.first().id)
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages excludes COMPLETE messages
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages excludes COMPLETE messages`() = runTest {
        // Given - create chat with only COMPLETE messages
        val chatId = 1L
        
        val completeMessage = Message(
            id = 1L,
            chatId = chatId,
            content = Content("Completed response"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(completeMessage))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then - should return empty list
        // Note: This test will fail until getIncompleteCrewMessages is implemented
        assertTrue(incompleteMessages.isEmpty())
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages includes thinking data for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages includes thinking data for resume`() = runTest {
        // Given - create chat with GENERATING message that has thinking
        val chatId = 1L
        
        val generatingMessage = Message(
            id = 2L,
            chatId = chatId,
            content = Content("Partial response..."),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            thinkingRaw = "Partial thinking...",
            thinkingDurationSeconds = 5
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(generatingMessage))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then - should include thinking data for pipeline resume
        // Note: This test will fail until getIncompleteCrewMessages is implemented
        assertTrue(incompleteMessages.isNotEmpty())
        val message = incompleteMessages.first()
        assertNotNull(message.thinkingRaw)
        assertTrue(requireNotNull(message.thinkingRaw).contains("Partial thinking"))
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages includes modelType for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages includes modelType for resume`() = runTest {
        // Given - create chat with GENERATING message that has modelType
        val chatId = 1L
        
        val generatingMessage = Message(
            id = 2L,
            chatId = chatId,
            content = Content("Agent response..."),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            modelType = ModelType.DRAFT_ONE
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(generatingMessage))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then - should include modelType for pipeline resume
        // Note: This test will fail until getIncompleteCrewMessages is implemented
        assertTrue(incompleteMessages.isNotEmpty())
        val message = incompleteMessages.first()
        assertNotNull(message.modelType)
        assertEquals(ModelType.DRAFT_ONE, message.modelType)
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns empty for non-existent chat
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns empty for non-existent chat`() = runTest {
        // Given - chat doesn't exist
        val nonExistentChatId = 999L
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(nonExistentChatId)
        
        // Then - should return empty list
        assertTrue(incompleteMessages.isEmpty())
    }
}
