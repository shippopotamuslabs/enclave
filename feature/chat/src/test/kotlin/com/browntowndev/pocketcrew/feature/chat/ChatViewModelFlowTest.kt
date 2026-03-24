package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
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

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        modelDisplayNamesUseCase = mockk(relaxed = true)

        every { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)

        val savedStateHandle = SavedStateHandle()

        chatViewModel = ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase
        )
    }

    @Test
    fun `isGenerating is true when any message is in generating state`() {
        val stateWithGenerating = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = 1L,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi("Thinking..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Generating(null)
                )
            )
        )

        assertTrue(stateWithGenerating.isGenerating)
    }

    @Test
    fun `isGenerating is false when no message is generating`() {
        val stateWithComplete = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = 1L,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi("Done"),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Complete(null)
                )
            )
        )

        assertFalse(stateWithComplete.isGenerating)
    }

    @Test
    fun `isGenerating is false when messages are empty`() {
        val emptyState = ChatUiState(
            messages = emptyList()
        )

        assertFalse(emptyState.isGenerating)
    }

    @Test
    fun `isGenerating is true when message is in thinking state`() {
        val stateWithThinking = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = 1L,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi("Thinking..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Thinking("Thinking...", 0L)
                )
            )
        )

        assertTrue(stateWithThinking.isGenerating)
    }

    @Test
    fun `isGenerating is true when message is in processing state`() {
        val stateWithProcessing = ChatUiState(
            messages = listOf(
                ChatMessage(
                    id = 1L,
                    chatId = 1L,
                    role = MessageRole.Assistant,
                    content = ContentUi("Processing..."),
                    formattedTimestamp = "Now",
                    indicatorState = IndicatorState.Processing
                )
            )
        )

        assertTrue(stateWithProcessing.isGenerating)
    }
}
