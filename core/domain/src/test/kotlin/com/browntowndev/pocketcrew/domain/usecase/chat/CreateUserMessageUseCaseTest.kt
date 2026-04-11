package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Date

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

    private lateinit var transactionProvider: TransactionProvider
    private lateinit var messageRepository: MessageRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var createUserMessageUseCase: CreateUserMessageUseCase

    @BeforeEach
    fun setUp() {
        transactionProvider = mockk()
        messageRepository = mockk()
        chatRepository = mockk()
        createUserMessageUseCase = CreateUserMessageUseCase(transactionProvider, messageRepository, chatRepository)
    }

    // ========================================================================
    // Test: New Chat Creation with Empty Content
    // Evidence: Empty content should return "New Chat" as default name
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `empty content returns New Chat as default name`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers {
            ChatId("42")
        }

        // Track calls to return correct IDs based on call order
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""), // Triggers new chat creation
            content = Content(text = ""),
            role = Role.USER
        )

        // When
        val result = createUserMessageUseCase(message)

        // Then
        assertEquals("New Chat", chatSlot.captured.name)
        assertEquals(MessageId("1"), result.userMessageId)
        assertEquals(MessageId("2"), result.assistantMessageId)
        assertEquals(chatSlot.captured.id, result.chatId)
    }

    // ========================================================================
    // Test: Chat Name Generation - Single Word
    // Evidence: Single word should be returned as-is
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `single word returns that word`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "Hello"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("Hello", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Chat Name Generation - Exactly 5 Words
    // Evidence: All 5 words should be returned
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `five words returns all five`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "The quick brown fox jumps"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("The quick brown fox jumps", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Chat Name Generation - More Than 5 Words
    // Evidence: Only first 5 words should be returned
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `more than five words returns first five`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "The quick brown fox jumps over the lazy dog"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("The quick brown fox jumps", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Chat Name Generation - Long String With No Spaces (>30 chars)
    // Evidence: Should truncate to first 30 chars with ellipsis
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `no words but long string truncates with ellipsis`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        // "aaaaaaaaa..." - 35 chars with no spaces
        val longString = "a".repeat(35)
        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = longString),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("a".repeat(30) + "...", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Chat Name Generation - Special Characters
    // Evidence: Special characters should be included as-is
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `special characters are included in chat name`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "Hello @user! How's #testing?"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("Hello @user! How's #testing?", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Chat Name Generation - Numbers Only
    // Evidence: Numbers-only should be allowed as chat name
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `numbers only are allowed as chat name`() = runTest {
        // Given
        val chatSlot = slot<Chat>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(capture(chatSlot)) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "12345"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals("12345", chatSlot.captured.name)
    }

    // ========================================================================
    // Test: Existing Chat Continuation
    // Evidence: Message with existing chatId should use that chat
    // REF: CLARIFIED_REQUIREMENTS.md - Section 2
    // ========================================================================

    @Test
    fun `existing chat id uses that chat without creating new one`() = runTest {
        // Given
        val existingChatId = ChatId("99")
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("10") else MessageId("11")
        }

        val message = Message(
            id = MessageId(""),
            chatId = existingChatId, // Existing chat
            content = Content(text = "Continuing conversation"),
            role = Role.USER
        )

        // When
        val result = createUserMessageUseCase(message)

        // Then
        assertEquals(MessageId("10"), result.userMessageId)
        assertEquals(MessageId("11"), result.assistantMessageId)
        assertEquals(existingChatId, result.chatId)
        // Should NOT create a new chat
        coVerify(exactly = 0) { chatRepository.createChat(any()) }
    }

    // ========================================================================
    // Test: Assistant Placeholder Created
    // Evidence: Assistant message should be created with empty content
    // ========================================================================

    @Test
    fun `assistant placeholder message is created with empty content`() = runTest {
        // Given
        val assistantMessageSlot = slot<Message>()
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(any()) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }
        coEvery { messageRepository.saveMessage(capture(assistantMessageSlot)) } coAnswers { MessageId("2") }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "Hello"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        assertEquals(Role.ASSISTANT, assistantMessageSlot.captured.role)
        assertEquals("", assistantMessageSlot.captured.content.text)
    }

    // ========================================================================
    // Test: Transaction Ensures Atomicity
    // Evidence: All operations wrapped in transaction
    // ========================================================================

    @Test
    fun `all operations wrapped in transaction`() = runTest {
        // Given
        var callCount = 0

        coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
            val lambda = firstArg<suspend () -> Any>()
            lambda()
        }

        coEvery { chatRepository.createChat(any()) } coAnswers { ChatId("1") }
        coEvery { messageRepository.saveMessage(any<Message>()) } coAnswers {
            callCount++
            if (callCount == 1) MessageId("1") else MessageId("2")
        }

        val message = Message(
            id = MessageId(""),
            chatId = ChatId(""),
            content = Content(text = "Test"),
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then - verify transaction was used
        coVerify {
            transactionProvider.runInTransaction<Any>(any())
        }
    }
}
