package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for [GenerateChatResponseUseCase] integration with `GenerationOptions`.
 *
 * Covers test_spec.md section 4:
 * - Defense Scenario 4: GenerateChatResponseUseCase calls sendPrompt with GenerationOptions
 * - Defense Scenario 8: Per-role thinkingEnabled resolution
 */
class GenerateChatResponseUseCaseGenerationOptionsTest {

    private lateinit var fastService: LlmInferencePort
    private lateinit var thinkingService: LlmInferencePort
    private lateinit var inferenceFactory: FakeInferenceFactory
    private lateinit var useCase: GenerateChatResponseUseCase
    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var pipelineExecutor: PipelineExecutorPort
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var loggingPort: LoggingPort

    private val sharedSha = "abc123"

    private fun config(thinkingEnabled: Boolean) = LocalModelConfiguration(
        id = 1L, localModelId = 1L, displayName = "Config",
        temperature = 0.7, topK = 40, topP = 0.9, minP = 0.0,
        repetitionPenalty = 1.1, maxTokens = 4096, contextWindow = 4096,
        thinkingEnabled = thinkingEnabled,
        systemPrompt = "You are a helpful assistant.", isSystemPreset = false
    )

    private fun asset() = LocalModelAsset(
        metadata = LocalModelMetadata(
            id = 1L, huggingFaceModelName = "test/model",
            remoteFileName = "model.gguf", localFileName = "model.gguf",
            sha256 = sharedSha, sizeInBytes = 1000L,
            modelFileFormat = ModelFileFormat.GGUF
        ),
        configurations = emptyList()
    )

    @BeforeEach
    fun setUp() {
        fastService = mockk(relaxed = true)
        thinkingService = mockk(relaxed = true)
        inferenceFactory = FakeInferenceFactory()
        pipelineExecutor = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        modelRegistry = mockk(relaxed = true)

        inferenceFactory.serviceMap[ModelType.FAST] = fastService
        inferenceFactory.serviceMap[ModelType.THINKING] = thinkingService

        useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = InferenceLockManagerImpl(),
            modelRegistry = modelRegistry,
        )

        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()
        coEvery { chatRepository.persistAllMessageData(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
    }

    private fun fastSuccessFlow() = flowOf(
        InferenceEvent.PartialResponse("Fast response", ModelType.FAST),
        InferenceEvent.Finished(ModelType.FAST)
    )

    private fun thinkingSuccessFlow() = flowOf(
        InferenceEvent.Thinking("Thinking...", ModelType.THINKING),
        InferenceEvent.PartialResponse("Thoughtful response", ModelType.THINKING),
        InferenceEvent.Finished(ModelType.THINKING)
    )

    // ===== Defense Scenario 4: FAST =====

    @Test
    fun `FAST mode calls sendPrompt overload with GenerationOptions reasoningBudget 0`() =
        runTest {
            coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset()
            coEvery { modelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns config(thinkingEnabled = false)

            val optionsSlot = CapturingSlot<GenerationOptions>()
            every { fastService.sendPrompt(any<String>(), capture(optionsSlot), any<Boolean>()) } returns fastSuccessFlow()

            useCase(
                prompt = "Hello", userMessageId = 1L, assistantMessageId = 2L,
                chatId = 1L, mode = Mode.FAST
            ).collect { }

            assertTrue(optionsSlot.isCaptured,
                "sendPrompt(prompt, options, closeConversation) overload must be called — the use case is still using the default overload")
            assertTrue(optionsSlot.captured.reasoningBudget == 0,
                "FAST with thinkingEnabled=false must pass reasoningBudget=0, but got ${optionsSlot.captured.reasoningBudget}")
        }

    @Test
    fun `FAST mode executes through inference factory wrapper`() = runTest {
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset()
        coEvery { modelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns config(thinkingEnabled = false)

        every { fastService.sendPrompt(any<String>(), any<GenerationOptions>(), any<Boolean>()) } returns fastSuccessFlow()

        useCase(
            prompt = "Hello", userMessageId = 1L, assistantMessageId = 2L,
            chatId = 1L, mode = Mode.FAST
        ).collect { }

        assertTrue(ModelType.FAST in inferenceFactory.executedTypes,
            "withInferenceService(ModelType.FAST) must be called")
    }

    // ===== Defense Scenario 4: THINKING =====

    @Test
    fun `THINKING mode calls sendPrompt overload with GenerationOptions reasoningBudget 2048`() =
        runTest {
            coEvery { modelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset()
            coEvery { modelRegistry.getRegisteredConfiguration(ModelType.THINKING) } returns config(thinkingEnabled = true)

            val optionsSlot = CapturingSlot<GenerationOptions>()
            every { thinkingService.sendPrompt(any<String>(), capture(optionsSlot), any<Boolean>()) } returns thinkingSuccessFlow()

            useCase(
                prompt = "Think", userMessageId = 1L, assistantMessageId = 2L,
                chatId = 1L, mode = Mode.THINKING
            ).collect { }

            assertTrue(optionsSlot.isCaptured,
                "sendPrompt(prompt, options, closeConversation) overload must be called")
            assertTrue(optionsSlot.captured.reasoningBudget == 2048,
                "THINKING with thinkingEnabled=true must pass reasoningBudget=2048, but got ${optionsSlot.captured.reasoningBudget}")
        }

    @Test
    fun `THINKING mode executes through inference factory wrapper`() = runTest {
        coEvery { modelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset()
        coEvery { modelRegistry.getRegisteredConfiguration(ModelType.THINKING) } returns config(thinkingEnabled = true)

        every { thinkingService.sendPrompt(any<String>(), any<GenerationOptions>(), any<Boolean>()) } returns thinkingSuccessFlow()

        useCase(
            prompt = "Think", userMessageId = 1L, assistantMessageId = 2L,
            chatId = 1L, mode = Mode.THINKING
        ).collect { }

        assertTrue(ModelType.THINKING in inferenceFactory.executedTypes,
            "withInferenceService(ModelType.THINKING) must be called")
    }

    // ===== Defense Scenario 8: Per-role thinking resolution =====
    // Same SHA, different configs → different reasoningBudget per role

    @Test
    fun `same SHA with FAST thinkingEnabled=false gets reasoningBudget=0`() = runTest {
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset()
        coEvery { modelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns config(thinkingEnabled = false)

        val optionsSlot = CapturingSlot<GenerationOptions>()
        every { fastService.sendPrompt(any<String>(), capture(optionsSlot), any<Boolean>()) } returns fastSuccessFlow()

        useCase(
            prompt = "Hello", userMessageId = 1L, assistantMessageId = 2L,
            chatId = 1L, mode = Mode.FAST
        ).collect { }

        assertTrue(optionsSlot.isCaptured)
        assertTrue(optionsSlot.captured.reasoningBudget == 0)
    }

    @Test
    fun `same SHA with THINKING thinkingEnabled=true gets reasoningBudget=2048`() = runTest {
        coEvery { modelRegistry.getRegisteredAsset(ModelType.THINKING) } returns asset()
        coEvery { modelRegistry.getRegisteredConfiguration(ModelType.THINKING) } returns config(thinkingEnabled = true)

        val optionsSlot = CapturingSlot<GenerationOptions>()
        every { thinkingService.sendPrompt(any<String>(), capture(optionsSlot), any<Boolean>()) } returns thinkingSuccessFlow()

        useCase(
            prompt = "Think", userMessageId = 1L, assistantMessageId = 2L,
            chatId = 1L, mode = Mode.THINKING
        ).collect { }

        assertTrue(optionsSlot.isCaptured)
        assertTrue(optionsSlot.captured.reasoningBudget == 2048)
    }

    @Test
    fun `FAST mode ignores misbound thinkingEnabled config and still uses reasoningBudget 0`() = runTest {
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns asset()
        coEvery { modelRegistry.getRegisteredConfiguration(ModelType.FAST) } returns config(thinkingEnabled = true)

        val optionsSlot = CapturingSlot<GenerationOptions>()
        every { fastService.sendPrompt(any<String>(), capture(optionsSlot), any<Boolean>()) } returns fastSuccessFlow()

        useCase(
            prompt = "Hello", userMessageId = 1L, assistantMessageId = 2L,
            chatId = 1L, mode = Mode.FAST
        ).collect { }

        assertTrue(optionsSlot.isCaptured)
        assertTrue(optionsSlot.captured.reasoningBudget == 0)
    }
}
