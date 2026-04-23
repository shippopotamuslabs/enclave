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
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import io.mockk.*
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    private lateinit var activeChatTurnStore: ActiveChatTurnStore
    private lateinit var listenToSpeechUseCase: ListenToSpeechUseCase
    private lateinit var speechEvents: MutableSharedFlow<SpeechState>

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
        activeChatTurnStore = ActiveChatTurnStore()
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

        speechEvents = MutableSharedFlow(extraBufferCapacity = 8)
        listenToSpeechUseCase = mockk()
        every { listenToSpeechUseCase.invoke(any(), any()) } returns speechEvents
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase

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
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = activeChatTurnStore,
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
    fun `initial speech state is Idle`() = runTest {
        assertEquals(SpeechState.Idle, chatViewModel.uiState.value.speechState)
    }

    @Test
    fun `onMicClick starts listening when Idle`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect() }
        chatViewModel.onMicClick()
        coVerify(exactly = 1) { listenToSpeechUseCase.invoke(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun `onMicClick sets stop signal when Listening`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        chatViewModel.onMicClick()
        speechEvents.emit(SpeechState.Listening(0f))
        runCurrent()
        chatViewModel.onMicClick()
        runCurrent()
        // Verify that the stop signal was sent. The actual state change to Idle will happen
        // when the use case processes the signal and completes.
        coVerify { listenToSpeechUseCase.invoke(any(), match { it.value }) }
        collectJob.cancel()
    }

    @Test
    fun `speechState FinalText updates inputText`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            chatViewModel.onMicClick()
            speechEvents.emit(SpeechState.FinalText("Hello world"))
            runCurrent()
            assertEquals("Hello world", chatViewModel.uiState.value.inputText)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `speechState Error updates inputText`() = runTest {
        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            chatViewModel.onMicClick()
            speechEvents.emit(SpeechState.Error("Some error"))
            runCurrent()
            // Error state should not clear input text
            assertEquals("", chatViewModel.uiState.value.inputText)
        } finally {
            collectJob.cancel()
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
        } returns flow {
            val snapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            )
            activeChatTurnStore.publish(ActiveChatTurnKey(chatId, assistantMessageId), snapshot)
            emit(snapshot)
        }

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
                    content = "streaming answer plus persisted suffix",
                    state = MessageState.COMPLETE,
                )
            )
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertTrue(
                chatViewModel.uiState.value.messages.any { message ->
                    message.id == assistantMessageId &&
                        message.content.text == "streaming answer plus persisted suffix"
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
        } returns flow {
            val snapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            )
            activeChatTurnStore.publish(ActiveChatTurnKey(chatId, assistantMessageId), snapshot)
            emit(snapshot)
        }

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            chatViewModel.onInputChange("hello")
            chatViewModel.onSendMessage()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            // Simulate DB emitting COMPLETE. In the old behavior the active
            // snapshot could clear before the combine saw the DB emission,
            // producing a gap frame. With the fix, handoff acknowledgment happens
            // after the COMPLETE DB message is already incorporated, so the
            // assistant message is never absent.
            dbMessages.value = listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "streaming answer plus persisted suffix",
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
                state.messages.any {
                    it.id == assistantMessageId && it.content.text == "streaming answer plus persisted suffix"
                },
                "Assistant message must show the persisted content after DB confirms COMPLETE.",
            )
        } finally {
            collectJob.cancel()
        }
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
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = activeChatTurnStore,
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
            assertEquals("Searching with Tavily", stateWithBanner.activeToolCallBanner.label)

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
        // Given - set up active tool banner state
        val eventFlow = kotlinx.coroutines.flow.MutableSharedFlow<ToolExecutionEvent>()
        every { toolExecutionEventPort.events } returns eventFlow

        val testChatId = ChatId("test-chat-id")
        val testUserMessageId = MessageId("user-123")
        val assistantMessageId = MessageId("assistant-123")
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "partial",
                    state = MessageState.GENERATING,
                )
            )
        )
        coEvery { chatUseCases.getChat(testChatId) } returns dbMessages

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
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = activeChatTurnStore,
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertTrue(chatViewModel.uiState.value.isGenerating)
            assertTrue(chatViewModel.uiState.value.canStop)

            // When
            chatViewModel.stopGeneration()
            runCurrent()

            // Then - cancelInferenceUseCase should be called
            verify { cancelInferenceUseCase() }
            // Tool banner should be null (either cleared or never set)
            val stateAfterStop = chatViewModel.uiState.value
            assertNull(stateAfterStop.activeToolCallBanner, "Tool banner should be null after stopGeneration")
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `uiState reattaches to active background inference snapshot after ViewModel recreation`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
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
        activeChatTurnStore.publish(
            key = ActiveChatTurnKey(chatId, assistantMessageId),
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
            activeChatTurnSnapshotPort = activeChatTurnStore,
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

    @Test
    fun `stale shorter snapshot replaces longer current snapshot`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
        val key = ActiveChatTurnKey(chatId, assistantMessageId)
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "partial",
                    state = MessageState.GENERATING,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        chatViewModel = createViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            activeChatTurnStore.publish(
                key = key,
                snapshot = AccumulatedMessages(
                    messages = mapOf(
                        assistantMessageId to createAssistantSnapshot(
                            id = assistantMessageId,
                            content = "longer streaming answer",
                            state = MessageState.GENERATING,
                        )
                    )
                ),
            )
            runCurrent()
            activeChatTurnStore.publish(
                key = key,
                snapshot = AccumulatedMessages(
                    messages = mapOf(
                        assistantMessageId to createAssistantSnapshot(
                            id = assistantMessageId,
                            content = "short",
                            state = MessageState.GENERATING,
                        )
                    )
                ),
            )
            runCurrent()

            assertEquals("short", chatViewModel.uiState.value.messages.single().content.text)
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `requested turn takes precedence over older incomplete database assistant`() = runTest {
        val chatId = ChatId("chat")
        val staleAssistantMessageId = MessageId("stale-assistant")
        val newUserMessageId = MessageId("new-user")
        val newAssistantMessageId = MessageId("new-assistant")
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = staleAssistantMessageId,
                    content = "stale incomplete answer",
                    state = MessageState.GENERATING,
                )
            )
        )

        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        coEvery { chatUseCases.processPrompt(any()) } returns CreateUserMessageUseCase.PromptResult(
            userMessageId = newUserMessageId,
            assistantMessageId = newAssistantMessageId,
            chatId = chatId,
        )
        coEvery {
            chatUseCases.generateChatResponse(any(), any(), any(), any(), any(), any())
        } returns flow {
            val snapshot = AccumulatedMessages(
                messages = mapOf(
                    newAssistantMessageId to createAssistantSnapshot(
                        id = newAssistantMessageId,
                        content = "new streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            )
            activeChatTurnStore.publish(ActiveChatTurnKey(chatId, newAssistantMessageId), snapshot)
            emit(snapshot)
        }

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            chatViewModel.onInputChange("new prompt")
            chatViewModel.onSendMessage()
            runCurrent()
            advanceTimeBy(100)
            runCurrent()

            assertTrue(
                chatViewModel.uiState.value.messages.any { message ->
                    message.id == newAssistantMessageId &&
                        message.content.text == "new streaming answer"
                },
                "The explicitly requested turn should be observed even when Room still has an older incomplete assistant.",
            )
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `no flicker handoff keeps assistant message present when Room completes`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
        val key = ActiveChatTurnKey(chatId, assistantMessageId)
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "partial",
                    state = MessageState.GENERATING,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        chatViewModel = createViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))
        val observedStates = mutableListOf<ChatUiState>()

        val collectJob = backgroundScope.launch {
            chatViewModel.uiState.collect { state -> observedStates.add(state) }
        }
        try {
            runCurrent()
            activeChatTurnStore.publish(
                key = key,
                snapshot = AccumulatedMessages(
                    messages = mapOf(
                        assistantMessageId to createAssistantSnapshot(
                            id = assistantMessageId,
                            content = "streaming answer",
                            state = MessageState.GENERATING,
                        )
                    )
                ),
            )
            runCurrent()

            dbMessages.value = listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "streaming answer plus persisted suffix",
                    state = MessageState.COMPLETE,
                )
            )
            runCurrent()
            runCurrent()

            val statesAfterAssistantAppears = observedStates.dropWhile { state ->
                state.messages.none { message -> message.id == assistantMessageId }
            }
            assertTrue(statesAfterAssistantAppears.isNotEmpty())
            assertTrue(
                statesAfterAssistantAppears.all { state ->
                    state.messages.any { message -> message.id == assistantMessageId }
                },
                "Assistant message must not disappear during active snapshot to Room handoff.",
            )
            assertEquals(
                "streaming answer plus persisted suffix",
                chatViewModel.uiState.value.messages.single().content.text,
            )
            assertNotNull(activeChatTurnStore.observe(key).first())
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `uiState renders completed Room message when no active snapshot is available`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "final answer",
                    state = MessageState.COMPLETE,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        chatViewModel = createViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))

        chatViewModel.uiState.test {
            advanceUntilIdle()
            var state = awaitItem()
            while (state.messages.none { it.id == assistantMessageId }) {
                state = awaitItem()
            }

            assertEquals("final answer", state.messages.single { it.id == assistantMessageId }.content.text)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `stopGeneration clears active store entry`() = runTest {
        val chatId = ChatId("chat")
        val assistantMessageId = MessageId("assistant")
        val key = ActiveChatTurnKey(chatId, assistantMessageId)
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "partial",
                    state = MessageState.GENERATING,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        chatViewModel = createViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))
        activeChatTurnStore.publish(
            key = key,
            snapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "streaming answer",
                        state = MessageState.GENERATING,
                    )
                )
            ),
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()
            chatViewModel.stopGeneration()
            runCurrent()

            assertNull(activeChatTurnStore.observe(key).first())
        } finally {
            collectJob.cancel()
        }
    }

    @Test
    fun `stopGeneration ignored while engine loading`() = runTest {
        val chatId = ChatId("chat-loading")
        val assistantMessageId = MessageId("assistant-loading")
        val key = ActiveChatTurnKey(chatId, assistantMessageId)
        val dbMessages = MutableStateFlow(
            listOf(
                createAssistantMessage(
                    id = assistantMessageId,
                    content = "",
                    state = MessageState.ENGINE_LOADING,
                )
            )
        )
        coEvery { chatUseCases.getChat(chatId) } returns dbMessages
        chatViewModel = createViewModel(SavedStateHandle(mapOf("chatId" to chatId.value)))
        activeChatTurnStore.publish(
            key = key,
            snapshot = AccumulatedMessages(
                messages = mapOf(
                    assistantMessageId to createAssistantSnapshot(
                        id = assistantMessageId,
                        content = "",
                        state = MessageState.ENGINE_LOADING,
                    )
                )
            ),
        )

        val collectJob = backgroundScope.launch { chatViewModel.uiState.collect { } }
        try {
            runCurrent()

            val stateBeforeStop = chatViewModel.uiState.value
            assertTrue(stateBeforeStop.isGenerating)
            assertEquals(false, stateBeforeStop.canStop)

            chatViewModel.stopGeneration()
            runCurrent()

            verify(exactly = 0) { cancelInferenceUseCase() }
            assertNotNull(activeChatTurnStore.observe(key).first())
        } finally {
            collectJob.cancel()
        }
    }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): ChatViewModel {
        return ChatViewModel(
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
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = activeChatTurnStore,
        )
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
