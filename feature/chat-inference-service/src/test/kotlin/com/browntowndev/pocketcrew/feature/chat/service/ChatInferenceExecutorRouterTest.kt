package com.browntowndev.pocketcrew.feature.chat.service

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.chat.DirectChatInferenceExecutor
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ChatInferenceExecutorRouterTest {
    private val directExecutor = mockk<DirectChatInferenceExecutor>(relaxed = true)
    private val serviceExecutor = mockk<ChatInferenceServiceExecutor>(relaxed = true)
    private val router = ChatInferenceExecutorRouter(directExecutor, serviceExecutor)

    @Test
    fun `execute routes to serviceExecutor when backgroundInferenceEnabled is true`() {
        router.execute(
            prompt = "hi",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = true
        )

        verify { serviceExecutor.execute(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { directExecutor.execute(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `execute routes to direct executor when backgroundInferenceEnabled is false`() {
        router.execute(
            prompt = "hi",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = false
        )

        verify { directExecutor.execute(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 0) { serviceExecutor.execute(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `stop calls both directExecutor and serviceExecutor stop`() {
        router.stop()
        verify { directExecutor.stop() }
        verify { serviceExecutor.stop() }
    }
}