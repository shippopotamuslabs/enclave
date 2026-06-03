package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Chat
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.repository.TransactionProvider
import com.browntowndev.enclave.domain.usecase.FakeChatRepository
import com.browntowndev.enclave.domain.usecase.FakeMessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.Date

class RollbackPromptSubmissionUseCaseTest {
    @Test
    @DisplayName("DOPG-D-06 — existing chat rollback soft-deletes both messages")
    fun dopgD06_existingChatSoftDeletesBoth() =
        runTest {
            val chatId = ChatId("chat-rb-existing")
            val messageRepository = FakeMessageRepository()
            val chatRepository = FakeChatRepository()
            val transactionProvider = mockk<TransactionProvider>()
            coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
                val block = firstArg<suspend () -> Any>()
                block()
            }
            messageRepository.saveMessage(
                Message(
                    id = MessageId("user-rb-1"),
                    chatId = chatId,
                    content = Content(text = "hi"),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                ),
            )
            messageRepository.saveMessage(
                Message(
                    id = MessageId("asst-rb-1"),
                    chatId = chatId,
                    content = Content(text = ""),
                    role = Role.ASSISTANT,
                    messageState = MessageState.PROCESSING,
                ),
            )

            RollbackPromptSubmissionUseCase(messageRepository, chatRepository, transactionProvider)
                .invoke(
                    chatId = chatId,
                    userMessageId = MessageId("user-rb-1"),
                    assistantMessageId = MessageId("asst-rb-1"),
                    wasNewChat = false,
                )

            assertTrue(messageRepository.activeMessagesForChat(chatId).isEmpty())
            assertEquals(0, chatRepository.deletedChatIds.size)
        }

    @Test
    @DisplayName("DOPG-D-07 — new chat rollback soft-deletes messages then deletes chat cascade")
    fun dopgD07_newChatDeletesChat() =
        runTest {
            val chatId = ChatId("chat-rb-new")
            val userMessageId = MessageId("user-rb-1")
            val assistantMessageId = MessageId("asst-rb-1")
            val messageRepository = FakeMessageRepository()
            val chatRepository = FakeChatRepository()
            val transactionProvider = mockk<TransactionProvider>()
            coEvery { transactionProvider.runInTransaction<Any>(any()) } coAnswers {
                val block = firstArg<suspend () -> Any>()
                block()
            }
            chatRepository.createChat(
                Chat(id = chatId, name = "New", created = Date(), lastModified = Date()),
            )
            messageRepository.saveMessage(
                Message(
                    id = userMessageId,
                    chatId = chatId,
                    content = Content(text = "hi"),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                ),
            )
            messageRepository.saveMessage(
                Message(
                    id = assistantMessageId,
                    chatId = chatId,
                    content = Content(text = ""),
                    role = Role.ASSISTANT,
                    messageState = MessageState.PROCESSING,
                ),
            )

            RollbackPromptSubmissionUseCase(messageRepository, chatRepository, transactionProvider)
                .invoke(
                    chatId = chatId,
                    userMessageId = userMessageId,
                    assistantMessageId = assistantMessageId,
                    wasNewChat = true,
                )

            assertTrue(messageRepository.activeMessagesForChat(chatId).isEmpty())
            assertEquals(setOf(chatId.value), chatRepository.deletedChatIds)
            coVerify(exactly = 1) { transactionProvider.runInTransaction<Any>(any()) }
        }
}