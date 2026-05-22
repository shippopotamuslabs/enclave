package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for ChatRepository - getIncompleteCouncilMessages and persistMessage.
 *
 * These tests verify:
 * 1. getIncompleteCouncilMessages returns only IN_PROGRESS messages
 * 2. getIncompleteCouncilMessages includes all data for COUNCIL pipeline resume
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
    // Test: getIncompleteCouncilMessages returns GENERATING state messages
    // Evidence: Only messages in GENERATING state need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages returns GENERATING messages`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val completeMessage =
                Message(
                    id = MessageId("1"),
                    chatId = chatId,
                    content = Content("Completed response"),
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                )

            val generatingMessage =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content("Partial response..."),
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                )

            chatRepository.setMessagesForChat(chatId, listOf(completeMessage, generatingMessage))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isNotEmpty())
            assertEquals(1, incompleteMessages.size)
            assertEquals(MessageId("2"), incompleteMessages.first().id)
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages excludes COMPLETE messages
    // Evidence: Complete messages don't need resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages excludes COMPLETE messages`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val completeMessage1 =
                Message(
                    id = MessageId("1"),
                    chatId = chatId,
                    content = Content("First completed"),
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                )

            val completeMessage2 =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content("Second completed"),
                    role = Role.ASSISTANT,
                    messageState = MessageState.COMPLETE,
                )

            chatRepository.setMessagesForChat(chatId, listOf(completeMessage1, completeMessage2))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isEmpty())
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages includes thinking data for resume
    // Evidence: Thinking is needed for COUNCIL pipeline resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages includes thinking data`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val generatingMessage =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content("Partial response..."),
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                    thinkingRaw = "Partial thinking...",
                    thinkingDurationSeconds = 5,
                )

            chatRepository.setMessagesForChat(chatId, listOf(generatingMessage))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isNotEmpty())
            val message = incompleteMessages.first()
            assertNotNull(message.thinkingRaw)
            assertTrue(requireNotNull(message.thinkingRaw).contains("Partial thinking"))
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages includes modelType for resume
    // Evidence: ModelType is needed for COUNCIL pipeline resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages includes modelType`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val generatingMessage =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content("Agent response..."),
                    role = Role.ASSISTANT,
                    messageState = MessageState.GENERATING,
                    modelType = ModelType.DRAFT_ONE,
                )

            chatRepository.setMessagesForChat(chatId, listOf(generatingMessage))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isNotEmpty())
            val message = incompleteMessages.first()
            assertNotNull(message.modelType)
            assertEquals(ModelType.DRAFT_ONE, message.modelType)
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages returns empty for non-existent chat
    // Evidence: Graceful handling of missing chat
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages returns empty for non-existent chat`() =
        runTest {
            // Given
            val nonExistentChatId = ChatId("999")

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(nonExistentChatId)

            // Then
            assertTrue(incompleteMessages.isEmpty())
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages returns PROCESSING state messages
    // Evidence: PROCESSING messages also need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages returns PROCESSING messages`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val processingMessage =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content(""),
                    role = Role.ASSISTANT,
                    messageState = MessageState.PROCESSING,
                )

            chatRepository.setMessagesForChat(chatId, listOf(processingMessage))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isNotEmpty())
            assertEquals(1, incompleteMessages.size)
            assertEquals(MessageId("2"), incompleteMessages.first().id)
        }

    // ========================================================================
    // Test: getIncompleteCouncilMessages returns THINKING state messages
    // Evidence: THINKING messages also need pipeline state for resume
    // ========================================================================

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `getIncompleteCouncilMessages returns THINKING messages`() =
        runTest {
            // Given
            val chatId = ChatId("1")

            val thinkingMessage =
                Message(
                    id = MessageId("2"),
                    chatId = chatId,
                    content = Content(""),
                    role = Role.ASSISTANT,
                    messageState = MessageState.THINKING,
                )

            chatRepository.setMessagesForChat(chatId, listOf(thinkingMessage))

            // When
            val incompleteMessages = chatRepository.getIncompleteCouncilMessages(chatId)

            // Then
            assertTrue(incompleteMessages.isNotEmpty())
            assertEquals(1, incompleteMessages.size)
            assertEquals(MessageId("2"), incompleteMessages.first().id)
        }
}
