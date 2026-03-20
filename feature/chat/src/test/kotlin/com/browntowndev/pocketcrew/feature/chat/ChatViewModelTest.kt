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
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

/**
 * Tests for ChatViewModel's computeIndicatorState function.
 *
 * These tests verify that the indicator state is correctly computed from
 * domain messages, especially handling nullable duration during THINKING state.
 *
 * REF: Bug fix - thinkingDurationSeconds was requireNotNull'd but never set during THINKING state
 */
class ChatViewModelTest {

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

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)

        // Create a simple fake for GetModelDisplayNameUseCase
        modelDisplayNamesUseCase = mockk(relaxed = true)
        every { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"

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
            id = 1L,
            chatId = 1L,
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
            chatMessage.indicatorState!!::class,
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
            id = 1L,
            chatId = 1L,
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
            id = 1L,
            chatId = 1L,
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
            id = 1L,
            chatId = 1L,
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
            id = 1L,
            chatId = 1L,
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
        assertEquals(45L, generatingState.thinkingData!!.thinkingDurationSeconds)
        assertEquals("Full thought", generatingState.thinkingData!!.thinkingRaw)
    }

    @Test
    fun `computeIndicatorState COMPLETE with duration returns thinkingData`() = runTest {
        // Given: A message in COMPLETE state with duration set
        val message = Message(
            id = 1L,
            chatId = 1L,
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
        assertEquals(60L, completeState.thinkingData!!.thinkingDurationSeconds)
        assertEquals("Full thought content", completeState.thinkingData!!.thinkingRaw)
    }

    @Test
    fun `computeIndicatorState USER returns None`() = runTest {
        // Given: A user message
        val message = Message(
            id = 1L,
            chatId = 1L,
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
            id = 1L,
            chatId = 1L,
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
}
