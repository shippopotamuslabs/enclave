package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.usecase.FakeChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for ChatRepository - getIncompleteCrewMessages and persistMessage.
 * 
 * These tests verify:
 * 1. getIncompleteCrewMessages returns only IN_PROGRESS messages
 * 2. getIncompleteCrewMessages includes all data for CREW pipeline resume
 * 3. Repository correctly persists message state changes
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class ChatRepositoryTest {

    private lateinit var chatRepository: FakeChatRepository

    @BeforeEach
    fun setup() {
        chatRepository = FakeChatRepository()
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns GENERATING state messages
    // Evidence: Only messages in GENERATING state need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns GENERATING messages`() = runTest {
        // Given
        val chatId = 1L
        
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
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then
        assertTrue(incompleteMessages.isNotEmpty())
        assertEquals(1, incompleteMessages.size)
        assertEquals(2L, incompleteMessages.first().id)
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages excludes COMPLETE messages
    // Evidence: Complete messages don't need resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages excludes COMPLETE messages`() = runTest {
        // Given
        val chatId = 1L
        
        val completeMessage1 = Message(
            id = 1L,
            chatId = chatId,
            content = Content("First completed"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE
        )
        
        val completeMessage2 = Message(
            id = 2L,
            chatId = chatId,
            content = Content("Second completed"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(completeMessage1, completeMessage2))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then
        assertTrue(incompleteMessages.isEmpty())
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages includes thinking data for resume
    // Evidence: Thinking is needed for CREW pipeline resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages includes thinking data`() = runTest {
        // Given
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
        
        // Then
        assertTrue(incompleteMessages.isNotEmpty())
        val message = incompleteMessages.first()
        assertNotNull(message.thinkingRaw)
        assertTrue(requireNotNull(message.thinkingRaw).contains("Partial thinking"))
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages includes modelType for resume
    // Evidence: ModelType is needed for CREW pipeline resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages includes modelType`() = runTest {
        // Given
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
        
        // Then
        assertTrue(incompleteMessages.isNotEmpty())
        val message = incompleteMessages.first()
        assertNotNull(message.modelType)
        assertEquals(ModelType.DRAFT_ONE, message.modelType)
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns empty for non-existent chat
    // Evidence: Graceful handling of missing chat
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns empty for non-existent chat`() = runTest {
        // Given
        val nonExistentChatId = 999L
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(nonExistentChatId)
        
        // Then
        assertTrue(incompleteMessages.isEmpty())
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns PROCESSING state messages
    // Evidence: PROCESSING messages also need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns PROCESSING messages`() = runTest {
        // Given
        val chatId = 1L
        
        val processingMessage = Message(
            id = 2L,
            chatId = chatId,
            content = Content(""),
            role = Role.ASSISTANT,
            messageState = MessageState.PROCESSING
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(processingMessage))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then
        assertTrue(incompleteMessages.isNotEmpty())
        assertEquals(1, incompleteMessages.size)
        assertEquals(2L, incompleteMessages.first().id)
    }

    // ========================================================================
    // Test: getIncompleteCrewMessages returns THINKING state messages
    // Evidence: THINKING messages also need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCrewMessages returns THINKING messages`() = runTest {
        // Given
        val chatId = 1L
        
        val thinkingMessage = Message(
            id = 2L,
            chatId = chatId,
            content = Content(""),
            role = Role.ASSISTANT,
            messageState = MessageState.THINKING
        )
        
        chatRepository.setMessagesForChat(chatId, listOf(thinkingMessage))
        
        // When
        val incompleteMessages = chatRepository.getIncompleteCrewMessages(chatId)
        
        // Then
        assertTrue(incompleteMessages.isNotEmpty())
        assertEquals(1, incompleteMessages.size)
        assertEquals(2L, incompleteMessages.first().id)
    }
}
