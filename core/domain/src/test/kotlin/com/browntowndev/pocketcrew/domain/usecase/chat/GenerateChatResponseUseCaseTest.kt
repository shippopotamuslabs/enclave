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
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
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
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


/**
 * Tests for GenerateChatResponseUseCase - Real-Time Flow Architecture.
 * 
 * Tests verify the new architecture where:
 * 1. Flow emits ONLY AccumulatedMessages (not individual events)
 * 2. State is accumulated internally using StringBuilder
 * 3. Persistence to DB happens on completion (not per-event)
 * 4. buffer(64) is used for backpressure
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class GenerateChatResponseUseCaseTest {

    private lateinit var fastModelService: LlmInferencePort
    private lateinit var thinkingModelService: LlmInferencePort
    private lateinit var inferenceFactory: FakeInferenceFactory
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
        inferenceFactory = FakeInferenceFactory().apply {
            serviceMap[ModelType.FAST] = fastModelService
            serviceMap[ModelType.THINKING] = thinkingModelService
        }
        pipelineExecutor = mockk(relaxed = true)
        chatRepository = mockk(relaxed = true)
        messageRepository = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        inferenceLockManager = InferenceLockManagerImpl()
        modelRegistry = mockk(relaxed = true)
        
        generateChatResponseUseCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = pipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry
        )
    }

    // ========================================================================
    // Test: Flow emits ONLY AccumulatedMessages (not individual states)
    // Evidence: New architecture emits accumulated state only
    // ========================================================================

    @Test
    fun `FAST mode emits AccumulatedMessages with accumulated content`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Thinking...", ModelType.FAST),
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - should emit AccumulatedMessages
        assertTrue(accumulatedMessages.isNotEmpty(), "Should emit at least one AccumulatedMessages")
        
        // Verify final AccumulatedMessages has accumulated content
        val finalState = accumulatedMessages.lastOrNull()
        assertNotNull(finalState)
        val snapshot = finalState!!.messages[2L]
        assertNotNull(snapshot)
        assertEquals("Hello world!", snapshot!!.content)
    }

    // ========================================================================
    // Test: Flow accumulates content across events
    // Evidence: StringBuilder updates are accumulated correctly
    // ========================================================================

    @Test
    fun `content is accumulated across multiple PartialResponse events`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
            InferenceEvent.PartialResponse(" world", ModelType.FAST),
            InferenceEvent.PartialResponse("!", ModelType.FAST)
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - each emission should have progressively more content
        val firstState = accumulatedMessages.find { it.messages[2L]?.content == "Hello" }
        val secondState = accumulatedMessages.find { it.messages[2L]?.content == "Hello world" }
        val thirdState = accumulatedMessages.find { it.messages[2L]?.content == "Hello world!" }

        assertNotNull(firstState)
        assertNotNull(secondState)
        assertNotNull(thirdState)
    }

    // ========================================================================
    // Test: Thinking content is accumulated
    // Evidence: Thinking raw content builds up over time
    // ========================================================================

    @Test
    fun `thinking content is accumulated across multiple Thinking events`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Step 1...", ModelType.FAST),
            InferenceEvent.Thinking("Step 2...", ModelType.FAST)
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - thinking raw should be accumulated
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertTrue(snapshot!!.thinkingRaw.contains("Step 1"))
        assertTrue(snapshot.thinkingRaw.contains("Step 2"))
    }

    // ========================================================================
    // Test: isComplete is set when inference finishes
    // Evidence: Completion is tracked correctly
    // ========================================================================

    @Test
    fun `isComplete is set to true when inference finishes`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - final state should have isComplete = true
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertTrue(snapshot!!.isComplete)
    }

    // ========================================================================
    // Test: Inference completes without error
    // Evidence: Error handling in flow transformation
    // ========================================================================

    @Test
    fun `inference completes without error`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.PartialResponse("Hello", ModelType.FAST),
            InferenceEvent.Finished(ModelType.FAST)
        )
        coEvery { messageRepository.getMessagesForChat(any()) } returns emptyList()

        // When - should not throw
        generateChatResponseUseCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.FAST
        ).collect { }

        // Then - no assertion needed, just verify no exception
        assertTrue(true)
    }

    // ========================================================================
    // Test: Error state is handled gracefully
    // Evidence: Error in inference doesn't crash the flow
    // ========================================================================

    @Test
    fun `error in inference is handled gracefully`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Error(RuntimeException("Test error"), ModelType.FAST)
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - should still emit AccumulatedMessages with error content
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertTrue(snapshot!!.content.contains("Error"))
    }

    // ========================================================================
    // Test: Processing event sets currentState to PROCESSING
    // Evidence: Processing handler correctly sets accumulator state
    // ========================================================================

    @Test
    fun `Processing event sets currentState to PROCESSING`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) } returns mockConfig

        // Mock pipeline executor to emit Processing event
        val fakePipelineExecutor = FakePipelineExecutor()
        fakePipelineExecutor.addProcessingEvent(ModelType.DRAFT_ONE)
        
        // Create new use case with fake executor
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = fakePipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = inferenceLockManager,
            modelRegistry = modelRegistry
        )

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        useCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - should have at least one emission with PROCESSING state
        assertTrue(accumulatedMessages.isNotEmpty(), "Should emit AccumulatedMessages")
        val processingState = accumulatedMessages.find { 
            it.messages[2L]?.messageState == MessageState.PROCESSING
        }
        assertNotNull(processingState, "Should have a state with currentState = PROCESSING")
        assertEquals(MessageState.PROCESSING, processingState!!.messages[2L]!!.messageState)
    }

    // ========================================================================
    // Test: Processing event sets pipelineStep correctly for each model type
    // Evidence: DRAFT_ONE -> DRAFT_ONE, DRAFT_TWO -> DRAFT_TWO, MAIN -> SYNTHESIS, FINAL_SYNTHESIS -> FINAL
    // ========================================================================

    @Test
    fun `Processing event sets pipelineStep correctly for each model type`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        val testCases = listOf(
            ModelType.DRAFT_ONE to PipelineStep.DRAFT_ONE,
            ModelType.DRAFT_TWO to PipelineStep.DRAFT_TWO,
            ModelType.MAIN to PipelineStep.SYNTHESIS,
            ModelType.FINAL_SYNTHESIS to PipelineStep.FINAL
        )

        for ((modelType, expectedPipelineStep) in testCases) {
            // Given - fresh fake executor for each test case
            val fakePipelineExecutor = FakePipelineExecutor()
            fakePipelineExecutor.addProcessingEvent(modelType)
            
            val expectedMessageId = if (modelType == ModelType.DRAFT_ONE) 2L else 3L
            coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } returns expectedMessageId
            
            val useCase = GenerateChatResponseUseCase(
                inferenceFactory = inferenceFactory,
                pipelineExecutor = fakePipelineExecutor,
                chatRepository = chatRepository,
                messageRepository = messageRepository,
                loggingPort = loggingPort,
                inferenceLockManager = InferenceLockManagerImpl(),
                modelRegistry = modelRegistry
            )

            // When
            val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
            useCase(
                prompt = "Hello",
                userMessageId = 1L,
                assistantMessageId = 2L,
                chatId = 1L,
                mode = Mode.CREW
            ).collect { state ->
                accumulatedMessages.add(state)
            }

            // Then - pipelineStep should match expected
            val processingState = accumulatedMessages.find { 
                it.messages[expectedMessageId]?.messageState == MessageState.PROCESSING
            }
            assertNotNull(processingState, "Should have PROCESSING state for $modelType")
            assertEquals(expectedPipelineStep, processingState!!.messages[expectedMessageId]!!.pipelineStep,
                "pipelineStep should be $expectedPipelineStep for $modelType")
        }
    }

    // ========================================================================
    // Test: Processing event creates new message in CREW mode for non-DRAFT_ONE steps
    // Evidence: createAssistantMessage is called for DRAFT_TWO, SYNTHESIS, FINAL
    // ========================================================================

    @Test
    fun `Processing event creates new message for DRAFT_TWO in CREW mode`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        // Mock createAssistantMessage to return specific IDs
        coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } returns 3L

        val fakePipelineExecutor = FakePipelineExecutor()
        fakePipelineExecutor.addProcessingEvent(ModelType.DRAFT_TWO)
        
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = fakePipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = InferenceLockManagerImpl(),
            modelRegistry = modelRegistry
        )

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        useCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - createAssistantMessage should have been called for DRAFT_TWO
        coVerify { chatRepository.createAssistantMessage(chatId = 1L, userMessageId = 1L, modelType = ModelType.DRAFT_TWO, pipelineStep = PipelineStep.DRAFT_TWO) }
        
        // Verify the accumulator was created for message ID 3L
        val processingState = accumulatedMessages.find { 
            it.messages[3L]?.messageState == MessageState.PROCESSING
        }
        assertNotNull(processingState, "Should have PROCESSING state for DRAFT_TWO message (ID 3)")
    }

    // ========================================================================
    // Test: Processing event creates new message for SYNTHESIS in CREW mode
    // Evidence: createAssistantMessage is called for MAIN model type
    // ========================================================================

    @Test
    fun `Processing event creates new message for SYNTHESIS in CREW mode`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        // Mock createAssistantMessage to return specific IDs
        coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } returns 4L

        val fakePipelineExecutor = FakePipelineExecutor()
        fakePipelineExecutor.addProcessingEvent(ModelType.MAIN)
        
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = fakePipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = InferenceLockManagerImpl(),
            modelRegistry = modelRegistry
        )

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        useCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - createAssistantMessage should have been called for MAIN (SYNTHESIS)
        coVerify { chatRepository.createAssistantMessage(chatId = 1L, userMessageId = 1L, modelType = ModelType.MAIN, pipelineStep = PipelineStep.SYNTHESIS) }
        
        // Verify the accumulator was created for message ID 4L
        val processingState = accumulatedMessages.find { 
            it.messages[4L]?.messageState == MessageState.PROCESSING
        }
        assertNotNull(processingState, "Should have PROCESSING state for SYNTHESIS message (ID 4)")
    }

    // ========================================================================
    // Test: Processing event creates new message for FINAL in CREW mode
    // Evidence: createAssistantMessage is called for FINAL_SYNTHESIS model type
    // ========================================================================

    @Test
    fun `Processing event creates new message for FINAL in CREW mode`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        // Mock createAssistantMessage to return specific IDs
        coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } returns 5L

        val fakePipelineExecutor = FakePipelineExecutor()
        fakePipelineExecutor.addProcessingEvent(ModelType.FINAL_SYNTHESIS)
        
        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = fakePipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = InferenceLockManagerImpl(),
            modelRegistry = modelRegistry
        )

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        useCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - createAssistantMessage should have been called for FINAL_SYNTHESIS
        coVerify { chatRepository.createAssistantMessage(chatId = 1L, userMessageId = 1L, modelType = ModelType.FINAL_SYNTHESIS, pipelineStep = PipelineStep.FINAL) }
        
        // Verify the accumulator was created for message ID 5L
        val processingState = accumulatedMessages.find { 
            it.messages[5L]?.messageState == MessageState.PROCESSING
        }
        assertNotNull(processingState, "Should have PROCESSING state for FINAL message (ID 5)")
    }

    // ========================================================================
    // Test: Complete CREW pipeline emits Processing state before each step
    // Evidence: All 4 steps (DRAFT_ONE, DRAFT_TWO, SYNTHESIS, FINAL) emit Processing
    // ========================================================================

    @Test
    fun `complete CREW pipeline emits Processing state before each step`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        // Mock createAssistantMessage to return sequential IDs
        var nextId = 3L
        coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } answers {
            nextId++
        }

        val fakePipelineExecutor = FakePipelineExecutor()
        fakePipelineExecutor.configureCompleteCrewPipeline()

        val useCase = GenerateChatResponseUseCase(
            inferenceFactory = inferenceFactory,
            pipelineExecutor = fakePipelineExecutor,
            chatRepository = chatRepository,
            messageRepository = messageRepository,
            loggingPort = loggingPort,
            inferenceLockManager = InferenceLockManagerImpl(),
            modelRegistry = modelRegistry
        )

        // When
        val accumulatedMessages = mutableListOf<GenerateChatResponseUseCase.AccumulatedMessages>()
        useCase(
            prompt = "Hello",
            userMessageId = 1L,
            assistantMessageId = 2L,
            chatId = 1L,
            mode = Mode.CREW
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then - verify Processing state appears for each step
        // DRAFT_ONE (message ID 2L)
        val draftOneProcessing = accumulatedMessages.find {
            it.messages[2L]?.messageState == MessageState.PROCESSING &&
            it.messages[2L]?.pipelineStep == PipelineStep.DRAFT_ONE
        }
        assertNotNull(draftOneProcessing, "DRAFT_ONE should emit PROCESSING state")

        // DRAFT_TWO (message ID 3L)
        val draftTwoProcessing = accumulatedMessages.find {
            it.messages[3L]?.messageState == MessageState.PROCESSING &&
            it.messages[3L]?.pipelineStep == PipelineStep.DRAFT_TWO
        }
        assertNotNull(draftTwoProcessing, "DRAFT_TWO should emit PROCESSING state")

        // SYNTHESIS (message ID 4L)
        val synthesisProcessing = accumulatedMessages.find {
            it.messages[4L]?.messageState == MessageState.PROCESSING &&
            it.messages[4L]?.pipelineStep == PipelineStep.SYNTHESIS
        }
        assertNotNull(synthesisProcessing, "SYNTHESIS should emit PROCESSING state")

        // FINAL (message ID 5L)
        val finalProcessing = accumulatedMessages.find {
            it.messages[5L]?.messageState == MessageState.PROCESSING &&
            it.messages[5L]?.pipelineStep == PipelineStep.FINAL
        }
        assertNotNull(finalProcessing, "FINAL should emit PROCESSING state")

        // Verify Processing comes before ThinkingLive for each step
        // Group emissions by message ID
        val draftOneEmissions = accumulatedMessages.filter { it.messages.containsKey(2L) }
        val draftTwoEmissions = accumulatedMessages.filter { it.messages.containsKey(3L) }

        // DRAFT_ONE: first Processing then Thinking
        val draftOneFirstProcessingIdx = draftOneEmissions.indexOfFirst {
            it.messages[2L]?.messageState == MessageState.PROCESSING
        }
        val draftOneFirstThinkingIdx = draftOneEmissions.indexOfFirst {
            it.messages[2L]?.messageState == MessageState.THINKING
        }
        assertTrue(draftOneFirstProcessingIdx >= 0, "DRAFT_ONE should have Processing emission")
        assertTrue(draftOneFirstThinkingIdx > draftOneFirstProcessingIdx,
            "DRAFT_ONE Processing should come before Thinking")
    }

    // ========================================================================
    // Test: pipelineStep is correctly computed and persisted for each model type
    // Evidence: FAST mode uses FINAL step, CREW mode maps correctly (DRAFT_ONE, DRAFT_TWO, SYNTHESIS, FINAL)
    // ========================================================================

    @Test
    fun `pipelineStep is correctly persisted for FAST mode`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
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

        // Then - pipelineStep should be FINAL for FAST mode (default fallback)
        coVerify {
            chatRepository.persistAllMessageData(
                messageId = 2L,
                modelType = ModelType.FAST,
                thinkingStartTime = any(),
                thinkingEndTime = any(),
                thinkingDuration = any(),
                thinkingRaw = any(),
                content = any(),
                messageState = any(),
                pipelineStep = PipelineStep.FINAL
            )
        }
    }

    @Test
    fun `pipelineStep is correctly computed from modelType for CREW mode`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(any()) } returns mockConfig

        val testCases = listOf(
            ModelType.DRAFT_ONE to PipelineStep.DRAFT_ONE,
            ModelType.DRAFT_TWO to PipelineStep.DRAFT_TWO,
            ModelType.MAIN to PipelineStep.SYNTHESIS,
            ModelType.FINAL_SYNTHESIS to PipelineStep.FINAL
        )

        for ((modelType, expectedPipelineStep) in testCases) {
            // Given - fresh fake executor for each test case
            val fakePipelineExecutor = FakePipelineExecutor()
            fakePipelineExecutor.addProcessingEvent(modelType)
            fakePipelineExecutor.addThinkingLiveEvent("Thinking...", modelType)
            fakePipelineExecutor.addGeneratingTextEvent("Content", modelType)
            fakePipelineExecutor.addFinishedEvent(modelType)

            // Mock createAssistantMessage to return specific IDs
            coEvery { chatRepository.createAssistantMessage(any(), any(), any(), any()) } returns 10L

            val useCase = GenerateChatResponseUseCase(
                inferenceFactory = inferenceFactory,
                pipelineExecutor = fakePipelineExecutor,
                chatRepository = chatRepository,
                messageRepository = messageRepository,
                loggingPort = loggingPort,
                inferenceLockManager = InferenceLockManagerImpl(),
                modelRegistry = modelRegistry
            )

            // When
            useCase(
                prompt = "Hello",
                userMessageId = 1L,
                assistantMessageId = 2L,
                chatId = 1L,
                mode = Mode.CREW
            ).collect { }

            // Then - verify pipelineStep is correctly computed from modelType
            coVerify {
                chatRepository.persistAllMessageData(
                    messageId = any(),
                    modelType = modelType,
                    thinkingStartTime = any(),
                    thinkingEndTime = any(),
                    thinkingDuration = any(),
                    thinkingRaw = any(),
                    content = any(),
                    messageState = any(),
                    pipelineStep = expectedPipelineStep
                )
            }

            // Reset for next iteration
            clearMocks(chatRepository)
        }
    }

    // ========================================================================

    // ========================================================================
    // Test: toSnapshot maps properties correctly and calculates thinkingDurationSeconds
    // Evidence: Properties in AccumulatedMessages.MessageSnapshot match expected values
    // ========================================================================

    @Test
    fun `toSnapshot maps properties correctly and calculates thinkingDurationSeconds`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        // Wait, System.currentTimeMillis() was causing StackOverflowError due to mockkStatic.
        // Instead of mockkStatic, let's just use the real time and verify duration is correctly calculated
        // based on thinkingStartTime and thinkingEndTime within the AccumulatedMessages response.

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Think 1 ", ModelType.FAST),
            InferenceEvent.PartialResponse("Response 1 ", ModelType.FAST),
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then
        assertTrue(accumulatedMessages.isNotEmpty())

        // The last emission should be for Finished
        val finalState = accumulatedMessages.lastOrNull()
        assertNotNull(finalState)
        val snapshot = finalState!!.messages[2L]
        assertNotNull(snapshot)

        assertEquals(2L, snapshot!!.messageId)
        assertEquals(ModelType.FAST, snapshot.modelType)
        assertEquals("Response 1 ", snapshot.content)
        assertEquals("Think 1 ", snapshot.thinkingRaw)

        assertTrue(snapshot.thinkingStartTime > 0)
        assertTrue(snapshot.thinkingEndTime >= snapshot.thinkingStartTime)
        val expectedDuration = (snapshot.thinkingEndTime - snapshot.thinkingStartTime) / 1000
        assertEquals(expectedDuration, snapshot.thinkingDurationSeconds)

        assertTrue(snapshot.isComplete)
        assertEquals(MessageState.COMPLETE, snapshot.messageState)
        assertEquals(PipelineStep.FINAL, snapshot.pipelineStep)
    }

    @Test
    fun `toSnapshot handles zero thinking duration correctly`() = runTest {
        // Given
        val mockConfig = mockk<ModelConfiguration>()
        every { modelRegistry.getRegisteredModelSync(ModelType.FAST) } returns mockConfig

        // Emitting ONLY Thinking events means thinkingEndTime remains null
        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.Thinking("Quick think", ModelType.FAST)
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
        ).collect { state ->
            accumulatedMessages.add(state)
        }

        // Then
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = finalState!!.messages[2L]
        assertNotNull(snapshot)

        // Starts thinking, but hasn't ended, so duration should be 0 because endTime is null
        assertTrue(snapshot!!.thinkingStartTime > 0)
        assertEquals(0L, snapshot.thinkingEndTime) // Snapshot constructor defaults null to 0L
        assertEquals(0L, snapshot.thinkingDurationSeconds) // Backing property correctly calculates 0
        assertFalse(snapshot.isComplete)
        assertEquals(MessageState.THINKING, snapshot.messageState)
    }
}
