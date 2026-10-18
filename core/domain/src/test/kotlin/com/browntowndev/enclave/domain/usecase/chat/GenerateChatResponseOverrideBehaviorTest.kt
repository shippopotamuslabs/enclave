package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatInferenceExecutorPort
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeSettingsRepository
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.enclave.domain.usecase.FakeInferenceLockManager
import com.browntowndev.enclave.domain.usecase.FakePipelineExecutor
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

    private fun fakeMessageRepository(): FakeMessageRepository =
        FakeMessageRepository(
            initial =
                listOf(
                    Message(
                        id = MessageId("1"),
                        chatId = ChatId("3"),
                        content = Content(text = "Hello"),
                    ),
                ),
        )

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
                    FakeActiveModelProvider(configuration = fastSlotConfig).apply {
                        configurationsByType[ModelType.VISION] = null
                        overrideConfiguration = overrideConfig
                        hasOverrideConfiguration = true
                    }

                val executor = FakeChatInferenceExecutorPort()

                val useCase =
                    GenerateChatResponseUseCase(
                        pipelineExecutor = FakePipelineExecutor(),
                        chatRepository = FakeChatRepository(),
                        messageRepository = fakeMessageRepository(),
                        loggingPort = FakeLoggingPort(),
                        activeModelProvider = activeModelProvider,
                        extractedUrlTracker = noOpTracker,
                        chatInferenceExecutor = executor,
                        embeddingEngine = FakeEmbeddingEngine(),
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
                val resolvedConfig = executor.executeWithOverrideConfigurations.single()
                assertEquals("Grok Beta", resolvedConfig.name)
                assertEquals(ApiModelConfigurationId("api-grok-beta"), resolvedConfig.id)

                // Resolution must go through the override seam, never the FAST slot.
                assertEquals(0, activeModelProvider.requestedModelTypes.count { it == ModelType.FAST })
                assertTrue(activeModelProvider.requestedOverrides.contains(override))
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
                    FakeActiveModelProvider(configuration = fastSlotConfig).apply {
                        configurationsByType[ModelType.VISION] = null
                        overrideConfiguration = overrideConfig
                        hasOverrideConfiguration = true
                    }

                val executor = FakeChatInferenceExecutorPort()

                val useCase =
                    GenerateChatResponseUseCase(
                        pipelineExecutor = FakePipelineExecutor(),
                        chatRepository = FakeChatRepository(),
                        messageRepository = fakeMessageRepository(),
                        loggingPort = FakeLoggingPort(),
                        activeModelProvider = activeModelProvider,
                        extractedUrlTracker = noOpTracker,
                        chatInferenceExecutor = executor,
                        embeddingEngine = FakeEmbeddingEngine(),
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
                val resolvedConfig = executor.executeWithOverrideConfigurations.single()
                assertEquals("Qwen Override", resolvedConfig.name)
                assertEquals(LocalModelConfigurationId("local-qwen-preset"), resolvedConfig.id)
                assertTrue(resolvedConfig.isLocal)

                // Resolution must go through the override seam, never the FAST slot.
                assertEquals(0, activeModelProvider.requestedModelTypes.count { it == ModelType.FAST })
                assertTrue(activeModelProvider.requestedOverrides.contains(override))
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
