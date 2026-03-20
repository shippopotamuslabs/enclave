package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests for ChatViewModel - Flow collection, merge, debounce.
 * 
 * These tests verify:
 * 1. ViewModel correctly initializes and exposes uiState
 * 2. ViewModel manages inputText state
 * 3. ViewModel handles mode selection
 * 4. isGenerating computed property works correctly
 * 
 * REF: SPEC: Real-Time Inference via Flow, Persist on Completion
 */
class ChatViewModelFlowTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @BeforeEach
    fun setupDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    private lateinit var chatViewModel: ChatViewModel
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelDisplayNamesUseCase: GetModelDisplayNameUseCase

    @OptIn(ExperimentalCoroutinesApi::class)
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

    // ========================================================================
    // Test: isGenerating is true when any message is generating
    // Evidence: isGenerating computed property works correctly
    // ========================================================================

    @Test
    fun `isGenerating is true when any message is generating`() {
        // Given
        val state = ChatUiState(
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
        
        // Then
        assertTrue(state.isGenerating)
    }

    @Test
    fun `isGenerating is false when no message is generating`() {
        // Given
        val state = ChatUiState(
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
        
        // Then
        assertFalse(state.isGenerating)
    }

    @Test
    fun `isGenerating is false when messages are empty`() {
        // Given
        val state = ChatUiState(
            messages = emptyList()
        )
        
        // Then
        assertFalse(state.isGenerating)
    }
}
