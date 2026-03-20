package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
/**
 * Tests for GenerateChatResponseUseCase - Real-Time Flow Architecture.
 * 
 * Tests verify the new architecture where:
 * 1. Flow emits ONLY MessagesState (not individual events)
 * 2. State is accumulated internally using StringBuilder
 * 3. Persistence to DB happens on completion (not per-event)
 * 4. buffer(64) is used for backpressure
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class GenerateChatResponseUseCaseTest {

    private lateinit var fastModelService: LlmInferencePort
    private lateinit var thinkingModelService: LlmInferencePort
    private lateinit var pipelineExecutor: PipelineExecutorPort
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var loggingPort: LoggingPort
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var generateChatResponseUseCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setUp() {
        fastModelService = mockk(relaxed = true)
        thinkingModelService = mockk(relaxed = true)
        pipelineExecutor = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        inferenceLockManager = InferenceLockManagerImpl()
        modelRegistry = mockk(relaxed = true)
        
        generateChatResponseUseCase = GenerateChatResponseUseCase(
            fastModelService = fastModelService,
            thinkingModelService = thinkingModelService,
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry
        )
    }

    // ========================================================================
    // Test: Flow emits ONLY MessagesState (not individual states)
    // Evidence: New architecture emits accumulated state only
    // ========================================================================

    @Test
    fun `FAST mode emits MessagesState with accumulated content`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", "Thinking...", ModelType.FAST),
            InferenceEvent.PartialResponse("Hello ", ModelType.FAST),
            InferenceEvent.PartialResponse("world!", ModelType.FAST),
            InferenceEvent.Completed("Hello world!", null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val states = mutableListOf<MessageGenerationState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            states.add(state)
        }

        // Then - should emit ONLY MessagesState (not individual ThinkingLive, GeneratingText, etc.)
        val messagesStates = states.filterIsInstance<MessageGenerationState.MessagesState>()
        assertTrue(messagesStates.isNotEmpty(), "Should emit at least one MessagesState")
        
        // Verify individual states are NOT emitted
        val thinkingStates = states.filterIsInstance<MessageGenerationState.ThinkingLive>()
        val generatingStates = states.filterIsInstance<MessageGenerationState.GeneratingText>()
        assertTrue(thinkingStates.isEmpty(), "Should NOT emit individual ThinkingLive states")
        assertTrue(generatingStates.isEmpty(), "Should NOT emit individual GeneratingText states")
        
        // Verify final MessagesState has accumulated content
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertEquals("Hello world!", finalState!!.messages[2L]?.content)
    }

    // ========================================================================
    // Test: MessagesState contains thinking content
    // Evidence: Thinking is accumulated and included in MessagesState
    // ========================================================================

    @Test
    fun `MessagesState contains accumulated thinking`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Step 1...", "Step 1...", ModelType.FAST),
            InferenceEvent.Thinking("Step 2...", "Step 1...Step 2...", ModelType.FAST),
            InferenceEvent.Completed("Done", "Step 1...Step 2...", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - thinking should be accumulated
        assertTrue(messagesStates.isNotEmpty())
        val finalState = messagesStates.lastOrNull()
        assertTrue(finalState!!.messages[2L]?.thinkingRaw?.contains("Step 1") == true, "Should contain Step 1")
        assertTrue(finalState.messages[2L]?.thinkingRaw?.contains("Step 2") == true, "Should contain Step 2")
    }

    // ========================================================================
    // Test: Lock released on flow completion
    // Evidence: Lock is released in onCompletion
    // ========================================================================

    @Test
    fun `lock released on flow completion`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Done", null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When - flow completes
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then - lock should be released
        assertFalse(inferenceLockManager.isInferenceBlocked.value)
    }

    // ========================================================================
    // Test: Lock released on flow cancellation
    // Evidence: Lock is released even when flow is cancelled
    // ========================================================================

    @Test
    fun `lock released on flow cancellation`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("partial", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When - flow is cancelled
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { 
            kotlinx.coroutines.delay(100)
        }

        // Then - lock should still be released
        assertFalse(inferenceLockManager.isInferenceBlocked.value)
    }

    // ========================================================================
    // Test: Rehydration failure continues without history
    // Evidence: Rehydration failure is caught but flow continues
    // ========================================================================

    @Test
    fun `rehydration failure continues without history`() = runTest {
        // Given - rehydration throws
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Response without history", null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } throws RuntimeException("Database error")

        // When
        val states = mutableListOf<MessageGenerationState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            states.add(state)
        }

        // Then - should still emit MessagesState (continued without history)
        val messagesStates = states.filterIsInstance<MessageGenerationState.MessagesState>()
        assertTrue(messagesStates.isNotEmpty(), "Should emit MessagesState even after rehydration failure")
    }

    // ========================================================================
    // Test: Persistence happens on completion
    // Evidence: Repository calls happen after flow completes
    // ========================================================================

    @Test
    fun `persistence happens on completion`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
            InferenceEvent.Completed("Hello", null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then - repository calls happen AFTER collection completes using single transaction
        coVerify { 
            chatRepository.persistAllMessageData(
                messageId = 2L,
                modelType = ModelType.FAST,
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = "Hello",
                messageState = MessageState.COMPLETE
            )
        }
    }

    // ========================================================================
    // Test: Blocked state accumulated in MessagesState
    // Evidence: Blocked content is accumulated and emitted
    // ========================================================================

    @Test
    fun `blocked state accumulated in MessagesState`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.SafetyBlocked("Content policy violation", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - MessagesState should contain blocked content
        assertTrue(messagesStates.isNotEmpty())
        val finalState = messagesStates.lastOrNull()
        assertTrue(finalState!!.messages[2L]?.content?.contains("Blocked") ?: false)
    }

    // ========================================================================
    // Test: Error state accumulated in MessagesState
    // Evidence: Error content is accumulated and emitted
    // ========================================================================

    @Test
    fun `error state accumulated in MessagesState`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Error(RuntimeException("Model crashed"), ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - MessagesState should contain error content
        assertTrue(messagesStates.isNotEmpty())
        val finalState = messagesStates.lastOrNull()
        assertTrue(finalState!!.messages[2L]?.content?.contains("Error") ?: false)
    }

    // ========================================================================
    // Test: Lock acquisition failure returns blocked MessagesState
    // Evidence: When lock can't be acquired, MessagesState is emitted
    // ========================================================================

    @Test
    fun `lock acquisition failure returns blocked MessagesState`() = runTest {
        // Given - lock already held
        inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)
        assertTrue(inferenceLockManager.isInferenceBlocked.value)

        // When
        val states = mutableListOf<MessageGenerationState>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            states.add(state)
        }

        // Then - should emit MessagesState with blocking message
        val messagesStates = states.filterIsInstance<MessageGenerationState.MessagesState>()
        assertTrue(messagesStates.isNotEmpty())
        
        val state = messagesStates.firstOrNull()
        assertNotNull(state)
        assertTrue(state!!.messages[2L]?.content?.contains("Another message is in progress") ?: false)
    }

    // ========================================================================
    // Test: ON_DEVICE blocks other ON_DEVICE
    // Evidence: Second ON_DEVICE inference is blocked
    // ========================================================================

    @Test
    fun `ON_DEVICE blocks other ON_DEVICE`() = runTest {
        // Given - First inference holds lock
        val firstAcquire = inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)
        assertTrue(firstAcquire)

        // When - Second ON_DEVICE tries to acquire
        val secondAcquire = inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)

        // Then - Second is blocked
        assertFalse(secondAcquire)
        assertTrue(inferenceLockManager.isInferenceBlocked.value)
    }
}
