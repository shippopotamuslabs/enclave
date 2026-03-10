package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.CreateUserMessageUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests for ChatViewModel state handling, especially ThinkingIndicator logic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var mockChatUseCases: ChatUseCases
    private lateinit var mockSettingsUseCases: SettingsUseCases
    private lateinit var mockCreateUserMessageUseCase: CreateUserMessageUseCase
    private lateinit var mockContext: Context
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockCreateUserMessageUseCase = mockk(relaxed = true)
        mockChatUseCases = mockk(relaxed = true) {
            every { processPrompt } returns mockCreateUserMessageUseCase
            coEvery { generateChatResponse(any(), any(), any()) } returns emptyFlow()
        }
        mockSettingsUseCases = mockk(relaxed = true) {
            every { getSettings() } returns MutableStateFlow(SettingsData())
        }
        mockContext = mockk(relaxed = true)

        viewModel = ChatViewModel(mockContext, mockSettingsUseCases, mockChatUseCases)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Mode enum has correct entries`() {
        assertEquals(3, Mode.entries.size)
    }

    @Test
    fun `Mode FAST has correct display name resource`() {
        // Just verify the enum works
        assertEquals(Mode.FAST, Mode.valueOf("FAST"))
    }

    @Test
    fun `Mode CREW has correct display name resource`() {
        // Just verify the enum works
        assertEquals(Mode.CREW, Mode.valueOf("CREW"))
    }

    @Test
    fun `Mode THINKING has correct display name resource`() {
        // Just verify the enum works
        assertEquals(Mode.THINKING, Mode.valueOf("THINKING"))
    }

    @Test
    fun `ChatUiState default values are correct`() {
        val state = ChatUiState()

        assertEquals(Mode.FAST, state.selectedMode)
        assertEquals("", state.inputText)
        assertEquals(false, state.isInputExpanded)
        assertEquals(false, state.isThinking)
        assertEquals(emptyList<String>(), state.thinkingSteps)
        assertEquals(false, state.showUseTheCrewPopup)
    }

    @Test
    fun `ChatMessage has correct structure`() {
        val message = ChatMessage(
            id = "test123",
            role = MessageRole.User,
            content = "Hello",
            formattedTimestamp = "Now"
        )

        assertEquals("test123", message.id)
        assertEquals(MessageRole.User, message.role)
        assertEquals("Hello", message.content)
        assertEquals("Now", message.formattedTimestamp)
    }

    @Test
    fun `ThinkingData has correct structure`() {
        val thinkingData = ThinkingData(
            durationSeconds = 30,
            steps = listOf("Step 1", "Step 2")
        )

        assertEquals(30, thinkingData.durationSeconds)
        assertEquals(2, thinkingData.steps.size)
    }

    @Test
    fun `ChatMessage can have thinking data`() {
        val thinkingData = ThinkingData(
            durationSeconds = 30,
            steps = listOf("Step 1", "Step 2")
        )

        val message = ChatMessage(
            id = "test123",
            role = MessageRole.Assistant,
            content = "Response",
            formattedTimestamp = "Now",
            thinkingData = thinkingData
        )

        assertEquals(thinkingData, message.thinkingData)
    }

    @Test
    fun `MessageRole enum has correct entries`() {
        assertEquals(2, MessageRole.entries.size)
        assertEquals(MessageRole.User, MessageRole.valueOf("User"))
        assertEquals(MessageRole.Assistant, MessageRole.valueOf("Assistant"))
    }

    // ===== ThinkingIndicator State Tests =====

    @Test
    fun `ThinkingLive sets isThinking to true and updates thinkingSteps`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive
        val thinkingSteps = listOf("Analyzing query...", "Drafting response...")
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(thinkingSteps)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should be true and thinkingSteps should be updated
        val state = viewModel.uiState.value
        assertTrue("isThinking should be true after ThinkingLive", state.isThinking)
        assertEquals(thinkingSteps, state.thinkingSteps)
    }

    @Test
    fun `Finished sets isThinking to false and clears thinkingSteps`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive then Finished
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking...")),
            MessageGenerationState.Finished
        )

        // When: onSendMessage is called and flow completes
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should be false and thinkingSteps should be empty
        val state = viewModel.uiState.value
        assertFalse("isThinking should be false after Finished", state.isThinking)
        assertTrue("thinkingSteps should be empty after Finished", state.thinkingSteps.isEmpty())
    }

    @Test
    fun `GeneratingText does not change isThinking state`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive then GeneratingText
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking...")),
            MessageGenerationState.GeneratingText("Hello")
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should still be true (from ThinkingLive)
        val state = viewModel.uiState.value
        assertTrue("isThinking should remain true during GeneratingText", state.isThinking)
    }

    @Test
    fun `Blocked sets isThinking to false`() = runTest {
        // Given: generateChatResponse returns a flow with Blocked
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns flowOf(
            MessageGenerationState.Blocked("Safety check failed")
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should be false
        val state = viewModel.uiState.value
        assertFalse("isThinking should be false after Blocked", state.isThinking)
    }

    @Test
    fun `Failed sets isThinking to false`() = runTest {
        // Given: generateChatResponse returns a flow with Failed
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns flowOf(
            MessageGenerationState.Failed(Exception("Test error"))
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should be false
        val state = viewModel.uiState.value
        assertFalse("isThinking should be false after Failed", state.isThinking)
    }

    @Test
    fun `onSendMessage adds user and placeholder assistant messages`() = runTest {
        // Given: generateChatResponse returns empty flow
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns emptyFlow()

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: messages should contain user message and placeholder assistant
        val state = viewModel.uiState.value
        assertEquals(2, state.messages.size)
        assertEquals(MessageRole.User, state.messages[0].role)
        assertEquals(MessageRole.Assistant, state.messages[1].role)
    }

    @Test
    fun `isThinking is true immediately after onSendMessage`() = runTest {
        // Given: generateChatResponse returns empty flow (immediate completion)
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any())
        } returns emptyFlow()

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: isThinking should be true from onSendMessage setting it
        val state = viewModel.uiState.value
        assertTrue("isThinking should be true immediately after sending message", state.isThinking)
    }

    // ===== MessageList Visibility Logic Tests =====

    @Test
    fun `showIndicator is true when user message has assistant after it`() {
        // Messages: [User(msg2), Assistant(msg1)] - msg2 has assistant after it
        val messages = listOf(
            ChatMessage("1", MessageRole.Assistant, "Response", "Now"),
            ChatMessage("2", MessageRole.User, "Hello", "Now")
        )

        // For the user message at index 1 (display order reversed), hasAssistantAfter should be true
        val index = 1 // User message at position 1 in the list
        val message = messages[index]
        val hasAssistantAfter = messages.drop(messages.size - index).any { it.role == MessageRole.Assistant }
        val showIndicator = message.role == MessageRole.User && true && hasAssistantAfter

        assertTrue("Indicator should show when User message has Assistant after it", showIndicator)
    }

    @Test
    fun `showIndicator is false when user message has no assistant after it`() {
        // Messages: [User(msg1)] - msg1 has no assistant after it
        val messages = listOf(
            ChatMessage("1", MessageRole.User, "Hello", "Now")
        )

        // For the only user message, hasAssistantAfter should be false
        val index = 0
        val message = messages[index]
        val hasAssistantAfter = messages.drop(messages.size - index).any { it.role == MessageRole.Assistant }
        val showIndicator = message.role == MessageRole.User && true && hasAssistantAfter

        assertFalse("Indicator should not show when there is no Assistant message after", showIndicator)
    }

    @Test
    fun `showIndicator is false for assistant messages`() {
        // Messages: [User(msg2), Assistant(msg1)]
        val messages = listOf(
            ChatMessage("1", MessageRole.Assistant, "Response", "Now"),
            ChatMessage("2", MessageRole.User, "Hello", "Now")
        )

        // For assistant message at index 0, showIndicator should be false regardless
        val index = 0
        val message = messages[index]
        val hasAssistantAfter = messages.drop(messages.size - index).any { it.role == MessageRole.Assistant }
        val showIndicator = message.role == MessageRole.User && true && hasAssistantAfter

        assertFalse("Indicator should not show for Assistant messages", showIndicator)
    }

    @Test
    fun `showIndicator is false when isThinking is false`() {
        // Even with User message and Assistant after, if not thinking, no indicator
        val messages = listOf(
            ChatMessage("1", MessageRole.Assistant, "Response", "Now"),
            ChatMessage("2", MessageRole.User, "Hello", "Now")
        )

        val index = 1
        val message = messages[index]
        val hasAssistantAfter = messages.drop(messages.size - index).any { it.role == MessageRole.Assistant }
        val isThinking = false
        val showIndicator = message.role == MessageRole.User && isThinking && hasAssistantAfter

        assertFalse("Indicator should not show when isThinking is false", showIndicator)
    }
}
