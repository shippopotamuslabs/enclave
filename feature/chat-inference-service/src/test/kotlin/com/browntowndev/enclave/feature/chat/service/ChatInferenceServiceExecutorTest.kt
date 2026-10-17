package com.browntowndev.enclave.feature.chat.service

import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class ChatInferenceServiceExecutorTest {
    private lateinit var serviceStarter: FakeChatInferenceServiceStarter
    private val loggingPort = FakeLoggingPort()
    private lateinit var executor: ChatInferenceServiceExecutor

    @org.junit.jupiter.api.BeforeEach
    fun setUp() {
        serviceStarter =
            FakeChatInferenceServiceStarter(ApplicationProvider.getApplicationContext(), FakeLoggingPort())
        executor = ChatInferenceServiceExecutor(serviceStarter, loggingPort)
    }

    @Test
    fun `execute emits no states when the service starts successfully`() =
        runTest {
            val results =
                executor
                    .execute(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("chat-1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        backgroundInferenceEnabled = true,
                    ).toList()

            assertTrue(results.isEmpty())
            assertEquals(
                listOf(
                    FakeChatInferenceServiceStarter.StartCall(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("chat-1"),
                        userHasImage = false,
                        modelType = ModelType.FAST,
                        modelOverride = null,
                    ),
                ),
                serviceStarter.startCalls,
            )
        }

    @Test
    fun `execute emits Failed when service start is rejected`() =
        runTest {
            serviceStarter.startFailure = IllegalStateException("foreground service start not allowed")

            val results =
                executor
                    .execute(
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
            assertEquals(1, serviceStarter.startCalls.size)
        }

    @Test
    fun `executeWithOverride emits no states when the service starts successfully`() =
        runTest {
            val override =
                ChatModelOverride.Api(
                    configId = ApiModelConfigurationId("api-claude"),
                    displayName = "Claude Sonnet",
                )
            val config =
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

            val results =
                executor
                    .executeWithOverride(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("chat-1"),
                        userHasImage = false,
                        modelOverride = override,
                        resolvedConfiguration = config,
                        backgroundInferenceEnabled = true,
                    ).toList()

            assertTrue(results.isEmpty())
            assertEquals(
                listOf(
                    FakeChatInferenceServiceStarter.StartCall(
                        prompt = "hello",
                        userMessageId = MessageId("u1"),
                        assistantMessageId = MessageId("a1"),
                        chatId = ChatId("chat-1"),
                        userHasImage = false,
                        modelType = null,
                        modelOverride = override,
                    ),
                ),
                serviceStarter.startCalls,
            )
        }

    @Test
    fun `stop calls serviceStarter stopInference`() {
        executor.stop()

        assertEquals(1, serviceStarter.stopInferenceCount)
    }
}
