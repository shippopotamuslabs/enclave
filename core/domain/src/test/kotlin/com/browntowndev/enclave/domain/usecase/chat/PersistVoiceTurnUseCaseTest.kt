package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class PersistVoiceTurnUseCaseTest {
    @Test
    @DisplayName("LV-PERSIST-001")
    fun lvPersist001_bothTurnsExistingChat() =
        runTest {
            val createUserMessageUseCase = mockk<CreateUserMessageUseCase>()
            val messageRepository = mockk<MessageRepository>()
            val userMessageSlot = slot<Message>()
            val assistantMessageSlot = slot<Message>()

            coEvery {
                createUserMessageUseCase(capture(userMessageSlot))
            } returns
                UserPromptResult(
                    userMessageId = MessageId("u1"),
                    chatId = ChatId("c1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000L,
                )
            coEvery { messageRepository.saveMessage(capture(assistantMessageSlot)) } returns MessageId("a1")

            val result =
                PersistVoiceTurnUseCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "hello there", "hi back")

            assertEquals(Role.USER, userMessageSlot.captured.role)
            assertEquals("hello there", userMessageSlot.captured.content.text)
            assertEquals(Role.ASSISTANT, assistantMessageSlot.captured.role)
            assertEquals("hi back", assistantMessageSlot.captured.content.text)
            assertEquals(ModelType.LIVE_VOICE, assistantMessageSlot.captured.modelType)
            assertEquals(ChatId("c1"), assistantMessageSlot.captured.chatId)
            assertEquals(1_700_000_000_001L, assistantMessageSlot.captured.createdAt)
            assertEquals(ChatId("c1"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-002")
    fun lvPersist002_chatCreatedOnDemand() =
        runTest {
            val createUserMessageUseCase = mockk<CreateUserMessageUseCase>()
            val messageRepository = mockk<MessageRepository>()
            val assistantMessageSlot = slot<Message>()

            coEvery { createUserMessageUseCase(any()) } returns
                UserPromptResult(
                    userMessageId = MessageId("u9"),
                    chatId = ChatId("new-chat"),
                    userMessageCreatedAtMillis = 0L,
                )
            coEvery { messageRepository.saveMessage(capture(assistantMessageSlot)) } returns MessageId("a1")

            val result =
                PersistVoiceTurnUseCase(createUserMessageUseCase, messageRepository)
                    .invoke(null, "start a session", "ready")

            assertEquals(ChatId("new-chat"), assistantMessageSlot.captured.chatId)
            assertEquals(ChatId("new-chat"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-003")
    fun lvPersist003_blankUserSeedsFromAssistant() =
        runTest {
            val createUserMessageUseCase = mockk<CreateUserMessageUseCase>()
            val messageRepository = mockk<MessageRepository>()
            val userMessageSlot = slot<Message>()
            val assistantMessageSlot = slot<Message>()

            coEvery { createUserMessageUseCase(capture(userMessageSlot)) } returns
                UserPromptResult(
                    userMessageId = MessageId("u2"),
                    chatId = ChatId("seed-chat"),
                    userMessageCreatedAtMillis = 0L,
                )
            coEvery { messageRepository.saveMessage(capture(assistantMessageSlot)) } returns MessageId("a1")

            val result =
                PersistVoiceTurnUseCase(createUserMessageUseCase, messageRepository)
                    .invoke(null, "   ", "the answer is 42")

            assertEquals("the answer is 42", userMessageSlot.captured.content.text)
            assertEquals("the answer is 42", assistantMessageSlot.captured.content.text)
            assertEquals(ChatId("seed-chat"), assistantMessageSlot.captured.chatId)
            assertEquals(ChatId("seed-chat"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-005")
    fun lvPersist005_assistantOnlyExistingChatDoesNotCreateUserRow() =
        runTest {
            val createUserMessageUseCase = mockk<CreateUserMessageUseCase>(relaxed = true)
            val messageRepository = mockk<MessageRepository>()
            val assistantMessageSlot = slot<Message>()

            coEvery { messageRepository.saveMessage(capture(assistantMessageSlot)) } returns MessageId("a1")

            val result =
                PersistVoiceTurnUseCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "", "assistant reply only")

            coVerify(exactly = 0) { createUserMessageUseCase(any()) }
            assertEquals(Role.ASSISTANT, assistantMessageSlot.captured.role)
            assertEquals("assistant reply only", assistantMessageSlot.captured.content.text)
            assertEquals(ChatId("c1"), assistantMessageSlot.captured.chatId)
            assertEquals(ChatId("c1"), result)
        }

    @Test
    @DisplayName("LV-PERSIST-004")
    fun lvPersist004_bothBlankPersistsNothing() =
        runTest {
            val createUserMessageUseCase = mockk<CreateUserMessageUseCase>(relaxed = true)
            val messageRepository = mockk<MessageRepository>(relaxed = true)

            val result =
                PersistVoiceTurnUseCase(createUserMessageUseCase, messageRepository)
                    .invoke(ChatId("c1"), "", "   ")

            coVerify(exactly = 0) { createUserMessageUseCase(any()) }
            coVerify(exactly = 0) { messageRepository.saveMessage(any()) }
            assertEquals(ChatId("c1"), result)
        }
}