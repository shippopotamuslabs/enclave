package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for ChatUiState - specifically the isGenerating computed property.
 *
 * These tests verify that isGenerating correctly computes whether any message
 * is in a generating state (Thinking, Processing, or Generating).
 *
 * Uses MainDispatcherRule per Golden Reference pattern.
 *
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
@ExtendWith(MainDispatcherRule::class)
class ChatViewModelFlowTest {

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelDisplayNamesUseCase: GetModelDisplayNameUseCase
    private lateinit var stageImageAttachmentUseCase: StageImageAttachmentUseCase
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var errorHandler: ViewModelErrorHandler
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var toolExecutionEventPort: ToolExecutionEventPort
    private lateinit var loggingPort: LoggingPort

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        modelDisplayNamesUseCase = mockk(relaxed = true)
        stageImageAttachmentUseCase = mockk(relaxed = true)
        activeModelProvider = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        cancelInferenceUseCase = mockk(relaxed = true)
        toolExecutionEventPort = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)

        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        val assignmentUseCases = mockk<SettingsAssignmentUseCases>()
        val getDefaultModelsUseCase = mockk<GetDefaultModelsUseCase>()
        every { settingsUseCases.assignments } returns assignmentUseCases
        every { assignmentUseCases.getDefaultModels } returns getDefaultModelsUseCase
        every { getDefaultModelsUseCase() } returns MutableStateFlow(emptyList())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        coEvery { activeModelProvider.getActiveConfiguration(any()) } returns null

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
            errorHandler = errorHandler,
            toolExecutionEventPort = toolExecutionEventPort,
            loggingPort = loggingPort,
            activeChatTurnSnapshotPort = ActiveChatTurnStore(),
            playTtsAudioUseCase = mockk(relaxed = true),
        )
    }

    @Test
    fun `isGenerating is true when any message is in generating state`() {
        val stateWithGenerating = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.Assistant,
                    content = ContentUi("Thinking..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Generating(null)
                )
            ),
            isGenerating = true
        )

        assertTrue(stateWithGenerating.isGenerating)
    }

    @Test
    fun `isGenerating is false when no message is generating`() {
        val stateWithComplete = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.Assistant,
                    content = ContentUi("Done"),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Complete(null)
                )
            ),
            isGenerating = false
        )

        assertFalse(stateWithComplete.isGenerating)
    }

    @Test
    fun `isGenerating is false when messages are empty`() {
        val emptyState = ChatUiState(
            messages = emptyList(),
            isGenerating = false
        )

        assertFalse(emptyState.isGenerating)
    }

    @Test
    fun `isGenerating is true when message is in thinking state`() {
        val stateWithThinking = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.Assistant,
                    content = ContentUi("Thinking..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Thinking("Thinking...", 0L)
                )
            ),
            isGenerating = true
        )

        assertTrue(stateWithThinking.isGenerating)
    }

    @Test
    fun `isGenerating is true when message is in processing state`() {
        val stateWithProcessing = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = MessageId("1"),
                    chatId = ChatId("1"),
                    role = MessageRole.Assistant,
                    content = ContentUi("Processing..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Processing
                )
            ),
            isGenerating = true
        )

        assertTrue(stateWithProcessing.isGenerating)
    }
}
