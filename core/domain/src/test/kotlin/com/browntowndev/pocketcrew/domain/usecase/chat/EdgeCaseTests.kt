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
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.FakeInferenceFactory
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManagerImpl
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


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
    // Test: Empty thinking is handled correctly
    // Evidence: No thinking events doesn't cause issues
    // ========================================================================

    @Test
    fun `empty thinking is handled correctly`() = runTest {
        // Given - response with no thinking
        val mockAsset = mockk<LocalModelAsset>()
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns mockAsset

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

        // Then - should handle empty thinking gracefully
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = requireNotNull(finalState).messages[2L]
        assertEquals("Hello", requireNotNull(snapshot).content)
        assertTrue(snapshot.thinkingRaw.isEmpty())
    }

    // ========================================================================
    // Test: Long responses are handled correctly
    // Evidence: Multiple partial responses accumulate correctly
    // ========================================================================

    @Test
    fun `long responses are handled correctly`() = runTest {
        // Given - response with many partial responses
        val mockAsset = mockk<LocalModelAsset>()
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns mockAsset

        val partialResponses = (1..100).map { i ->
            InferenceEvent.PartialResponse("chunk$i ", ModelType.FAST)
        } + InferenceEvent.Finished(ModelType.FAST)

        every { fastModelService.sendPrompt(any(), any()) } returns flow { partialResponses.forEach { emit(it) } }
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

        // Then - all content should be accumulated
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = requireNotNull(finalState).messages[2L]
        assertTrue(requireNotNull(snapshot).content.contains("chunk1"))
        assertTrue(snapshot.content.contains("chunk100"))
    }

    // ========================================================================
    // Test: Lock is acquired and released correctly
    // Evidence: Lock management works properly
    // ========================================================================

    @Test
    fun `lock is acquired and released correctly`() = runTest {
        // Given
        val mockAsset = mockk<LocalModelAsset>()
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns mockAsset

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

        // Then - lock should be released
        assertFalse(inferenceLockManager.isInferenceBlocked.value)
    }

    // ========================================================================
    // Test: Blocked inference returns blocked content
    // Evidence: Blocked state is handled correctly
    // ========================================================================

    @Test
    fun `blocked inference returns blocked content`() = runTest {
        // Given - lock already held
        inferenceLockManager.acquireLock(InferenceType.ON_DEVICE)

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

        // Then - should return blocked message
        val state = accumulatedMessages.firstOrNull()
        val snapshot = requireNotNull(state).messages[2L]
        assertTrue(requireNotNull(snapshot).content.contains("Another message is in progress"))
    }

    // ========================================================================
    // Test: Safety blocked returns proper message
    // Evidence: Safety blocking is handled correctly
    // ========================================================================

    @Test
    fun `safety blocked returns proper message`() = runTest {
        // Given
        val mockAsset = mockk<LocalModelAsset>()
        coEvery { modelRegistry.getRegisteredAsset(ModelType.FAST) } returns mockAsset

        every { fastModelService.sendPrompt(any(), any()) } returns flowOf(
            InferenceEvent.SafetyBlocked("Content policy violation", ModelType.FAST)
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

        // Then - should contain blocked message
        assertTrue(accumulatedMessages.isNotEmpty())
        val finalState = accumulatedMessages.lastOrNull()
        val snapshot = requireNotNull(finalState).messages[2L]
        assertTrue(requireNotNull(snapshot).content.contains("Blocked"))
    }

    // ========================================================================
    // Test: ON_DEVICE blocks other ON_DEVICE
    // Evidence: Lock acquisition failure is handled correctly
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
