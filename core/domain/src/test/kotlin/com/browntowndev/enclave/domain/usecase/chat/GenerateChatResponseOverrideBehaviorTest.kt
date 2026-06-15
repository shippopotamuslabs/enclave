package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ChatInferenceExecutorPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.usecase.FakeInferenceLockManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Behavioral MD01 tests: verifies that [ChatResponseMode.Other] resolves the
 * explicit override config and threads it to [ChatInferenceExecutorPort.executeWithOverride],
 * and that the FAST slot assignment is never consulted for resolution.
 */
class GenerateChatResponseOverrideBehaviorTest {
    private val noOpTracker =
        object : ExtractedUrlTrackerPort {
            override val urls: Set<String> get() = emptySet()

            override fun add(url: String) {}

            override fun clear() {}
        }

    @Nested
    @DisplayName("MD01 — API override resolves explicit config, never FAST slot")
    inner class ApiOverride {
        @Test
        @DisplayName("MD01 — Other(API override) threads 'Grok Beta' config to executeWithOverride")
        fun md01_apiOverrideThreadedToExecutor() =
            runTest {
                val fastSlotConfig = activeApiConfig(name = "Fast Slot Model", id = ApiModelConfigurationId("fast-slot"))
                val overrideConfig = activeApiConfig(name = "Grok Beta", id = ApiModelConfigurationId("api-grok-beta"))

                val activeModelProvider =
                    mockk<ActiveModelProviderPort>()
                coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns fastSlotConfig
                coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null
                coEvery {
                    activeModelProvider.getActiveConfigurationByOverride(any())
                } returns overrideConfig

                val messageRepository =
                    mockk<MessageRepository> {
                        coEvery { getMessageById(any()) } returns mockk(relaxed = true)
                    }
                val settingsRepository =
                    mockk<SettingsRepository> {
                        every { settingsFlow } returns flowOf(SettingsData())
                    }

                val executor = mockk<ChatInferenceExecutorPort>(relaxed = true)
                val resolvedConfigSlot = slot<ActiveModelConfiguration>()
                every { executor.executeWithOverride(any(), any(), any(), any(), any(), any(), capture(resolvedConfigSlot), any()) } returns flowOf()

                val useCase =
                    GenerateChatResponseUseCase(
                        pipelineExecutor = mockk(relaxed = true) {
                            every { executePipeline(any(), any(), any(), any(), any()) } returns flowOf()
                        },
                        chatRepository = mockk(relaxed = true),
                        messageRepository = messageRepository,
                        loggingPort = mockk(relaxed = true),
                        activeModelProvider = activeModelProvider,
                        extractedUrlTracker = noOpTracker,
                        chatInferenceExecutor = executor,
                        embeddingEngine = mockk(relaxed = true) {
                            coEvery { getEmbedding(any()) } returns floatArrayOf()
                        },
                        inferenceLockManager = FakeInferenceLockManager(),
                    )

                val override =
                    ChatModelOverride.Api(
                        configId = ApiModelConfigurationId("api-grok-beta"),
                        displayName = "Grok Beta",
                    )

                useCase(
                    prompt = "Hello",
                    userMessageId = MessageId("1"),
                    assistantMessageId = MessageId("2"),
                    chatId = ChatId("3"),
                    responseMode = ChatResponseMode.Other(modelOverride = override),
                    backgroundInferenceEnabled = false,
                ).toList()

                // The executor must receive the resolved OVERRIDE config, not the FAST slot.
                assertEquals("Grok Beta", resolvedConfigSlot.captured.name)
                assertEquals(ApiModelConfigurationId("api-grok-beta"), resolvedConfigSlot.captured.id)

                // Resolution must go through the override seam, never the FAST slot.
                coVerify(exactly = 0) {
                    activeModelProvider.getActiveConfiguration(ModelType.FAST)
                }
                coVerify(atLeast = 1) {
                    activeModelProvider.getActiveConfigurationByOverride(override)
                }
            }
    }

    @Nested
    @DisplayName("MD01 — Local override resolves explicit config, never FAST slot")
    inner class LocalOverride {
        @Test
        @DisplayName("MD01 — Other(Local override) threads 'Qwen Override' config to executeWithOverride")
        fun md01_localOverrideThreadedToExecutor() =
            runTest {
                val fastSlotConfig = activeApiConfig(name = "Fast Slot Model", id = ApiModelConfigurationId("fast-slot"))
                val overrideConfig = activeLocalConfig(
                    name = "Qwen Override",
                    id = LocalModelConfigurationId("local-qwen-preset"),
                )

                val activeModelProvider =
                    mockk<ActiveModelProviderPort>()
                coEvery { activeModelProvider.getActiveConfiguration(ModelType.FAST) } returns fastSlotConfig
                coEvery { activeModelProvider.getActiveConfiguration(ModelType.VISION) } returns null
                coEvery {
                    activeModelProvider.getActiveConfigurationByOverride(any())
                } returns overrideConfig

                val messageRepository =
                    mockk<MessageRepository> {
                        coEvery { getMessageById(any()) } returns mockk(relaxed = true)
                    }
                val settingsRepository =
                    mockk<SettingsRepository> {
                        every { settingsFlow } returns flowOf(SettingsData())
                    }

                val executor = mockk<ChatInferenceExecutorPort>(relaxed = true)
                val resolvedConfigSlot = slot<ActiveModelConfiguration>()
                every { executor.executeWithOverride(any(), any(), any(), any(), any(), any(), capture(resolvedConfigSlot), any()) } returns flowOf()

                val useCase =
                    GenerateChatResponseUseCase(
                        pipelineExecutor = mockk(relaxed = true) {
                            every { executePipeline(any(), any(), any(), any(), any()) } returns flowOf()
                        },
                        chatRepository = mockk(relaxed = true),
                        messageRepository = messageRepository,
                        loggingPort = mockk(relaxed = true),
                        activeModelProvider = activeModelProvider,
                        extractedUrlTracker = noOpTracker,
                        chatInferenceExecutor = executor,
                        embeddingEngine = mockk(relaxed = true) {
                            coEvery { getEmbedding(any()) } returns floatArrayOf()
                        },
                        inferenceLockManager = FakeInferenceLockManager(),
                    )

                val override =
                    ChatModelOverride.Local(
                        configId = LocalModelConfigurationId("local-qwen-preset"),
                        displayName = "Qwen Override",
                    )

                useCase(
                    prompt = "Hello",
                    userMessageId = MessageId("1"),
                    assistantMessageId = MessageId("2"),
                    chatId = ChatId("3"),
                    responseMode = ChatResponseMode.Other(modelOverride = override),
                    backgroundInferenceEnabled = false,
                ).toList()

                // The executor must receive the resolved OVERRIDE config, not the FAST slot.
                assertEquals("Qwen Override", resolvedConfigSlot.captured.name)
                assertEquals(LocalModelConfigurationId("local-qwen-preset"), resolvedConfigSlot.captured.id)
                assertTrue(resolvedConfigSlot.captured.isLocal)

                // Resolution must go through the override seam, never the FAST slot.
                coVerify(exactly = 0) {
                    activeModelProvider.getActiveConfiguration(ModelType.FAST)
                }
                coVerify(atLeast = 1) {
                    activeModelProvider.getActiveConfigurationByOverride(override)
                }
            }
    }

    private fun activeApiConfig(
        name: String,
        id: ApiModelConfigurationId,
    ): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = name.lowercase().replace(" ", "-"),
            id = id,
            isLocal = false,
            name = name,
            systemPrompt = null,
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )

    private fun activeLocalConfig(
        name: String,
        id: LocalModelConfigurationId,
    ): ActiveModelConfiguration =
        ActiveModelConfiguration(
            modelId = name.lowercase().replace(" ", "-"),
            id = id,
            isLocal = true,
            name = name,
            systemPrompt = null,
            reasoningEffort = null,
            temperature = 0.7,
            topK = 40,
            topP = 0.95,
            maxTokens = 512,
            minP = 0.0,
            repetitionPenalty = 1.1,
            contextWindow = 4096,
            thinkingEnabled = false,
        )
}
