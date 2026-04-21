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

package com.browntowndev.pocketcrew.feature.chat
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.TAVILY_WEB_SEARCH
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.usecase.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageSnapshot
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

private class FakeActiveModelProvider : ActiveModelProviderPort {
    private val configurations = mutableMapOf<ModelType, ActiveModelConfiguration?>()

    fun setConfiguration(modelType: ModelType, configuration: ActiveModelConfiguration?) {
        configurations[modelType] = configuration
    }

    override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? =
        configurations[modelType]
}

/**
 * Tests for ChatViewModel's mapping logic.
 *
 * These tests verify that the indicator state is correctly computed from
 * domain messages, especially handling nullable duration during THINKING state.
 *
 * Uses MainDispatcherRule per Golden Reference pattern.
 *
 * REF: Bug fix - thinkingDurationSeconds was requireNotNull'd but never set during THINKING state
 */
@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelDisplayNamesUseCase: GetModelDisplayNameUseCase
    private lateinit var stageImageAttachmentUseCase: StageImageAttachmentUseCase
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var toolExecutionEventPort: ToolExecutionEventPort
    private lateinit var loggingPort: LoggingPort
    private lateinit var errorHandler: ViewModelErrorHandler

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        stageImageAttachmentUseCase = mockk(relaxed = true)
        cancelInferenceUseCase = mockk(relaxed = true)
        toolExecutionEventPort = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        activeModelProvider = FakeActiveModelProvider()
        activeModelProvider.setConfiguration(
            ModelType.FAST,
            ActiveModelConfiguration(
                id = LocalModelConfigurationId("fast"),
                isLocal = true,
                name = "Fast Vision",
                systemPrompt = "Describe images briefly.",
                reasoningEffort = null,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                minP = 0.0,
                repetitionPenalty = 1.1,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = true,
            )
        )
        activeModelProvider.setConfiguration(
            ModelType.VISION,
            ActiveModelConfiguration(
                id = LocalModelConfigurationId("vision"),
                isLocal = false,
                name = "Vision API",
                systemPrompt = "Describe images briefly.",
                reasoningEffort = null,
                temperature = 0.7,
                topK = 40,
                topP = 0.95,
                maxTokens = 512,
                minP = 0.0,
                repetitionPenalty = 1.1,
                contextWindow = 4096,
                thinkingEnabled = false,
                isMultimodal = true,
            )
        )
        
        // Stub coroutineExceptionHandler to return a real one to avoid ClassCastException with MockK
        every { errorHandler.coroutineExceptionHandler(any(), any(), any()) } returns CoroutineExceptionHandler { _, _ -> }
        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { chatUseCases.mergeMessagesUseCase } returns MergeMessagesUseCase()
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        // Create a simple fake for GetModelDisplayNameUseCase
        modelDisplayNamesUseCase = mockk(relaxed = true)
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { toolExecutionEventPort.events } returns MutableSharedFlow()

        val savedStateHandle = SavedStateHandle()

        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = cancelInferenceUseCase,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort
        )
    }

    @Test
    fun `onSendMessage error invokes errorHandler`() = runTest {
        // Given
        val input = "Hello"
        chatViewModel.onInputChange(input)
        
        val exception = IOException("Network error")
        coEvery { chatUseCases.processPrompt(any()) } throws exception
        
        // When
        chatViewModel.onSendMessage()
        
        // Then
        // Verify coroutineExceptionHandler was called with correct parameters
        verify {
            errorHandler.coroutineExceptionHandler(
                tag = "ChatViewModel",
                message = "Failed to send message",
                userMessage = "Could not send message. Please try again."
            )
        }
    }

    @Test
    fun `onSendMessage allows image only messages`() = runTest {
        coEvery { stageImageAttachmentUseCase.invoke(any()) } returns "file:///tmp/cached.jpg"
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            chatId = ChatId("chat"),
        )
        coEvery { chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any()) } returns kotlinx.coroutines.flow.emptyFlow()

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            chatViewModel.onImageSelected("content://picked/image")
            advanceUntilIdle()

            coVerify(exactly = 1) {
                stageImageAttachmentUseCase("content://picked/image")
            }

            chatViewModel.onSendMessage()
            advanceUntilIdle()

            coVerify {
                chatUseCases.processPrompt(
                    match {
                        it.content.imageUri == "file:///tmp/cached.jpg" &&
                            it.content.text.isBlank()
                    }
                )
            }
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `onSendMessage defaults to background inference routing`() = runTest {
        val settingsFlow = MutableStateFlow(SettingsData())
        every { settingsUseCases.getSettings() } returns settingsFlow
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = MessageId("user"),
            assistantMessageId = MessageId("assistant"),
            chatId = ChatId("chat"),
        )
        coEvery {
            chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any())
        } returns kotlinx.coroutines.flow.emptyFlow()

        chatViewModel.onInputChange("hello")
        chatViewModel.onSendMessage()
        advanceUntilIdle()

        coVerify {
            chatUseCases.generateChatResponse(
                prompt = "hello",
                userMessageId = MessageId("user"),
                assistantMessageId = MessageId("assistant"),
                chatId = ChatId("chat"),
                mode = any(),
                backgroundInferenceEnabled = true,
            )
        }
    }

    @Test
    fun `onSendMessage keeps in-flight response visible until database confirms complete`() = runTest {
        val chatId = ChatId("chat")
        val userMessageId = MessageId("user")
        val assistantMessageId = MessageId("assistant")
        val dbMessages = MutableStateFlow<List<Message>>(emptyList())

        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
        )
        coEvery {
            chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any())
        } returns flowOf(
            AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            )
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            chatViewModel.onInputChange("hello")
            chatViewModel.onSendMessage()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertTrue(
                chatViewModel.uiState.value.messages.any { message ->
                    message.id == assistantMessageId && message.content.text == "streaming answer"
                },
                "In-flight assistant response should remain visible after generation flow completion.",
            )

            dbMessages.value = listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "final answer",
                    state = MessageState.COMPLETE,
                )
            )
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertTrue(
                chatViewModel.uiState.value.messages.any { message ->
                    message.id == assistantMessageId && message.content.text == "final answer"
                },
                "Persisted COMPLETE assistant response should replace the in-flight response.",
            )
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `no flicker gap when in-flight transitions to database complete`() = runTest {
        val chatId = ChatId("chat")
        val userMessageId = MessageId("user")
        val assistantMessageId = MessageId("assistant")
        val dbMessages = MutableStateFlow<List<Message>>(emptyList())

        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            chatId = chatId,
        )
        coEvery {
            chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any())
        } returns flowOf(
            AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            )
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            chatViewModel.onInputChange("hello")
            chatViewModel.onSendMessage()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            // Simulate DB emitting COMPLETE — in the old (broken) behavior the
            // non-debounced prune observer would clear _inFlightMessages before
            // the combine saw the DB emission, producing a gap frame.
            // With the fix, the prune is triggered inside the combine after it
            // has already incorporated the COMPLETE DB message, so the assistant
            // message is never absent.
            dbMessages.value = listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "final answer",
                    state = MessageState.COMPLETE,
                )
            )

            // Process the DB emission immediately — no debounce delay
            runCurrent()
            // After the combine emits with the COMPLETE DB message, the async
            // prune launches and triggers a second combine emission. Process it.
            runCurrent()
            advanceTimeBy(50)
            runCurrent()

            // At every point the assistant message must have been present
            val state = chatViewModel.uiState.value
            assertTrue(
                state.messages.any { it.id == assistantMessageId },
                "Assistant message must never disappear during in-flight → DB transition.",
            )
            assertTrue(
                state.messages.any { it.id == assistantMessageId && it.content.text == "final answer" },
                "Assistant message must show the persisted content after DB confirms COMPLETE.",
            )
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `pruneCompletedInFlightMessages removes only database confirmed complete ids`() {
        val completeId = MessageId("complete")
        val generatingId = MessageId("generating")
        val missingId = MessageId("missing")
        val current = mapOf(
            completeId to createAssistantSnapshot(completeId, "complete", MessageState.GENERATING),
            generatingId to createAssistantSnapshot(generatingId, "generating", MessageState.GENERATING),
            missingId to createAssistantSnapshot(missingId, "missing", MessageState.GENERATING),
        )

        val result = pruneCompletedInFlightMessages(
            current = current,
            dbMessages = listOf(
                createAssistantMessage(completeId, "complete", MessageState.COMPLETE),
                createAssistantMessage(generatingId, "generating", MessageState.GENERATING),
            ),
        )

        assertFalse(result.containsKey(completeId))
        assertTrue(result.containsKey(generatingId))
        assertTrue(result.containsKey(missingId))
    }

    @Test
    fun `pruneCompletedInFlightMessages retains entries until database message is complete`() {
        val assistantMessageId = MessageId("assistant")
        val current = mapOf(
            assistantMessageId to createAssistantSnapshot(
                id = assistantMessageId,
                content = "streaming answer",
                state = MessageState.GENERATING,
            )
        )

        val result = pruneCompletedInFlightMessages(
            current = current,
            dbMessages = listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "",
                    state = MessageState.PROCESSING,
                )
            ),
        )

        assertEquals(current, result)
    }

    // ========================================================================
    // Bug Fix Test: Nullable duration during THINKING state
    // The app was crashing with IllegalArgumentException: Required value was null
    // when computeIndicatorState was called with a message in THINKING state
    // that had no thinkingDurationSeconds set.
    // ========================================================================

    @Test
    fun `computeIndicatorState THINKING with null duration does not crash`() = runTest {
        // Given: A message in THINKING state with null thinkingDurationSeconds
        // This simulates the bug scenario where duration was never set during THINKING
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Thinking..."),
            role = Role.ASSISTANT,
            messageState = MessageState.THINKING,
            thinkingDurationSeconds = null, // This is the bug - null when in THINKING
            thinkingRaw = "Initial thought",
        )

        // When: mapToChatMessage is called (which calls computeIndicatorState)
        // Then: It should NOT throw IllegalArgumentException
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Verify the indicator state is Thinking (not crashed)
        assertNotNull(chatMessage.indicatorState)
        assertEquals(
            IndicatorState.Thinking::class,
            requireNotNull(chatMessage.indicatorState)::class,
            "Indicator state should be Thinking"
        )
        // Verify it has 0 duration when null
        val thinkingState = chatMessage.indicatorState as IndicatorState.Thinking
        assertEquals(0L, thinkingState.thinkingDurationSeconds)
    }

    @Test
    fun `computeIndicatorState THINKING with duration works correctly`() = runTest {
        // Given: A message in THINKING state with duration set
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Thinking..."),
            role = Role.ASSISTANT,
            messageState = MessageState.THINKING,
            thinkingDurationSeconds = 30L,
            thinkingRaw = "Full thought here",
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Thinking with duration
        assertNotNull(chatMessage.indicatorState)
        val thinkingState = chatMessage.indicatorState as IndicatorState.Thinking
        assertEquals(30L, thinkingState.thinkingDurationSeconds)
        assertEquals("Full thought here", thinkingState.thinkingRaw)
    }

    @Test
    fun `computeIndicatorState PROCESSING returns Processing state`() = runTest {
        // Given: A message in PROCESSING state
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Processing..."),
            role = Role.ASSISTANT,
            messageState = MessageState.PROCESSING
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Processing
        assertEquals(IndicatorState.Processing, chatMessage.indicatorState)
    }

    @Test
    fun `computeIndicatorState GENERATING with null duration returns null thinkingData`() = runTest {
        // Given: A message in GENERATING state with null duration
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Generating..."),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            thinkingDurationSeconds = null, // Still thinking
            thinkingRaw = "Raw thought content"
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Generating with null thinkingData
        assertNotNull(chatMessage.indicatorState)
        val generatingState = chatMessage.indicatorState as IndicatorState.Generating
        assertNull(generatingState.thinkingData) // No duration yet
    }

    @Test
    fun `computeIndicatorState GENERATING with duration returns thinkingData`() = runTest {
        // Given: A message in GENERATING state with duration set
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Generating response..."),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            thinkingDurationSeconds = 45L,
            thinkingRaw = "Full thought",
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Generating with thinkingData
        assertNotNull(chatMessage.indicatorState)
        val generatingState = chatMessage.indicatorState as IndicatorState.Generating
        assertNotNull(generatingState.thinkingData)
        assertEquals(45L, requireNotNull(generatingState.thinkingData).thinkingDurationSeconds)
        assertEquals("Full thought", requireNotNull(generatingState.thinkingData).thinkingRaw)
    }

    @Test
    fun `computeIndicatorState COMPLETE with duration returns thinkingData`() = runTest {
        // Given: A message in COMPLETE state with duration set
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Response complete"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            thinkingDurationSeconds = 60L, // Duration is set
            thinkingRaw = "Full thought content",
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Complete with thinkingData
        assertNotNull(chatMessage.indicatorState)
        val completeState = chatMessage.indicatorState as IndicatorState.Complete
        assertNotNull(completeState.thinkingData)
        assertEquals(60L, requireNotNull(completeState.thinkingData).thinkingDurationSeconds)
        assertEquals("Full thought content", requireNotNull(completeState.thinkingData).thinkingRaw)
    }

    @Test
    fun `computeIndicatorState USER returns None`() = runTest {
        // Given: A user message
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "User message"),
            role = Role.USER,
            messageState = MessageState.COMPLETE
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be None
        assertEquals(IndicatorState.None, chatMessage.indicatorState)
    }

    @Test
    fun `computeIndicatorState COMPLETE with null duration returns null thinkingData`() = runTest {
        // Given: A message in COMPLETE state with null duration (edge case)
        val message = Message(
            id = MessageId("1"),
            chatId = ChatId("1"),
            content = Content(text = "Response complete"),
            role = Role.ASSISTANT,
            messageState = MessageState.COMPLETE,
            thinkingDurationSeconds = null,
            thinkingRaw = null
        )

        // When: mapToChatMessage is called
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: Indicator state should be Complete with null thinkingData
        assertNotNull(chatMessage.indicatorState)
        val completeState = chatMessage.indicatorState as IndicatorState.Complete
        assertNull(completeState.thinkingData)
    }

    // ========================================================================
    // Test: pipelineStep is propagated from Message to ChatMessage
    // ========================================================================

    @Test
    fun `mapToChatMessage propagates pipelineStep from Message to ChatMessage`() = runTest {
        // Given: A message with pipelineStep set
        val expectedPipelineStep = PipelineStep.DRAFT_ONE

        val message = Message(
            id = MessageId("2"),
            chatId = ChatId("1"),
            content = Content(text = "Response content", pipelineStep = expectedPipelineStep),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING,
            thinkingDurationSeconds = 5L,
            thinkingRaw = "Raw thought"
        )

        // When: Map to ChatMessage
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: pipelineStep should be propagated
        assertEquals(
            expectedPipelineStep,
            chatMessage.content.pipelineStep,
            "pipelineStep should be propagated from Message to ChatMessage"
        )
    }

    @Test
    fun `mapToChatMessage with null pipelineStep returns null`() = runTest {
        // Given: A message with null pipelineStep
        val message = Message(
            id = MessageId("2"),
            chatId = ChatId("1"),
            content = Content(text = "Response content", pipelineStep = null),
            role = Role.ASSISTANT,
            messageState = MessageState.GENERATING
        )

        // When: Map to ChatMessage
        val chatMessage = chatViewModel.mapToChatMessageForTesting(message)

        // Then: pipelineStep should be null
        assertNull(chatMessage.content.pipelineStep, "pipelineStep should be null when not set in Message")
    }

    @Test
    fun `ToolCallBanner state updates on start and finish`() = runTest {
        val eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<ToolExecutionEvent>()
        every { toolExecutionEventPort.events } returns eventFlow

        val testChatId = ChatId("test-chat-id")
        val testUserMessageId = MessageId("user-123")
        
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to testChatId.value))
        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = cancelInferenceUseCase,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort
        )

        // Give init and observeToolEvents a chance to subscribe
        runCurrent()

        chatViewModel.uiState.test {
            // Allow initial state emission to propagate
            advanceTimeBy(100)
            runCurrent()
            
            // 1. Initial State
            val initialState = awaitItem()
            assertNull(initialState.activeToolCallBanner)

            // 2. Emit Started Event
            val startEvent = ToolExecutionEvent.Started(
                eventId = "tool-1",
                chatId = testChatId,
                userMessageId = testUserMessageId,
                toolName = TAVILY_WEB_SEARCH.name,
                argumentsJson = "{}",
                modelType = com.browntowndev.pocketcrew.domain.model.inference.ModelType.FAST
            )
            eventFlow.emit(startEvent)
            runCurrent() // Crucial: let observeToolEvents process the event
            
            // Should see a new state with the banner. Use a loop/skip to be robust against other updates.
            var stateWithBanner = awaitItem()
            while (stateWithBanner.activeToolCallBanner == null) {
                stateWithBanner = awaitItem()
            }
            
            assertNotNull(stateWithBanner.activeToolCallBanner)
            assertEquals("Searching with Tavily", stateWithBanner.activeToolCallBanner?.label)

            // 3. Emit Finished Event
            val finishEvent = ToolExecutionEvent.Finished(
                eventId = "tool-1",
                chatId = testChatId,
                userMessageId = testUserMessageId
            )
            eventFlow.emit(finishEvent)
            runCurrent()
            
            // Banner should still be present (1s minimum rule)
            // No new state should be emitted yet as banner hasn't changed (job only started)
            assertNotNull(chatViewModel.uiState.value.activeToolCallBanner)

            // 4. Advance time to pass the gate
            advanceTimeBy(1500)
            runCurrent()
            
            // Now the banner should be dismissed
            var finalState = awaitItem()
            while (finalState.activeToolCallBanner != null) {
                 finalState = awaitItem()
            }
            assertNull(finalState.activeToolCallBanner)
        }
    }

    @Test
    fun `stopGeneration cancels inference and clears tool banner`() = runTest {
        // Given - set up some in-flight messages and tool banner state
        val eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<ToolExecutionEvent>()
        every { toolExecutionEventPort.events } returns eventFlow

        val testChatId = ChatId("test-chat-id")
        val testUserMessageId = MessageId("user-123")

        val savedStateHandle = SavedStateHandle(mapOf("chatId" to testChatId.value))
        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            savedStateHandle = savedStateHandle,
            cancelInferenceUseCase = cancelInferenceUseCase,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort
        )

        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        // When
        chatViewModel.stopGeneration()

        // Then - cancelInferenceUseCase should be called
        verify { cancelInferenceUseCase() }
        // Tool banner should be null (either cleared or never set)
        val stateAfterStop = chatViewModel.uiState.value
        assertNull(stateAfterStop.activeToolCallBanner, "Tool banner should be null after stopGeneration")
    }

    @Test
    fun `uiState reattaches to active background inference snapshot after ViewModel recreation`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
        val eventBus = InferenceEventBus()
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "partial before background",
                    state = MessageState.GENERATING,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        eventBus.emitChatSnapshot(
            key = InferenceEventBus.ChatRequestKey(chatId, assistantMessageId),
            snapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "partial before background plus text streamed while away",
                        state = MessageState.GENERATING,
                    )
                )
            ),
        )

        val recreatedViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            savedStateHandle = SavedStateHandle(mapOf("chatId" to chatId.value)),
            cancelInferenceUseCase = cancelInferenceUseCase,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeModelProvider = activeModelProvider,
            toolExecutionEventPort = toolExecutionEventPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort,
            inferenceEventBus = eventBus,
        )

        val collectJob = backgroundScope.launch { recreatedViewModel.uiState.collect { } }
        try {
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            val state = recreatedViewModel.uiState.value
            assertEquals("partial before background plus text streamed while away", state.messages.single().content.text)
            assertTrue(state.isGenerating)
        } finally {
            collectJob.cancel()
        }
    }

    private fun createAssistantSnapshot(
        id: MessageId,
        content: String,
        state: MessageState,
    ): MessageSnapshot = MessageSnapshot(
        messageId = id,
        modelType = ModelType.FAST,
        content = content,
        thinkingRaw = "",
        messageState = state,
    )

    private fun createAssistantMessage(
        id: MessageId,
        content: String,
        state: MessageState,
    ): Message = Message(
        id = id,
        chatId = ChatId("chat"),
        content = Content(text = content),
        role = Role.ASSISTANT,
        messageState = state,
        createdAt = 1L,
    )
}
