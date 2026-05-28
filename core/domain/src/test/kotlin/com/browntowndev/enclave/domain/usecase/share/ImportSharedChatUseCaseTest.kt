package com.browntowndev.enclave.domain.usecase.share

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.model.share.SharedMessage
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportSharedChatUseCaseTest {
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var useCase: ImportSharedChatUseCase

    @BeforeEach
    fun setUp() {
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        useCase = ImportSharedChatUseCase(chatRepository, messageRepository)
    }

    @Test
    fun `returns sourceChatId when local messages exist for that chat`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val existingMessage =
                Message(id = MessageId("m1"), chatId = sourceChatId, role = Role.USER, content = Content(text = "hi"))
            coEvery { messageRepository.getMessagesForChat(sourceChatId) } returns listOf(existingMessage)

            val sharedChat = sharedChat(sourceChatId = sourceChatId.value)

            val result = useCase(sharedChat)

            assertEquals(sourceChatId, result)
            coVerify(exactly = 0) { chatRepository.createChat(any()) }
        }

    @Test
    fun `creates new chat when sourceChatId is null`() =
        runTest {
            val newChatId = ChatId("new-chat")
            coEvery { chatRepository.createChat(any()) } returns newChatId

            val sharedChat = sharedChat(sourceChatId = null)

            val result = useCase(sharedChat)

            assertEquals(newChatId, result)
            coVerify { chatRepository.createChat(any()) }
        }

    @Test
    fun `creates new chat when sourceChatId set but no local messages`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val newChatId = ChatId("new-chat")
            coEvery { messageRepository.getMessagesForChat(sourceChatId) } returns emptyList()
            coEvery { chatRepository.createChat(any()) } returns newChatId

            val sharedChat = sharedChat(sourceChatId = sourceChatId.value)

            val result = useCase(sharedChat)

            assertEquals(newChatId, result)
            coVerify { chatRepository.createChat(any()) }
        }

    @Test
    fun `saves all shared messages when creating new chat`() =
        runTest {
            val newChatId = ChatId("new-chat")
            coEvery { chatRepository.createChat(any()) } returns newChatId

            val sharedChat =
                SharedChat(
                    id = "share-1",
                    messages =
                        listOf(
                            SharedMessage(role = "user", content = "Hello", timestamp = 100L),
                            SharedMessage(role = "assistant", content = "Hi there", timestamp = 200L),
                        ),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "",
                )

            useCase(sharedChat)

            val savedMessages = mutableListOf<Message>()
            coVerify(exactly = 2) { messageRepository.saveMessage(capture(savedMessages)) }
            assertEquals(Role.USER, savedMessages[0].role)
            assertEquals("Hello", savedMessages[0].content.text)
            assertEquals(100L, savedMessages[0].createdAt)
            assertEquals(MessageState.COMPLETE, savedMessages[0].messageState)
            assertEquals(Role.ASSISTANT, savedMessages[1].role)
            assertEquals("Hi there", savedMessages[1].content.text)
            assertEquals(200L, savedMessages[1].createdAt)
        }

    @Test
    fun `saved messages have the new chatId`() =
        runTest {
            val newChatId = ChatId("new-chat")
            coEvery { chatRepository.createChat(any()) } returns newChatId

            val sharedChat =
                SharedChat(
                    id = "share-1",
                    messages = listOf(SharedMessage(role = "user", content = "Hello", timestamp = 0L)),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "",
                )

            useCase(sharedChat)

            val messageSlot = slot<Message>()
            coVerify { messageRepository.saveMessage(capture(messageSlot)) }
            assertEquals(newChatId, messageSlot.captured.chatId)
        }

    @Test
    fun `new chat is named Imported Chat`() =
        runTest {
            val chatSlot = slot<Chat>()
            coEvery { chatRepository.createChat(capture(chatSlot)) } returns ChatId("new")

            useCase(sharedChat(sourceChatId = null))

            assertEquals("Imported Chat", chatSlot.captured.name)
        }

    @Test
    fun `does not save messages when returning owners existing chat`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val existingMessage =
                Message(id = MessageId("m1"), chatId = sourceChatId, role = Role.USER, content = Content(text = "hi"))
            coEvery { messageRepository.getMessagesForChat(sourceChatId) } returns listOf(existingMessage)

            useCase(sharedChat(sourceChatId = sourceChatId.value))

            coVerify(exactly = 0) { messageRepository.saveMessage(any()) }
        }

    private fun sharedChat(sourceChatId: String? = null) =
        SharedChat(
            id = "share-1",
            messages = listOf(SharedMessage(role = "user", content = "Hello", timestamp = 0L)),
            createdAt = 0,
            expiresAt = 0,
            shareUrl = "",
            sourceChatId = sourceChatId,
        )
}
