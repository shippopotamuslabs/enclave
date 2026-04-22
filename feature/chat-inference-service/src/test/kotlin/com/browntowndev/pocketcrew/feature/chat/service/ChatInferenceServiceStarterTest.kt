package com.browntowndev.pocketcrew.feature.chat.service

import android.content.ComponentName
import android.content.Context
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ChatInferenceServiceStarterTest {

    private val context = mockk<Context>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val starter = ChatInferenceServiceStarter(context, loggingPort)

    @Test
    fun `startService logs and starts foreground service`() {
        every { context.startForegroundService(any()) } returns mockk<ComponentName>(relaxed = true)

        starter.startService(
            prompt = "hello",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("chat-1"),
            userHasImage = true,
            modelType = ModelType.THINKING,
        )

        verify(exactly = 1) {
            loggingPort.info(
                "ChatInferenceStarter",
                match { it.contains("startService requested") && it.contains("chat=chat-1") && it.contains("assistantMessageId=a1") },
            )
        }
        verify(exactly = 1) {
            loggingPort.debug(
                "ChatInferenceStarter",
                match { it.contains("startForegroundService intent prepared") && it.contains("chat=chat-1") },
            )
        }
        verify(exactly = 1) { context.startForegroundService(any()) }
        verify(exactly = 1) {
            loggingPort.info(
                "ChatInferenceStarter",
                match { it.contains("startForegroundService invoked") && it.contains("chat=chat-1") },
            )
        }
    }
}
