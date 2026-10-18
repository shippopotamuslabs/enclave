package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.usecase.FakeTransactionProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for CreateUserMessageUseCase.
 *
 * REF: CLARIFIED_REQUIREMENTS.md - Section 2 (CreateUserMessageUseCase - Chat Name Generation)
 *
 * Desired Behavior for chat name generation:
 * | Input | Output |
 * |-------|--------|
 * | Empty content | "New Chat" (default) |
 * | Single word | That word only |
 * | 2-5 words | First N words (N = actual count) |
 * | More than 5 words | First 5 words |
 * | No words (split fails) AND length > 30 | First 30 chars + "..." |
 * | Special characters | Included as-is |
 * | Numbers only | Allowed as chat name |
 */
class CreateUserMessageUseCaseTest {
    companion object {
        const val OSDA_D01_REQUIRED_SAVE_MESSAGE_COUNT = 1
    }

    private lateinit var transactionProvider: FakeTransactionProvider
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var createUserMessageUseCase: CreateUserMessageUseCase

    @BeforeEach
    fun setUp() {
        transactionProvider = FakeTransactionProvider()
        messageRepository = FakeMessageRepository()
        chatRepository = FakeChatRepository()
        createUserMessageUseCase = CreateUserMessageUseCase(transactionProvider, messageRepository, chatRepository)
    }

    // ========================================================================
    // Test: New Chat Creation with Null chatId (new sentinel)
    // Evidence: null chatId signals "new chat needed" — replaces ChatId("") sentinel
    // ========================================================================

    @Test
    fun `null chatId triggers new chat creation`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null, // null signals "no chat yet — create one"
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            // When
            val result = createUserMessageUseCase(message)

            // Then
            assertEquals(1, chatRepository.chats().size)
            assertEquals(MessageId("user-msg-id"), result.userMessageId)
        }

    // ========================================================================
    // Test: New Chat Creation with Empty Content
    // Evidence: Empty content should return "New Chat" as default name
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `empty content returns New Chat as default name`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null, // null triggers new chat creation
                    content = Content(text = ""),
                    role = Role.USER,
                )

            // When
            val result = createUserMessageUseCase(message)

            // Then
            val createdChat = chatRepository.chats().single()
            assertEquals("New Chat", createdChat.name)
            assertEquals(MessageId("user-msg-id"), result.userMessageId)
            assertEquals(createdChat.id, result.chatId)
        }

    // ========================================================================
    // Test: Chat Name Generation - Single Word
    // Evidence: Single word should be returned as-is
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `single word returns that word`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("Hello", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Chat Name Generation - Exactly 5 Words
    // Evidence: All 5 words should be returned
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `five words returns all five`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "The quick brown fox jumps"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("The quick brown fox jumps", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Chat Name Generation - More Than 5 Words
    // Evidence: Only first 5 words should be returned
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `more than five words returns first five`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "The quick brown fox jumps over the lazy dog"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("The quick brown fox jumps", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Chat Name Generation - Long String With No Spaces (>30 chars)
    // Evidence: Should truncate to first 30 chars with ellipsis
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `no words but long string truncates with ellipsis`() =
        runTest {
            // "aaaaaaaaa..." - 35 chars with no spaces
            val longString = "a".repeat(35)
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = longString),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("a".repeat(30) + "...", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Chat Name Generation - Special Characters
    // Evidence: Special characters should be included as-is
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `special characters are included in chat name`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "Hello @user! How's #testing?"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("Hello @user! How's #testing?", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Chat Name Generation - Numbers Only
    // Evidence: Numbers-only should be allowed as chat name
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `numbers only are allowed as chat name`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "12345"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then
            assertEquals("12345", chatRepository.chats().single().name)
        }

    // ========================================================================
    // Test: Existing Chat Continuation
    // Evidence: Message with existing chatId should use that chat
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `existing chat id uses that chat without creating new one`() =
        runTest {
            // Given
            val existingChatId = ChatId("existing-chat-99")

            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = existingChatId, // Existing chat — non-null
                    content = Content(text = "Continuing conversation"),
                    role = Role.USER,
                )

            // When
            val result = createUserMessageUseCase(message)

            // Then
            assertEquals(MessageId("user-msg-id"), result.userMessageId)
            assertEquals(existingChatId, result.chatId)
            // Should NOT create a new chat
            assertTrue(chatRepository.chats().isEmpty())
        }

    // ========================================================================
    // Test: Transaction Ensures Atomicity
    // Evidence: All operations wrapped in transaction
    // ========================================================================

    @Test
    fun `all operations wrapped in transaction`() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "Test"),
                    role = Role.USER,
                )

            // When
            createUserMessageUseCase(message)

            // Then - verify transaction was used
            assertEquals(1, transactionProvider.getTransactionCount())
        }

    @Test
    @DisplayName("OSDA-D-01 — null chatId saves chat and user without assistant")
    fun osdaD01_newChatPersistsUserOnly() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            val result = createUserMessageUseCase(message)

            assertEquals(OSDA_D01_REQUIRED_SAVE_MESSAGE_COUNT, messageRepository.messages().size)
            assertEquals(1, chatRepository.chats().size)
            assertEquals(Role.USER, messageRepository.messages().single().role)
            assertEquals(chatRepository.chats().single().id, result.chatId)
            assertEquals(MessageId("user-msg-id"), result.userMessageId)
        }

    @Test
    @DisplayName("OSDA-D-02 — existing chatId saves single user row")
    fun osdaD02_existingChatPersistsUserOnly() =
        runTest {
            val existingChatId = ChatId("existing-chat-99")

            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = existingChatId,
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            val result = createUserMessageUseCase(message)

            assertEquals(1, messageRepository.messages().size)
            assertTrue(chatRepository.chats().isEmpty())
            assertEquals(existingChatId, result.chatId)
        }

    @Test
    @DisplayName("OSDA-D-03 — five-word cap name generation still applies")
    fun osdaD03_chatNameGenerationUnchanged() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "one two three four five six"),
                    role = Role.USER,
                )

            createUserMessageUseCase(message)

            assertEquals("one two three four five", chatRepository.chats().single().name)
        }

    @Test
    @DisplayName("OSDA-D-04 — repository returns same id as input message")
    fun osdaD04_clientMessageIdPreserved() =
        runTest {
            val message =
                Message(
                    id = MessageId("client-uuid-7a2b"),
                    chatId = null,
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            val result = createUserMessageUseCase(message)

            assertEquals(MessageId("client-uuid-7a2b"), result.userMessageId)
        }

    @Test
    @DisplayName("OSDA-M-01 — assistant save in user use case fails single-save assertion")
    fun osdaM01_combinedTransactionGuard() =
        runTest {
            val message =
                Message(
                    id = MessageId("user-msg-id"),
                    chatId = null,
                    content = Content(text = "Hello"),
                    role = Role.USER,
                )

            createUserMessageUseCase(message)

            assertEquals(1, messageRepository.messages().size)
        }
}
