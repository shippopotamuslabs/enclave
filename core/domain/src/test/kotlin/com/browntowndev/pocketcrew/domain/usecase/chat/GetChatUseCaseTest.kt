package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for GetChatUseCase.
 */
class GetChatUseCaseTest {

    private lateinit var chatRepository: ChatRepository
    private lateinit var getChatUseCase: GetChatUseCase

    @BeforeEach
    fun setUp() {
        chatRepository = mockk()
        getChatUseCase = GetChatUseCase(chatRepository)
    }

    @Test
    fun getChatUseCase_returnsFlowOfMessagesForChatId() = runTest {
        // Given
        val chatId = ChatId("1")
        val messages = listOf(
            Message(id = MessageId("1"), chatId = chatId, content = Content(text = "Hello"), role = Role.USER),
            Message(id = MessageId("2"), chatId = chatId, content = Content(text = "Hi there!"), role = Role.ASSISTANT)
        )
        val flow: Flow<List<Message>> = flowOf(messages)

        every { chatRepository.getMessagesForChat(chatId) } returns flow

        // When
        val result = getChatUseCase(chatId)

        // Then
        result.collect { emittedMessages ->
            assertEquals(2, emittedMessages.size)
            assertEquals("Hello", emittedMessages[0].content.text)
            assertEquals("Hi there!", emittedMessages[1].content.text)
        }
    }

    @Test
    fun getChatUseCase_returnsEmptyListWhenNoMessages() = runTest {
        // Given
        val chatId = ChatId("1")
        val flow: Flow<List<Message>> = flowOf(emptyList())

        every { chatRepository.getMessagesForChat(chatId) } returns flow

        // When
        val result = getChatUseCase(chatId)

        // Then
        result.collect { emittedMessages ->
            assertEquals(0, emittedMessages.size)
        }
    }
}
