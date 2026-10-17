package com.browntowndev.enclave.feature.chat.service

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatInferenceServiceStarterTest {
    private lateinit var application: android.app.Application
    private val loggingPort = FakeLoggingPort()
    private lateinit var starter: ChatInferenceServiceStarter

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        application = RuntimeEnvironment.getApplication()
        starter = ChatInferenceServiceStarter(application, loggingPort)
    }

    @Test
    fun `startService logs and starts foreground service`() {
        starter.startService(
            prompt = "hello",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("chat-1"),
            userHasImage = true,
            modelType = ModelType.THINKING,
        )

        assertEquals(
            1,
            loggingPort.infoCalls.count {
                it.first == "ChatInferenceStarter" &&
                    it.second.contains("startService requested") &&
                    it.second.contains("chat=chat-1") &&
                    it.second.contains("assistantMessageId=a1")
            },
        )
        assertEquals(
            1,
            loggingPort.debugCalls.count {
                it.first == "ChatInferenceStarter" &&
                    it.second.contains("startForegroundService intent prepared") &&
                    it.second.contains("chat=chat-1")
            },
        )
        assertEquals(
            1,
            loggingPort.infoCalls.count {
                it.first == "ChatInferenceStarter" &&
                    it.second.contains("startForegroundService invoked") &&
                    it.second.contains("chat=chat-1")
            },
        )

        val startedServices = shadowOf(application).allStartedServices
        assertEquals(1, startedServices.size)
        val intent = startedServices.single()
        assertEquals(ChatInferenceService.ACTION_START, intent.action)
        assertEquals("chat-1", intent.getStringExtra(ChatInferenceService.EXTRA_CHAT_ID))
        assertEquals("hello", intent.getStringExtra(ChatInferenceService.EXTRA_PROMPT))
        assertTrue(intent.getBooleanExtra(ChatInferenceService.EXTRA_USER_HAS_IMAGE, false))
    }
}
