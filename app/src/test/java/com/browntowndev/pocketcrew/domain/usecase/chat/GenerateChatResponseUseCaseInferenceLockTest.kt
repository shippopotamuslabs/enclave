package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for inference lock behavior in GenerateChatResponseUseCase.
 * Tests cover:
 * - CREW mode with mixed models (some BYOK, some on-device)
 * - CREW mode with all on-device models
 * - FAST/THINKING modes
 * - Edge cases around lock acquisition and release
 */
class GenerateChatResponseUseCaseInferenceLockTest {

    private lateinit var fastModelService: LlmInferencePort
    private lateinit var thinkingModelService: LlmInferencePort
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var bufferThinkingSteps: BufferThinkingStepsUseCase
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var useCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setup() {
        fastModelService = mockk(relaxed = true)
        thinkingModelService = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        bufferThinkingSteps = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        modelRegistry = mockk(relaxed = true)

        // Default: lock is available
        coEvery { inferenceLockManager.acquireLock(any()) } returns true
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        coEvery { inferenceLockManager.releaseLock() } returns Unit

        // Default: models are registered (on-device)
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockk()

        val loggingPort = mockk<LoggingPort>(relaxed = true)

        useCase = GenerateChatResponseUseCase(
            fastModelService = fastModelService,
            thinkingModelService = thinkingModelService,
            chatRepository = mockk(relaxed = true),
            pipelineExecutor = mockk(relaxed = true),
            messageRepository = messageRepository,
            bufferThinkingSteps = bufferThinkingSteps,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry
        )
    }

    // ========== CREW Mode Mixed Models Tests ==========

    @Test
    fun `CREW mode acquires ON_DEVICE lock when draftOne is on-device`() = runTest {
        // Given: Only DRAFT_ONE is registered (on-device), others are null
        // Need to set up specific model registry behavior for this test
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should acquire ON_DEVICE lock (at least one model is on-device)
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `CREW mode acquires ON_DEVICE lock when draftTwo is on-device`() = runTest {
        // Given: Only DRAFT_TWO is registered (on-device), others are null
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should acquire ON_DEVICE lock (at least one model is on-device)
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `CREW mode acquires ON_DEVICE lock when main is on-device`() = runTest {
        // Given: Only MAIN is registered (on-device), others are null
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns mockk()

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should acquire ON_DEVICE lock (at least one model is on-device)
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `CREW mode with all three models on-device acquires ON_DEVICE lock`() = runTest {
        // Given: All three models are registered (on-device)
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns mockk()

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should acquire ON_DEVICE lock
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `CREW mode with two on-device and one BYOK acquires ON_DEVICE lock`() = runTest {
        // Given: DRAFT_ONE and DRAFT_TWO are on-device, MAIN is BYOK (null)
        // This is the mixed scenario: some BYOK, some on-device
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns mockk()
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should still acquire ON_DEVICE lock because at least one is on-device
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    // ========== FAST Mode Tests ==========

    @Test
    fun `FAST mode acquires ON_DEVICE lock when model is registered`() = runTest {
        // Given: FAST model is registered (on-device)
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockk()

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then: Should acquire ON_DEVICE lock
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    // ========== THINKING Mode Tests ==========

    @Test
    fun `THINKING mode acquires ON_DEVICE lock when model is registered`() = runTest {
        // Given: THINKING model is registered (on-device)
        every { modelRegistry.getRegisteredModelSync(ModelType.THINKING) } returns mockk()

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { }

        // Then: Should acquire ON_DEVICE lock
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    // ========== Lock Blocking Tests ==========

    @Test
    fun `blocks concurrent inference when lock is already held`() = runTest {
        // Given: Lock is already held by another inference
        coEvery { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) } returns false

        // When
        val states = mutableListOf<MessageGenerationState>()
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { states.add(it) }

        // Then: Should emit Blocked state immediately (before any other inference)
        assertTrue(states.first() is MessageGenerationState.Blocked)
        assertEquals(
            "Another inference is in progress. Please wait.",
            (states.first() as MessageGenerationState.Blocked).reason
        )
    }

    @Test
    fun `releases lock when inference completes`() = runTest {
        // Given: Lock is acquired successfully
        coEvery { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) } returns true

        // When: Collect all emissions (completes the flow)
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then: Lock should be released
        coVerify { inferenceLockManager.releaseLock() }
    }

    // ========== Edge Cases ==========

    @Test
    fun `uses ON_DEVICE as safety default when no models registered in CREW`() = runTest {
        // Given: No models are registered (null for all)
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns null
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { }

        // Then: Should still acquire ON_DEVICE lock as safety default
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `uses ON_DEVICE as safety default when FAST model not registered`() = runTest {
        // Given: FAST model is not registered
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then: Should still acquire ON_DEVICE lock as safety default
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }

    @Test
    fun `uses ON_DEVICE as safety default when THINKING model not registered`() = runTest {
        // Given: THINKING model is not registered
        every { modelRegistry.getRegisteredModelSync(ModelType.THINKING) } returns null

        // When
        useCase(
            prompt = "test",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { }

        // Then: Should still acquire ON_DEVICE lock as safety default
        coVerify { inferenceLockManager.acquireLock(InferenceType.ON_DEVICE) }
    }
}
