package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateAssistantPlaceholderUseCaseTest {
    private lateinit var messageRepository: FakeMessageRepository
    private lateinit var chatRepository: FakeChatRepository
    private lateinit var useCase: CreateAssistantPlaceholderUseCase

    @BeforeEach
    fun setUp() {
        messageRepository = FakeMessageRepository()
        chatRepository = FakeChatRepository()
        useCase = CreateAssistantPlaceholderUseCase(messageRepository, chatRepository)
    }

    @Test
    @DisplayName("OSDA-D-05 — placeholder is empty assistant after user timestamp")
    fun osdaD05_placeholderShape() =
        runTest {
            val result =
                useCase.invoke(
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000,
                    assistantMessageId = MessageId("asst-pre-1"),
                )

            val saved = messageRepository.messages().single()
            assertEquals(Role.ASSISTANT, saved.role)
            assertEquals("", saved.content.text)
            assertEquals(1_700_000_000_001, saved.createdAt)
            assertEquals(MessageId("asst-pre-1"), result.assistantMessageId)
        }

    @Test
    @DisplayName("OSDA-D-06 — saveMessage uses orchestrator-provided id")
    fun osdaD06_callerSuppliedId() =
        runTest {
            useCase.invoke(
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 1_700_000_000_000,
                assistantMessageId = MessageId("fixed-asst-id"),
            )

            assertEquals(MessageId("fixed-asst-id"), messageRepository.messages().single().id)
        }

    @Test
    @DisplayName("OSDA-D-07 — assistant save never creates chat")
    fun osdaD07_noChatCreation() =
        runTest {
            useCase.invoke(
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 1_700_000_000_000,
                assistantMessageId = MessageId("asst-pre-1"),
            )

            assertTrue(chatRepository.chats().isEmpty())
        }
}
