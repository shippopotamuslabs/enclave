package com.browntowndev.pocketcrew.domain.usecase.chat

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.Message
import com.browntowndev.pocketcrew.domain.model.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import com.browntowndev.pocketcrew.domain.usecase.FakeChatRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeMessageRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeTransactionProvider
import io.mockk.MockKAnnotations
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CreateUserMessageUseCaseTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeTransactionProvider: FakeTransactionProvider
    private lateinit var fakeMessageRepository: FakeMessageRepository
    private lateinit var fakeChatRepository: FakeChatRepository

    private lateinit var useCase: CreateUserMessageUseCase

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>()) } returns 0

        Dispatchers.setMain(testDispatcher)

        // Use Fake implementations that actually execute the lambdas
        fakeTransactionProvider = FakeTransactionProvider()
        fakeMessageRepository = FakeMessageRepository()
        fakeChatRepository = FakeChatRepository()

        useCase = CreateUserMessageUseCase(
            transactionProvider = fakeTransactionProvider,
            messageRepository = fakeMessageRepository,
            chatRepository = fakeChatRepository
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    @Test
    fun `invoke saves message for existing chat`() = runTest {
        // Given
        val message = Message(
            id = 1L, // Existing chat
            chatId = 100L,
            content = "Hello",
            role = Role.USER
        )

        // When
        useCase(message)

        // Then - verify message was saved
        assert(fakeMessageRepository.getSavedMessages().any { 
            it.id == message.id && it.content == message.content 
        })
        // Verify no new chat was created
        assert(fakeChatRepository.getCreatedChats().isEmpty())
    }

    @Test
    fun `invoke creates new chat when message id is negative one`() = runTest {
        // Given
        val message = Message(
            id = -1L, // New chat indicator
            chatId = -1L,
            content = "Hello world test message",
            role = Role.USER
        )

        // When
        useCase(message)

        // Then - verify chat was created with correct name
        assert(fakeChatRepository.getCreatedChats().any { 
            it.name.contains("Hello") && it.name.contains("world")
        })
    }

    @Test
    fun `invoke generates chat name from first five words`() = runTest {
        // Given
        val message = Message(
            id = -1L,
            chatId = -1L,
            content = "One two three four five six seven eight", // 8 words
            role = Role.USER
        )

        // When
        useCase(message)

        // Then
        assert(fakeChatRepository.getCreatedChats().any { 
            it.name == "One two three four five"
        })
    }

    @Test
    fun `invoke handles short message content`() = runTest {
        // Given
        val message = Message(
            id = -1L,
            chatId = -1L,
            content = "Hi", // Only 1 word
            role = Role.USER
        )

        // When
        useCase(message)

        // Then
        assert(fakeChatRepository.getCreatedChats().any { 
            it.name == "Hi"
        })
    }

    @Test
    fun `invoke updates message chatId when creating new chat`() = runTest {
        // Given
        val message = Message(
            id = -1L,
            chatId = -1L,
            content = "Test message",
            role = Role.USER
        )

        // When
        useCase(message)

        // Then - verify message is saved with updated chatId
        val createdChatId = fakeChatRepository.getCreatedChats().firstOrNull()?.id
        assert(createdChatId != null)
        assert(fakeMessageRepository.getSavedMessages().any { 
            it.chatId == createdChatId
        })
    }

    @Test
    fun `invoke creates unpinned chat`() = runTest {
        // Given
        val message = Message(
            id = -1L,
            chatId = -1L,
            content = "Test",
            role = Role.USER
        )

        // When
        useCase(message)

        // Then
        assert(fakeChatRepository.getCreatedChats().any { 
            it.pinned == false
        })
    }

    @Test
    fun `invoke does not create chat for assistant message`() = runTest {
        // Given
        val message = Message(
            id = 1L,
            chatId = 100L,
            content = "Assistant response",
            role = Role.ASSISTANT
        )

        // When
        useCase(message)

        // Then
        assert(fakeChatRepository.getCreatedChats().isEmpty())
        assert(fakeMessageRepository.getSavedMessages().any { 
            it.content == message.content
        })
    }
}
