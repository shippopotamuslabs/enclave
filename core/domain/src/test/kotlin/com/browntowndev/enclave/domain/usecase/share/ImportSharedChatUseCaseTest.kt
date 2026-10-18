package com.browntowndev.enclave.domain.usecase.share

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.model.share.SharedMessage
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ImportSharedChatUseCaseTest {
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var useCase: ImportSharedChatUseCase

    @BeforeEach
    fun setUp() {
        chatRepository = FakeChatRepository()
        messageRepository = FakeMessageRepository()
        useCase = ImportSharedChatUseCase(chatRepository, messageRepository)
    }

    @Test
    fun `returns sourceChatId when local messages exist for that chat`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val existingMessage =
                Message(id = MessageId("m1"), chatId = sourceChatId, role = Role.USER, content = Content(text = "hi"))
            messageRepository.seed(listOf(existingMessage))

            val sharedChat = sharedChat(sourceChatId = sourceChatId.value)

            val result = useCase(sharedChat)

            assertEquals(sourceChatId, result)
            assertTrue(chatRepository.chats().isEmpty())
        }

    @Test
    fun `creates new chat when sourceChatId is null`() =
        runTest {
            val newChatId = ChatId("new-chat")
            chatRepository.createChatResultOverride = newChatId

            val sharedChat = sharedChat(sourceChatId = null)

            val result = useCase(sharedChat)

            assertEquals(newChatId, result)
            assertEquals(1, chatRepository.chats().size)
        }

    @Test
    fun `creates new chat when sourceChatId set but no local messages`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val newChatId = ChatId("new-chat")
            chatRepository.createChatResultOverride = newChatId

            val sharedChat = sharedChat(sourceChatId = sourceChatId.value)

            val result = useCase(sharedChat)

            assertEquals(newChatId, result)
            assertEquals(1, chatRepository.chats().size)
        }

    @Test
    fun `saves all shared messages when creating new chat`() =
        runTest {
            val newChatId = ChatId("new-chat")
            chatRepository.createChatResultOverride = newChatId

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

            val savedMessages = messageRepository.messages()
            assertEquals(2, savedMessages.size)
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
            chatRepository.createChatResultOverride = newChatId

            val sharedChat =
                SharedChat(
                    id = "share-1",
                    messages = listOf(SharedMessage(role = "user", content = "Hello", timestamp = 0L)),
                    createdAt = 0,
                    expiresAt = 0,
                    shareUrl = "",
                )

            useCase(sharedChat)

            assertEquals(newChatId, messageRepository.messages().single().chatId)
        }

    @Test
    fun `new chat is named Imported Chat`() =
        runTest {
            chatRepository.createChatResultOverride = ChatId("new")

            useCase(sharedChat(sourceChatId = null))

            assertEquals("Imported Chat", chatRepository.chats().single().name)
        }

    @Test
    fun `does not save messages when returning owners existing chat`() =
        runTest {
            val sourceChatId = ChatId("original-chat")
            val existingMessage =
                Message(id = MessageId("m1"), chatId = sourceChatId, role = Role.USER, content = Content(text = "hi"))
            messageRepository.seed(listOf(existingMessage))

            useCase(sharedChat(sourceChatId = sourceChatId.value))

            assertEquals(listOf(existingMessage), messageRepository.messages())
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
