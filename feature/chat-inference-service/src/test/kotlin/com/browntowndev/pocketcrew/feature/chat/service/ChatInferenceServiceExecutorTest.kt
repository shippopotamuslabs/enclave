package com.browntowndev.pocketcrew.feature.chat.service

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatInferenceServiceExecutorTest {

    private val serviceStarter = mockk<ChatInferenceServiceStarter>(relaxed = true)
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val executor = ChatInferenceServiceExecutor(serviceStarter, loggingPort)

    @Test
    fun `execute emits no states when the service starts successfully`() = runTest {
        val results = executor.execute(
            prompt = "hello",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("chat-1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = true,
        ).toList()

        assertTrue(results.isEmpty())
        verify(exactly = 1) {
            serviceStarter.startService(
                prompt = "hello",
                userMessageId = MessageId("u1"),
                assistantMessageId = MessageId("a1"),
                chatId = ChatId("chat-1"),
                userHasImage = false,
                modelType = ModelType.FAST,
            )
        }
    }

    @Test
    fun `execute emits Failed when service start is rejected`() = runTest {
        every {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        } throws IllegalStateException("foreground service start not allowed")

        val results = executor.execute(
            prompt = "hello",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("chat-1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = true,
        ).toList()

        assertEquals(1, results.size)
        assertTrue(results.single() is MessageGenerationState.Failed)
        verify(exactly = 1) {
            serviceStarter.startService(any(), any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `stop calls serviceStarter stopInference`() {
        executor.stop()
        verify { serviceStarter.stopInference() }
    }
}
