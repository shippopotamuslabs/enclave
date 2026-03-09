package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CreateUserMessageUseCaseTest {

    private lateinit var fakeTransactionProvider: FakeTransactionProvider
    private lateinit var fakeMessageRepository: FakeMessageRepository
    private lateinit var fakeChatRepository: FakeChatRepository
    private lateinit var createUserMessageUseCase: CreateUserMessageUseCase

    @BeforeEach
    fun setup() {
        fakeTransactionProvider = FakeTransactionProvider()
        fakeMessageRepository = FakeMessageRepository()
        fakeChatRepository = FakeChatRepository()
        createUserMessageUseCase = CreateUserMessageUseCase(
            transactionProvider = fakeTransactionProvider,
            messageRepository = fakeMessageRepository,
            chatRepository = fakeChatRepository
        )
    }

    @Test
    fun `invoke saves message within transaction`() = runTest {
        // Given
        val message = Message(
            id = 1,
            chatId = 1,
            content = "Hello, world!",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(message)

        // Then
        fakeTransactionProvider.verifyTransactionCalled(1)
        fakeMessageRepository.verifySaveMessageCalled(1)
        fakeMessageRepository.verifyMessageSaved(message)
    }

    @Test
    fun `invoke handles user message correctly`() = runTest {
        // Given
        val userMessage = Message(
            id = 5,
            chatId = 1,
            content = "What is the weather?",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(userMessage)

        // Then
        fakeMessageRepository.verifyMessageSaved(userMessage)
        assertTrue(fakeMessageRepository.getSavedMessages().first().role == Role.USER)
    }

    @Test
    fun `invoke handles assistant message correctly`() = runTest {
        // Given
        val assistantMessage = Message(
            id = 6,
            chatId = 1,
            content = "The weather is sunny.",
            role = Role.ASSISTANT
        )

        // When
        createUserMessageUseCase(assistantMessage)

        // Then
        fakeMessageRepository.verifyMessageSaved(assistantMessage)
        assertFalse(fakeMessageRepository.getSavedMessages().first().role == Role.USER)
    }

    @Test
    fun `invoke propagates exception when transaction fails`() = runTest {
        // Given
        fakeTransactionProvider.setShouldThrowInTransaction(true)
        val message = Message(
            id = 1,
            chatId = 1,
            content = "Test message",
            role = Role.USER
        )

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { createUserMessageUseCase(message) }
        }
    }

    @Test
    fun `invoke propagates exception when repository fails`() = runTest {
        // Given
        fakeMessageRepository.shouldThrowOnSaveMessage = true
        val message = Message(
            id = 1,
            chatId = 1,
            content = "Test message",
            role = Role.USER
        )

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { createUserMessageUseCase(message) }
        }
    }

    @Test
    fun `invoke creates chat and updates message when id is negative`() = runTest {
        // Given - A message with id=-1 indicates it hasn't been assigned a valid chat ID
        // This should now create a new chat and update the message's chatId
        val orphanMessage = Message(
            id = -1,
            chatId = 1,
            content = "Orphan message without chat",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(orphanMessage)

        // Then - Verify chat was created with correct name (first 5 words)
        fakeChatRepository.verifyChatCreated(1)
        fakeChatRepository.verifyChatName("Orphan message without chat")

        // And verify message was saved with updated chatId
        fakeMessageRepository.verifySaveMessageCalled(1)
        val savedMessage = fakeMessageRepository.getSavedMessages().first()
        assertEquals(1L, savedMessage.chatId, "Message should have the new chat ID")
    }

    @Test
    fun `invoke generates correct chat name with more than 5 words`() = runTest {
        // Given - Message with more than 5 words
        val longMessage = Message(
            id = -1,
            chatId = 1,
            content = "This is a very long message with more than five words in it",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(longMessage)

        // Then - Verify chat name is first 5 words
        fakeChatRepository.verifyChatName("This is a very long")
    }

    @Test
    fun `invoke generates correct chat name with fewer than 5 words`() = runTest {
        // Given - Message with fewer than 5 words
        val shortMessage = Message(
            id = -1,
            chatId = 1,
            content = "Hello world",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(shortMessage)

        // Then - Verify chat name contains all available words
        fakeChatRepository.verifyChatName("Hello world")
    }

    @Test
    fun `invoke works with valid message id of zero`() = runTest {
        // Given - id=0 is valid for auto-generate (treated as existing message)
        val messageWithAutoId = Message(
            id = 0,
            chatId = 1,
            content = "Message with auto-generate ID",
            role = Role.USER
        )

        // When
        createUserMessageUseCase(messageWithAutoId)

        // Then
        fakeMessageRepository.verifySaveMessageCalled(1)
        // Chat should NOT be created for id=0 (only for id=-1)
        fakeChatRepository.verifyChatCreated(0)
    }

    @Test
    fun `invoke propagates exception when chat creation fails`() = runTest {
        // Given
        fakeChatRepository.shouldThrowOnCreateChat = true
        val orphanMessage = Message(
            id = -1,
            chatId = 1,
            content = "Test message",
            role = Role.USER
        )

        // When/Then
        assertThrows(RuntimeException::class.java) {
            runTest { createUserMessageUseCase(orphanMessage) }
        }
    }
}

