package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.FakePipelineExecutor
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests that GenerateChatResponseUseCase correctly routes through InferenceFactoryPort
 * after being refactored from direct @FastModelEngine/@ThinkingModelEngine qualifier injection.
 *
 * These tests will FAIL until the use case constructor is refactored to accept
 * InferenceFactoryPort instead of two qualified LlmInferencePort parameters.
 */
class GenerateChatResponseUseCaseRefactorTest {

    private lateinit var inferenceFactory: FakeInferenceFactory
    private lateinit var pipelineExecutor: FakePipelineExecutor
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var loggingPort: LoggingPort
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var mockInferenceService: LlmInferencePort
    private lateinit var useCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setUp() {
        inferenceFactory = FakeInferenceFactory()
        pipelineExecutor = FakePipelineExecutor()
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        inferenceLockManager = InferenceLockManagerImpl()
        modelRegistry = mockk(relaxed = true)

        mockInferenceService = mockk(relaxed = true)
        inferenceFactory.serviceToReturn = mockInferenceService

        // This constructor will fail to compile until the refactor is complete.
        // The refactored constructor accepts InferenceFactoryPort instead of
        // @FastModelEngine/@ThinkingModelEngine qualified LlmInferencePort params.
        useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry,
        )
    }

    // ========================================================================
    // Happy Path
    // ========================================================================

    @Test
    fun `FAST mode routes through InferenceFactoryPort`() = runTest {
        every { mockInferenceService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST),
        )

        val results = useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 100L,
            mode = Mode.FAST,
        ).toList()

        assertEquals(listOf(ModelType.FAST), inferenceFactory.resolvedTypes)
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `THINKING mode routes through InferenceFactoryPort`() = runTest {
        every { mockInferenceService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Deep thought", ModelType.THINKING),
            InferenceEvent.Finished(ModelType.THINKING),
        )

        val results = useCase(
            prompt = "explain this",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 100L,
            mode = Mode.THINKING,
        ).toList()

        assertEquals(listOf(ModelType.THINKING), inferenceFactory.resolvedTypes)
        assertTrue(results.isNotEmpty())
    }

    @Test
    fun `CREW mode delegates to PipelineExecutorPort - not InferenceFactoryPort`() = runTest {
        val results = useCase(
            prompt = "build me a feature",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 100L,
            mode = Mode.CREW,
        ).toList()

        assertTrue(inferenceFactory.resolvedTypes.isEmpty())
        assertTrue(pipelineExecutor.executePipelineCalled)
    }

    // ========================================================================
    // Error Path
    // ========================================================================

    @Test
    fun `InferenceFactoryPort failure propagates as error state`() = runTest {
        inferenceFactory.exceptionToThrow = RuntimeException("Engine unavailable")

        val results = useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 100L,
            mode = Mode.FAST,
        ).toList()

        assertTrue(results.isNotEmpty())
        // The last emission should contain error content
        val lastEmission = results.last()
        val errorSnapshot = lastEmission.messages.values.firstOrNull()
        assertTrue(
            errorSnapshot?.content?.contains("Error", ignoreCase = true) == true ||
                errorSnapshot?.content?.contains("unavailable", ignoreCase = true) == true,
            "Expected error content but got: ${errorSnapshot?.content}",
        )
    }

    // ========================================================================
    // Mutation Defense
    // ========================================================================

    @Test
    fun `removing old qualifier fields doesnt break FAST or THINKING`() = runTest {
        // This test proves the use case no longer needs fastModelService/thinkingModelService
        // fields — it only needs InferenceFactoryPort. If this test compiles and we reach
        // this point, the refactored constructor is structurally correct.
        every { mockInferenceService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("fast", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST),
        )

        useCase(
            prompt = "fast test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 100L,
            mode = Mode.FAST,
        ).toList()

        every { mockInferenceService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("think", ModelType.THINKING),
            InferenceEvent.Finished(ModelType.THINKING),
        )

        // Need a new lock for the second call
        inferenceLockManager.releaseLock()

        useCase(
            prompt = "thinking test",
            userMessageId = 3L,
            assistantMessageId = 4L,
            chatId = 100L,
            mode = Mode.THINKING,
        ).toList()

        assertEquals(listOf(ModelType.FAST, ModelType.THINKING), inferenceFactory.resolvedTypes)
    }
}
