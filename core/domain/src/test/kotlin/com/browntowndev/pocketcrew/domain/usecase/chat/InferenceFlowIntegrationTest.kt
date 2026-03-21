/*
 * Copyright 2024 Pocket Crew
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Integration tests for GenerateChatResponseUseCase.
 * 
 * These tests verify the complete flow from InferenceEvent to AccumulatedMessages,
 * ensuring that state is accumulated correctly and emitted for real-time UI updates.
 */
class InferenceFlowIntegrationTest {

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
    // Test: End-to-end flow from InferenceEvent to AccumulatedMessages
    // Evidence: Complete flow transformation works correctly
    // ========================================================================

    @Test
    fun `end-to-end flow from InferenceEvent to AccumulatedMessages`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking step 1...", ModelType.FAST),
            InferenceEvent.PartialResponse("Hello ", ModelType.FAST),
            InferenceEvent.PartialResponse("world!", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - should emit AccumulatedMessages for each event
        assertTrue(accumulatedMessages.isNotEmpty())
        
        // Verify final state has accumulated content
        val finalState = accumulatedMessages.lastOrNull()
        assertNotNull(finalState)
        val snapshot = finalState!!.messages[2L]
        assertNotNull(snapshot)
        assertTrue(snapshot!!.content.contains("Hello"))
        assertTrue(snapshot.content.contains("world!"))
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
            InferenceEvent.Finished(ModelType.FAST)
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
                messageState = MessageState.COMPLETE,
                pipelineStep = PipelineStep.FINAL
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
            InferenceEvent.Finished(ModelType.FAST)
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
    // Test: Blocked inference returns AccumulatedMessages immediately
    // Evidence: Lock acquisition failure is handled gracefully
    // ========================================================================

    @Test
    fun `blocked inference returns AccumulatedMessages immediately`() = runTest {
        // Given - lock already held
        inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)

        // When
        val blockedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            blockedMessages.add(messages)
        }

        // Then - should emit immediately with blocking message
        assertTrue(blockedMessages.isNotEmpty())
        
        val state = blockedMessages.firstOrNull()
        assertNotNull(state)
        val snapshot = state!!.messages[2L]
        assertNotNull(snapshot)
        assertTrue(snapshot!!.content.contains("Another message is in progress"))
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
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - AccumulatedMessages should contain error content
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertTrue(snapshot!!.content.contains("Error"))
    }

    // ========================================================================
    // Test: Thinking state is accumulated correctly
    // Evidence: Thinking content is preserved in AccumulatedMessages
    // ========================================================================

    @Test
    fun `thinking state is accumulated correctly`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Step 1...", ModelType.FAST),
            InferenceEvent.Thinking("Step 2...", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - thinking should be accumulated
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertTrue(snapshot!!.thinkingRaw.contains("Step 1"))
        assertTrue(snapshot.thinkingRaw.contains("Step 2"))
    }

    // ========================================================================
    // Test: ThinkingLive event sets messageState to THINKING
    // Evidence: MessageSnapshot should track THINKING state during thinking phase
    // ========================================================================

    @Test
    fun `ThinkingLive event sets messageState to THINKING`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Let me think...", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - messageState should be THINKING during thinking phase
        assertTrue(accumulatedMessages.isNotEmpty())
        val snapshot = accumulatedMessages.last().messages[2L]
        assertNotNull(snapshot)
        assertEquals(MessageState.THINKING, snapshot!!.messageState)
    }

    // ========================================================================
    // Test: GeneratingText event sets messageState to GENERATING
    // Evidence: MessageSnapshot should transition to GENERATING when text starts
    // ========================================================================

    @Test
    fun `GeneratingText event sets messageState to GENERATING`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello world!", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - messageState should be GENERATING during generation phase
        assertTrue(accumulatedMessages.isNotEmpty())
        val snapshot = accumulatedMessages.last().messages[2L]
        assertNotNull(snapshot)
        assertEquals(MessageState.GENERATING, snapshot!!.messageState)
    }

    // ========================================================================
    // Test: Finished event sets messageState to COMPLETE
    // Evidence: MessageSnapshot should transition to COMPLETE when finished
    // ========================================================================

    @Test
    fun `Finished event sets messageState to COMPLETE`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Finished(ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - messageState should be COMPLETE on finish
        assertTrue(accumulatedMessages.isNotEmpty())
        val snapshot = accumulatedMessages.last().messages[2L]
        assertNotNull(snapshot)
        assertEquals(MessageState.COMPLETE, snapshot!!.messageState)
    }

    // ========================================================================
    // Test: thinkingEndTime is set when first GeneratingText arrives after Thinking
    // Evidence: Duration should be calculated from thinking timestamps
    // ========================================================================

    @Test
    fun `thinkingEndTime is set when GeneratingText first arrives after Thinking`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", ModelType.FAST),
            InferenceEvent.PartialResponse("First response", ModelType.FAST),
            InferenceEvent.PartialResponse(" continues", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - thinkingEndTime should be set when first GeneratingText arrives
        val generatingSnapshot = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.startsWith("First response") == true
        }
        assertNotNull(generatingSnapshot, "Should have a state with content starting with 'First response'")
        val snapshot = generatingSnapshot!!.messages[2L]
        assertNotNull(snapshot)
        assertTrue(snapshot!!.thinkingEndTime > 0, "thinkingEndTime should be set when generating starts")
    }

    // ========================================================================
    // Test: thinkingEndTime is not overwritten by subsequent GeneratingText
    // Evidence: Duration should remain constant once set
    // ========================================================================

    @Test
    fun `thinkingEndTime is not overwritten by subsequent GeneratingText`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", ModelType.FAST),
            InferenceEvent.PartialResponse("First", ModelType.FAST),
            InferenceEvent.PartialResponse("Second", ModelType.FAST),
            InferenceEvent.PartialResponse("Third", ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - thinkingEndTime should remain consistent across states
        val firstGenerating = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("First") == true
        }
        val secondGenerating = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Second") == true
        }
        val thirdGenerating = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Third") == true
        }

        assertNotNull(firstGenerating)
        assertNotNull(secondGenerating)
        assertNotNull(thirdGenerating)

        val firstEndTime = firstGenerating!!.messages[2L]!!.thinkingEndTime
        val secondEndTime = secondGenerating!!.messages[2L]!!.thinkingEndTime
        val thirdEndTime = thirdGenerating!!.messages[2L]!!.thinkingEndTime

        assertEquals(firstEndTime, secondEndTime, "thinkingEndTime should not change")
        assertEquals(secondEndTime, thirdEndTime, "thinkingEndTime should not change")
    }

    // ========================================================================
    // Test: messageState transitions correctly from THINKING to GENERATING to COMPLETE
    // Evidence: Full lifecycle should be tracked
    // ========================================================================

    @Test
    fun `messageState transitions correctly from THINKING to GENERATING to COMPLETE`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", ModelType.FAST),
            InferenceEvent.PartialResponse("Response", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - verify state transitions
        assertTrue(accumulatedMessages.isNotEmpty())
        
        // Find states with different content
        val thinkingState = accumulatedMessages.find { messages ->
            messages.messages[2L]?.thinkingRaw?.contains("Thinking") == true &&
            messages.messages[2L]?.content?.isEmpty() == true
        }
        val generatingState = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Response") == true &&
            messages.messages[2L]?.isComplete == false
        }
        val completeState = accumulatedMessages.find { messages ->
            messages.messages[2L]?.isComplete == true
        }

        assertNotNull(thinkingState, "Should have THINKING state")
        assertNotNull(generatingState, "Should have GENERATING state")
        assertNotNull(completeState, "Should have COMPLETE state")

        assertEquals(MessageState.THINKING, thinkingState!!.messages[2L]!!.messageState)
        assertEquals(MessageState.GENERATING, generatingState!!.messages[2L]!!.messageState)
        assertEquals(MessageState.COMPLETE, completeState!!.messages[2L]!!.messageState)
    }

    // ========================================================================
    // Test: GeneratingText with DRAFT_ONE model sets pipelineStep to DRAFT_ONE
    // Evidence: pipelineStep should be tracked based on model type
    // ========================================================================

    @Test
    fun `GeneratingText with DRAFT_ONE model sets pipelineStep to DRAFT_ONE`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns mockConfig

        every { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Draft content", ModelType.DRAFT_ONE),
            InferenceEvent.Finished(ModelType.DRAFT_ONE)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - pipelineStep should be DRAFT_ONE for DRAFT_ONE model
        assertTrue(accumulatedMessages.isNotEmpty())
        val generatingSnapshot = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Draft") == true
        }
        assertNotNull(generatingSnapshot, "Should have DRAFT_ONE state")
        val snapshot = generatingSnapshot!!.messages[2L]
        assertNotNull(snapshot)
        assertEquals(PipelineStep.DRAFT_ONE, snapshot!!.pipelineStep)
    }

    // ========================================================================
    // Test: GeneratingText with DRAFT_TWO model sets pipelineStep to DRAFT_TWO
    // Evidence: pipelineStep should be tracked for each model type
    // ========================================================================

    @Test
    fun `GeneratingText with DRAFT_TWO model sets pipelineStep to DRAFT_TWO`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) } returns mockConfig

        every { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Draft two content", ModelType.DRAFT_TWO),
            InferenceEvent.Finished(ModelType.DRAFT_TWO)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - pipelineStep should be DRAFT_TWO for DRAFT_TWO model
        assertTrue(accumulatedMessages.isNotEmpty())
        val generatingSnapshot = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Draft two") == true
        }
        assertNotNull(generatingSnapshot, "Should have DRAFT_TWO state")
        val snapshot = generatingSnapshot!!.messages[2L]
        assertNotNull(snapshot)
        assertEquals(PipelineStep.DRAFT_TWO, snapshot!!.pipelineStep)
    }

    // ========================================================================
    // Test: GeneratingText with MAIN model sets pipelineStep to SYNTHESIS
    // Evidence: MAIN model maps to SYNTHESIS step
    // ========================================================================

    @Test
    fun `GeneratingText with MAIN model sets pipelineStep to SYNTHESIS`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.MAIN) } returns mockConfig

        every { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Main synthesis content", ModelType.MAIN),
            InferenceEvent.Finished(ModelType.MAIN)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - pipelineStep should be SYNTHESIS for MAIN model
        assertTrue(accumulatedMessages.isNotEmpty())
        val generatingSnapshot = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Main synthesis") == true
        }
        assertNotNull(generatingSnapshot, "Should have SYNTHESIS state")
        val snapshot = generatingSnapshot!!.messages[2L]
        assertNotNull(snapshot)
        assertEquals(PipelineStep.SYNTHESIS, snapshot!!.pipelineStep)
    }

    // ========================================================================
    // Test: GeneratingText with FINAL_SYNTHESIS model sets pipelineStep to FINAL
    // Evidence: FINAL_SYNTHESIS model maps to FINAL step
    // ========================================================================

    @Test
    fun `GeneratingText with FINAL_SYNTHESIS model sets pipelineStep to FINAL`() = runTest {
        // Given
        val mockConfig = mockk<com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FINAL_SYNTHESIS) } returns mockConfig

        every { thinkingModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Final content", ModelType.FINAL_SYNTHESIS),
            InferenceEvent.Finished(ModelType.FINAL_SYNTHESIS)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.THINKING
        ).collect { messages ->
            accumulatedMessages.add(messages)
        }

        // Then - pipelineStep should be FINAL for FINAL_SYNTHESIS model
        assertTrue(accumulatedMessages.isNotEmpty())
        val generatingSnapshot = accumulatedMessages.find { messages ->
            messages.messages[2L]?.content?.contains("Final") == true
        }
        assertNotNull(generatingSnapshot, "Should have FINAL state")
        val snapshot = generatingSnapshot!!.messages[2L]
        assertNotNull(snapshot)
        assertEquals(PipelineStep.FINAL, snapshot!!.pipelineStep)
    }
}
