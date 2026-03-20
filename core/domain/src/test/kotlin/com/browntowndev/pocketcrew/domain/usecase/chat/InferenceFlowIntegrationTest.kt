package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Integration tests for end-to-end inference flows.
 * 
 * These tests verify:
 * 1. End-to-end flow from InferenceEvent to MessagesState
 * 2. Transaction handling for persistence
 * 3. CREW resume with existing state
 * 4. Lock management across flow lifecycle
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class InferenceFlowIntegrationTest {

    private lateinit var fastModelService: LlmInferencePort
    private lateinit var pipelineExecutor: PipelineExecutorPort
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository
    private lateinit var loggingPort: LoggingPort
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelRegistry: ModelRegistryPort
    private lateinit var generateChatResponseUseCase: GenerateChatResponseUseCase

    @BeforeEach
    fun setup() {
        fastModelService = mockk(relaxed = true)
        pipelineExecutor = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        inferenceLockManager = InferenceLockManagerImpl()
        modelRegistry = mockk(relaxed = true)
        
        generateChatResponseUseCase = GenerateChatResponseUseCase(
            fastModelService = fastModelService,
            thinkingModelService = fastModelService,
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry
        )
    }

    // ========================================================================
    // Test: End-to-end flow from InferenceEvent to MessagesState
    // Evidence: Complete flow transformation works correctly
    // ========================================================================

    @Test
    fun `end-to-end flow from InferenceEvent to MessagesState`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking step 1...", "Thinking step 1...", ModelType.FAST),
            InferenceEvent.PartialResponse("Hello ", ModelType.FAST),
            InferenceEvent.PartialResponse("world!", ModelType.FAST),
            InferenceEvent.Completed("Hello world!", "Thinking step 1...", ModelType.FAST)
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

        // Then - should emit MessagesState for each event
        val messagesStates = states.filterIsInstance<MessageGenerationState.MessagesState>()
        assertTrue(messagesStates.isNotEmpty())
        
        // Verify final state has accumulated content
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertTrue(finalState!!.messages[2L]?.content?.contains("Hello") == true)
        assertTrue(finalState.messages[2L]?.content?.contains("world!") == true)
    }

    // ========================================================================
    // Test: Flow completes and persists all messages
    // Evidence: onCompletion persists accumulated state
    // ========================================================================

    @Test
    fun `flow completes and persists all messages`() = runTest {
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

        // Then - repository should be called with final content using single transaction
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
    // Test: Lock is acquired on start and released on completion
    // Evidence: Lock management works correctly
    // ========================================================================

    @Test
    fun `lock acquired on start and released on completion`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("Done", null, ModelType.FAST)
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

        // Then - lock was released
        assertTrue(inferenceLockManager.isInferenceBlocked.value == false)
    }

    // ========================================================================
    // Test: Blocked inference returns MessagesState immediately
    // Evidence: Lock acquisition failure is handled gracefully
    // ========================================================================

    @Test
    fun `blocked inference returns MessagesState immediately`() = runTest {
        // Given - lock already held
        inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)

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
        assertTrue(state!!.messages[2L]?.content?.contains("Another message is in progress") == true)
    }

    // ========================================================================
    // Test: Error state is accumulated and persisted
    // Evidence: Error handling in flow transformation
    // ========================================================================

    @Test
    fun `error state is accumulated and persisted`() = runTest {
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
        assertTrue(finalState!!.messages[2L]?.content?.contains("Error") == true)
    }

    // ========================================================================
    // Test: Thinking state is accumulated correctly
    // Evidence: Thinking content is preserved in MessagesState
    // ========================================================================

    @Test
    fun `thinking state is accumulated correctly`() = runTest {
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
        assertTrue(finalState!!.messages[2L]?.thinkingRaw?.contains("Step 1") == true)
        assertTrue(finalState.messages[2L]?.thinkingRaw?.contains("Step 2") == true)
    }
}
