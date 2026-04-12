package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceService
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatHistoryRehydratorTest {

    @Test
    fun `rehydrator excludes active messages and formats saved vision analyses`() = runTest {
        val priorUser = Message(
            id = MessageId("prior-user"),
            chatId = ChatId("chat"),
            content = Content(text = "", imageUri = "file:///prior.jpg"),
            role = Role.USER,
        )
        val priorAssistant = Message(
            id = MessageId("prior-assistant"),
            chatId = ChatId("chat"),
            content = Content(text = "Previous answer"),
            role = Role.ASSISTANT,
        )
        val currentUser = Message(
            id = MessageId("current-user"),
            chatId = ChatId("chat"),
            content = Content(text = "Newest question"),
            role = Role.USER,
        )
        val activeAssistant = Message(
            id = MessageId("active-assistant"),
            chatId = ChatId("chat"),
            content = Content(text = ""),
            role = Role.ASSISTANT,
        )
        val analysis = MessageVisionAnalysis(
            id = "analysis-1",
            userMessageId = priorUser.id,
            imageUri = "file:///prior.jpg",
            promptText = "",
            analysisText = "A cracked pipe joint under the sink.",
            modelType = ModelType.VISION,
            createdAt = 1L,
            updatedAt = 1L,
        )
        val repository = mockk<MessageRepository> {
            coEvery { getMessagesForChat(ChatId("chat")) } returns listOf(
                priorUser,
                priorAssistant,
                currentUser,
                activeAssistant,
            )
            coEvery { getVisionAnalysesForMessages(any()) } returns mapOf(priorUser.id to listOf(analysis))
        }
        val service = FakeInferenceService()
        val rehydrator = ChatHistoryRehydrator(
            messageRepository = repository,
            loggingPort = mockk<LoggingPort>(relaxed = true),
        )

        rehydrator(
            chatId = ChatId("chat"),
            userMessageId = currentUser.id,
            assistantMessageId = activeAssistant.id,
            service = service,
        )

        val history = service.getHistory()
        assertEquals(2, history.size)
        assertTrue(history.first().content.contains("A cracked pipe joint under the sink."))
        assertFalse(history.first().content.contains("[User attached an image]"))
        assertEquals("Previous answer", history[1].content)
    }

    @Test
    fun `rehydrator falls back to image placeholder when no vision analysis exists`() = runTest {
        val priorUser = Message(
            id = MessageId("prior-user"),
            chatId = ChatId("chat"),
            content = Content(text = "", imageUri = "file:///prior.jpg"),
            role = Role.USER,
        )
        val repository = mockk<MessageRepository> {
            coEvery { getMessagesForChat(any()) } returns listOf(priorUser)
            coEvery { getVisionAnalysesForMessages(any()) } returns emptyMap()
        }
        val service = FakeInferenceService()
        val rehydrator = ChatHistoryRehydrator(
            messageRepository = repository,
            loggingPort = mockk<LoggingPort>(relaxed = true),
        )

        rehydrator(
            chatId = ChatId("chat"),
            userMessageId = MessageId("current-user"),
            assistantMessageId = MessageId("active-assistant"),
            service = service,
        )

        assertEquals("[User attached an image]", service.getHistory().single().content)
    }
}
