package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class CreateAssistantPlaceholderUseCaseTest {
    private lateinit var messageRepository: MessageRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var useCase: CreateAssistantPlaceholderUseCase

    @BeforeEach
    fun setUp() {
        messageRepository = mockk()
        chatRepository = mockk()
        useCase = CreateAssistantPlaceholderUseCase(messageRepository, chatRepository)
    }

    @Test
    @DisplayName("OSDA-D-05 — placeholder is empty assistant after user timestamp")
    fun osdaD05_placeholderShape() =
        runTest {
            val savedSlot = slot<com.browntowndev.enclave.domain.model.chat.Message>()
            coEvery { messageRepository.saveMessage(capture(savedSlot)) } returns MessageId("asst-pre-1")

            val result =
                useCase.invoke(
                    chatId = ChatId("chat-1"),
                    userMessageCreatedAtMillis = 1_700_000_000_000,
                    assistantMessageId = MessageId("asst-pre-1"),
                )

            assertEquals(Role.ASSISTANT, savedSlot.captured.role)
            assertEquals("", savedSlot.captured.content.text)
            assertEquals(1_700_000_000_001, savedSlot.captured.createdAt)
            assertEquals(MessageId("asst-pre-1"), result.assistantMessageId)
        }

    @Test
    @DisplayName("OSDA-D-06 — saveMessage uses orchestrator-provided id")
    fun osdaD06_callerSuppliedId() =
        runTest {
            val savedSlot = slot<com.browntowndev.enclave.domain.model.chat.Message>()
            coEvery { messageRepository.saveMessage(capture(savedSlot)) } returns MessageId("fixed-asst-id")

            useCase.invoke(
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 1_700_000_000_000,
                assistantMessageId = MessageId("fixed-asst-id"),
            )

            assertEquals(MessageId("fixed-asst-id"), savedSlot.captured.id)
        }

    @Test
    @DisplayName("OSDA-D-07 — assistant save never creates chat")
    fun osdaD07_noChatCreation() =
        runTest {
            coEvery { messageRepository.saveMessage(any()) } returns MessageId("asst-pre-1")

            useCase.invoke(
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 1_700_000_000_000,
                assistantMessageId = MessageId("asst-pre-1"),
            )

            coVerify(exactly = 0) { chatRepository.createChat(any()) }
        }
}
