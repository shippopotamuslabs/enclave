package com.browntowndev.enclave.feature.chat.service

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatInferenceExecutorRouterTest {
    private lateinit var directExecutor: FakeDirectChatInferenceExecutor
    private lateinit var serviceExecutor: FakeChatInferenceServiceExecutor
    private lateinit var router: ChatInferenceExecutorRouter

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        directExecutor = FakeDirectChatInferenceExecutor()
        serviceExecutor =
            FakeChatInferenceServiceExecutor(
                FakeChatInferenceServiceStarter(ApplicationProvider.getApplicationContext(), FakeLoggingPort()),
                FakeLoggingPort(),
            )
        router = ChatInferenceExecutorRouter(directExecutor, serviceExecutor)
    }

    private fun testOverride() =
        ChatModelOverride.Api(
            configId = ApiModelConfigurationId("api-claude"),
            displayName = "Claude Sonnet",
        )

    private fun testConfig(override: ChatModelOverride.Api) =
        ActiveModelConfiguration(
            id = override.configId,
            isLocal = false,
            name = override.displayName,
            modelId = "claude-sonnet",
            systemPrompt = null,
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = true,
        )

    @Test
    fun `execute routes to serviceExecutor when backgroundInferenceEnabled is true`() {
        router.execute(
            prompt = "hi",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelType = ModelType.FAST,
            backgroundInferenceEnabled = true,
        )

        assertEquals(
            listOf(
                RecordedExecuteCall("hi", MessageId("u1"), MessageId("a1"), ChatId("c1"), false, ModelType.FAST, null, true),
            ),
            serviceExecutor.executeCalls,
        )
        assertTrue(directExecutor.executeCalls.isEmpty())
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
            backgroundInferenceEnabled = false,
        )

        assertEquals(
            listOf(
                RecordedExecuteCall("hi", MessageId("u1"), MessageId("a1"), ChatId("c1"), false, ModelType.FAST, null, false),
            ),
            directExecutor.executeCalls,
        )
        assertTrue(serviceExecutor.executeCalls.isEmpty())
    }

    @Test
    fun `executeWithOverride routes to serviceExecutor when backgroundInferenceEnabled is true`() {
        val override = testOverride()

        router.executeWithOverride(
            prompt = "hi",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelOverride = override,
            resolvedConfiguration = testConfig(override),
            backgroundInferenceEnabled = true,
        )

        assertEquals(
            listOf(
                RecordedExecuteCall("hi", MessageId("u1"), MessageId("a1"), ChatId("c1"), false, null, override, true),
            ),
            serviceExecutor.executeCalls,
        )
        assertTrue(directExecutor.executeCalls.isEmpty())
    }

    @Test
    fun `executeWithOverride routes to direct executor when backgroundInferenceEnabled is false`() {
        val override = testOverride()

        router.executeWithOverride(
            prompt = "hi",
            userMessageId = MessageId("u1"),
            assistantMessageId = MessageId("a1"),
            chatId = ChatId("c1"),
            userHasImage = false,
            modelOverride = override,
            resolvedConfiguration = testConfig(override),
            backgroundInferenceEnabled = false,
        )

        assertEquals(
            listOf(
                RecordedExecuteCall("hi", MessageId("u1"), MessageId("a1"), ChatId("c1"), false, null, override, false),
            ),
            directExecutor.executeCalls,
        )
        assertTrue(serviceExecutor.executeCalls.isEmpty())
    }

    @Test
    fun `stop calls both directExecutor and serviceExecutor stop`() {
        router.stop()

        assertEquals(1, directExecutor.stopCount)
        assertEquals(1, serviceExecutor.stopCount)
    }
}
