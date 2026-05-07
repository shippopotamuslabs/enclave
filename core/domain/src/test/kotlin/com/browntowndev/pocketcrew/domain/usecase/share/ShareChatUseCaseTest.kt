package com.browntowndev.pocketcrew.domain.usecase.share

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.share.ShareOption
import com.browntowndev.pocketcrew.domain.model.share.SharedChat
import com.browntowndev.pocketcrew.domain.port.repository.ShareRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.model.share.SharedMessage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ShareChatUseCaseTest {

    private lateinit var shareRepository: ShareRepositoryPort
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var shareChatUseCase: ShareChatUseCase

    @BeforeEach
    fun setUp() {
        shareRepository = mockk()
        chatUseCases = mockk()
        shareChatUseCase = ShareChatUseCase(shareRepository, chatUseCases)
    }

    @Test
    fun `invoke with SingleTurn should share specific message pair`() = runTest {
        // Given
        val chatId = ChatId("chat-1")
        val assistantMessageId = MessageId("msg-2")
        val option = ShareOption.SingleTurn(chatId, assistantMessageId)
        
        val userMessage = createMessage("msg-1", chatId, Role.USER, "User message")
        val assistantMessage = createMessage("msg-2", chatId, Role.ASSISTANT, "Assistant response")
        val otherMessage = createMessage("msg-3", chatId, Role.USER, "Other message")
        
        coEvery { chatUseCases.getChat(chatId) } returns flowOf(listOf(userMessage, assistantMessage, otherMessage))
        
        val sharedMessages = listOf(mapToShared(userMessage), mapToShared(assistantMessage))
        val sharedChat = SharedChat("share-1", sharedMessages, 0, 0, "url")
        coEvery { shareRepository.shareChat(listOf(userMessage, assistantMessage)) } returns sharedChat

        // When
        val result = shareChatUseCase(option)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(sharedChat, result.getOrNull())
    }

    @Test
    fun `invoke with FullConversation should share all messages`() = runTest {
        // Given
        val chatId = ChatId("chat-1")
        val option = ShareOption.FullConversation(chatId)
        
        val messages = listOf(
            createMessage("msg-1", chatId, Role.USER, "Msg 1"),
            createMessage("msg-2", chatId, Role.ASSISTANT, "Msg 2")
        )
        
        coEvery { chatUseCases.getChat(chatId) } returns flowOf(messages)
        
        val sharedChat = SharedChat("share-1", messages.map(::mapToShared), 0, 0, "url")
        coEvery { shareRepository.shareChat(messages) } returns sharedChat

        // When
        val result = shareChatUseCase(option)

        // Then
        assertTrue(result.isSuccess)
        assertEquals(sharedChat, result.getOrNull())
    }

    @Test
    fun `invoke should return failure when repository fails`() = runTest {
        // Given
        val chatId = ChatId("chat-1")
        val option = ShareOption.FullConversation(chatId)
        val messages = listOf(createMessage("msg-1", chatId, Role.USER, "Msg 1"))
        
        coEvery { chatUseCases.getChat(chatId) } returns flowOf(messages)
        coEvery { shareRepository.shareChat(any()) } throws Exception("Upload failed")

        // When
        val result = shareChatUseCase(option)

        // Then
        assertTrue(result.isFailure)
        assertEquals("Upload failed", result.exceptionOrNull()?.message)
    }

    @Test
    fun `invoke should wait for messages if flow emits empty list initially`() = runTest {
        // Given
        val chatId = ChatId("chat-1")
        val option = ShareOption.FullConversation(chatId)
        val messages = listOf(createMessage("msg-1", chatId, Role.USER, "Msg 1"))
        
        // Flow that emits empty list first, then the populated list
        coEvery { chatUseCases.getChat(chatId) } returns flow {
            emit(emptyList())
            emit(messages)
        }
        
        val sharedChat = SharedChat("share-1", messages.map(::mapToShared), 0, 0, "url")
        coEvery { shareRepository.shareChat(messages) } returns sharedChat

        // When
        val result = shareChatUseCase(option)

        // Then
        assertTrue(result.isSuccess, "Should succeed even if first emission was empty")
        assertEquals(sharedChat, result.getOrNull())
    }

    private fun createMessage(id: String, chatId: ChatId, role: Role, text: String): Message {
        return Message(
            id = MessageId(id),
            chatId = chatId,
            content = Content(text = text),
            role = role
        )
    }

    private fun mapToShared(message: Message): SharedMessage {
        return SharedMessage(
            role = message.role.name,
            content = message.content.text,
            timestamp = 0L
        )
    }
}
