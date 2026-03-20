package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.Message
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
/**
 * Edge case tests for GenerateChatResponseUseCase.
 * 
 * These tests verify:
 * 1. Empty thinking is handled correctly
 * 2. Long responses are handled correctly
 * 3. Cancellation is handled correctly
 * 4. Rapid state changes are handled correctly
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class EdgeCaseTests {

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
    // Test: Empty thinking is handled correctly
    // Evidence: No thinking events doesn't cause issues
    // ========================================================================

    @Test
    fun `empty thinking is handled correctly`() = runTest {
        // Given - response with no thinking
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
            InferenceEvent.Completed("Hello", null, ModelType.FAST)
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

        // Then - should complete successfully
        val messagesStates = states.filterIsInstance<MessageGenerationState.MessagesState>()
        assertTrue(messagesStates.isNotEmpty())
        
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertEquals("Hello", finalState!!.messages[2L]?.content)
        assertTrue(finalState.messages[2L]?.thinkingRaw?.isEmpty() == true, "Thinking should be empty")
    }

    // ========================================================================
    // Test: Long response is handled correctly
    // Evidence: Large content doesn't cause memory issues
    // ========================================================================

    @Test
    fun `long response is handled correctly`() = runTest {
        // Given - response with long content
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        val longContent = "A".repeat(10000) // 10KB response
        // Split into chunks to simulate partial responses
        val chunk1 = longContent.substring(0, 5000)
        val chunk2 = longContent.substring(5000)
        
        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse(chunk1, ModelType.FAST),
            InferenceEvent.PartialResponse(chunk2, ModelType.FAST),
            InferenceEvent.Completed(longContent, null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Give me a long response",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - should handle long content
        assertTrue(messagesStates.isNotEmpty())
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertEquals(longContent, finalState!!.messages[2L]?.content)
    }

    // ========================================================================
    // Test: Rapid partial responses are accumulated correctly
    // Evidence: Fast token emission doesn't cause race conditions
    // ========================================================================

    @Test
    fun `rapid partial responses are accumulated correctly`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        // Emit many rapid partial responses
        val rapidEvents = mutableListOf<InferenceEvent>()
        for (i in 1..100) {
            rapidEvents.add(InferenceEvent.PartialResponse("$i ", ModelType.FAST))
        }
        rapidEvents.add(InferenceEvent.Completed("", null, ModelType.FAST))
        
        every { fastModelService.sendPrompt(any(), any()) } returns kotlinx.coroutines.flow.flow {
            rapidEvents.forEach { emit(it) }
        }
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Count to 100",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - all content should be accumulated
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        val content = finalState!!.messages[2L]?.content ?: ""
        assertTrue(content.contains("1 "))
        assertTrue(content.contains("100 "))
    }

    // ========================================================================
    // Test: Safety blocked response is handled correctly
    // Evidence: Blocked content is accumulated and emitted
    // ========================================================================

    @Test
    fun `safety blocked response is handled correctly`() = runTest {
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
            prompt = "Bad request",
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
    // Test: Thinking followed by response is accumulated correctly
    // Evidence: Both thinking and content are preserved
    // ========================================================================

    @Test
    fun `thinking followed by response is accumulated correctly`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Let me think...", "Let me think...", ModelType.FAST),
            InferenceEvent.PartialResponse("Based on my ", ModelType.FAST),
            InferenceEvent.PartialResponse("thinking, here ", ModelType.FAST),
            InferenceEvent.PartialResponse("is the answer.", ModelType.FAST),
            InferenceEvent.Completed("Based on my thinking, here is the answer.", "Let me think...", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "What do you think?",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - both thinking and content should be accumulated
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertTrue(finalState!!.messages[2L]?.thinkingRaw?.contains("think") == true, "Should contain 'think'")
        assertTrue(finalState.messages[2L]?.content?.contains("thinking") == true, "Should contain 'thinking'")
    }

    // ========================================================================
    // Test: Empty content response is handled correctly
    // Evidence: Empty response doesn't cause issues
    // ========================================================================

    @Test
    fun `empty content response is handled correctly`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Completed("", null, ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Say nothing",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - should handle empty content
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        assertEquals("", finalState!!.messages[2L]?.content)
    }

    // ========================================================================
    // Test: Multiple thinking segments are accumulated
    // Evidence: Chain-of-thought is preserved
    // ========================================================================

    @Test
    fun `multiple thinking segments are accumulated`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("First, ", "First, ", ModelType.FAST),
            InferenceEvent.Thinking("second, ", "First, second, ", ModelType.FAST),
            InferenceEvent.Thinking("and third.", "First, second, and third.", ModelType.FAST),
            InferenceEvent.Completed("Done", "First, second, and third.", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val messagesStates = mutableListOf<MessageGenerationState.MessagesState>()
        generateChatResponseUseCase(
            prompt = "Think step by step",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { state ->
            if (state is MessageGenerationState.MessagesState) {
                messagesStates.add(state)
            }
        }

        // Then - all thinking should be accumulated
        val finalState = messagesStates.lastOrNull()
        assertNotNull(finalState)
        val thinking = finalState!!.messages[2L]?.thinkingRaw ?: ""
        assertTrue(thinking.contains("First"))
        assertTrue(thinking.contains("second"))
        assertTrue(thinking.contains("third"))
    }
}
