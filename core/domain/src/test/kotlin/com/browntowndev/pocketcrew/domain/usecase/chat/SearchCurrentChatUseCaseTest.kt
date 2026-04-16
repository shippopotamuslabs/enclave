package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SearchCurrentChatUseCaseTest {

    private lateinit var messageRepository: MessageRepository
    private lateinit var useCase: SearchCurrentChatUseCase

    @BeforeEach
    fun setup() {
        messageRepository = mockk()
        useCase = SearchCurrentChatUseCase(messageRepository)
    }

    @Test
    fun `invoke delegates to messageRepository searchMessagesInChat`() = runTest {
        val chatId = ChatId("chat-1")
        val query = "test query"
        val messages = listOf(
            Message(id = MessageId("m1"), chatId = chatId, content = Content(text = "test content"), role = Role.USER),
        )
        coEvery { messageRepository.searchMessagesInChat(chatId, query) } returns messages

        val result = useCase(chatId, query)

        assertEquals(messages, result)
        coVerify { messageRepository.searchMessagesInChat(chatId, query) }
    }

    @Test
    fun `invoke returns empty list when no messages match`() = runTest {
        val chatId = ChatId("chat-2")
        coEvery { messageRepository.searchMessagesInChat(chatId, "nonexistent") } returns emptyList()

        val result = useCase(chatId, "nonexistent")

        assertEquals(emptyList<Message>(), result)
    }
}
