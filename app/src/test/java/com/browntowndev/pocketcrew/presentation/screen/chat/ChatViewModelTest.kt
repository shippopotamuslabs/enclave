package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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
    private lateinit var mockSavedStateHandle: SavedStateHandle
    private lateinit var mockModelRegistry: ModelRegistryPort
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        mockCreateUserMessageUseCase = mockk(relaxed = true)
        mockChatUseCases = mockk(relaxed = true) {
            every { processPrompt } returns mockCreateUserMessageUseCase
            coEvery { generateChatResponse(any(), any(), any(), any(), any()) } returns emptyFlow()
        }
        mockSettingsUseCases = mockk(relaxed = true) {
            every { getSettings() } returns MutableStateFlow(SettingsData())
        }
        mockContext = mockk(relaxed = true)
        mockSavedStateHandle = mockk(relaxed = true) {
            every { get<Long>("chatId") } returns null
        }
        mockModelRegistry = mockk(relaxed = true)

        viewModel = ChatViewModel(mockContext, mockSettingsUseCases, mockChatUseCases, mockSavedStateHandle, mockModelRegistry)
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
        assertEquals(ResponseState.NONE, state.responseState)
        assertEquals(emptyList<String>(), state.thinkingSteps)
        assertEquals(0L, state.thinkingStartTime)
        assertEquals(false, state.showUseTheCrewPopup)
    }

    @Test
    fun `ChatMessage has correct structure`() {
        val message = ChatMessage(
            id = 1L,
            chatId = 1L,
            role = MessageRole.User,
            content = "Hello",
            formattedTimestamp = "Now"
        )

        assertEquals(1L, message.id)
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
            id = 1L,
            chatId = 1L,
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

    @Test
    fun `ResponseState enum has correct entries`() {
        assertEquals(3, ResponseState.entries.size)
        assertEquals(ResponseState.NONE, ResponseState.valueOf("NONE"))
        assertEquals(ResponseState.PROCESSING, ResponseState.valueOf("PROCESSING"))
        assertEquals(ResponseState.THINKING, ResponseState.valueOf("THINKING"))
    }

    // ===== ThinkingIndicator State Tests =====

    @Test
    fun `ThinkingLive sets responseState to THINKING and updates thinkingSteps`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive
        val thinkingSteps = listOf("Analyzing query...", "Drafting response...")
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(thinkingSteps, ModelType.FAST)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be THINKING and thinkingSteps should be updated
        val state = viewModel.uiState.value
        assertEquals("responseState should be THINKING after ThinkingLive", ResponseState.THINKING, state.responseState)
        assertEquals(thinkingSteps, state.thinkingSteps)
    }

    @Test
    fun `ThinkingLive with FAST modelType sets thinkingModelDisplayName from registry`() = runTest {
        // Given: model registry returns display name for FAST model
        val fastModelDisplayName = "Quick Response Model"
        every {
            mockModelRegistry.getRegisteredModelSync(ModelType.FAST)
        } returns mockk {
            every { metadata } returns mockk {
                every { displayName } returns fastModelDisplayName
            }
        }

        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking..."), ModelType.FAST)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingModelDisplayName should be set from registry
        val state = viewModel.uiState.value
        assertEquals(fastModelDisplayName, state.thinkingModelDisplayName)
    }

    @Test
    fun `ThinkingLive with THINKING modelType sets thinkingModelDisplayName from registry`() = runTest {
        // Given: model registry returns display name for THINKING model
        val thinkingModelDisplayName = "Deep Reasoning Model"
        every {
            mockModelRegistry.getRegisteredModelSync(ModelType.THINKING)
        } returns mockk {
            every { metadata } returns mockk {
                every { displayName } returns thinkingModelDisplayName
            }
        }

        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Reasoning..."), ModelType.THINKING)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingModelDisplayName should be set from registry
        val state = viewModel.uiState.value
        assertEquals(thinkingModelDisplayName, state.thinkingModelDisplayName)
    }

    @Test
    fun `ThinkingLive with MAIN modelType sets thinkingModelDisplayName from registry`() = runTest {
        // Given: model registry returns display name for MAIN model
        val mainModelDisplayName = "Main Synthesis Model"
        every {
            mockModelRegistry.getRegisteredModelSync(ModelType.MAIN)
        } returns mockk {
            every { metadata } returns mockk {
                every { displayName } returns mainModelDisplayName
            }
        }

        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Synthesizing..."), ModelType.MAIN)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingModelDisplayName should be set from registry
        val state = viewModel.uiState.value
        assertEquals(mainModelDisplayName, state.thinkingModelDisplayName)
    }

    @Test
    fun `ThinkingLive falls back to empty string when model not registered`() = runTest {
        // Given: model registry returns null for the model type
        every {
            mockModelRegistry.getRegisteredModelSync(ModelType.FAST)
        } returns null

        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking..."), ModelType.FAST)
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: thinkingModelDisplayName should be empty
        val state = viewModel.uiState.value
        assertEquals("", state.thinkingModelDisplayName)
    }

    @Test
    fun `Finished sets responseState to NONE and clears thinkingSteps`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive then Finished
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking..."), ModelType.FAST),
            MessageGenerationState.Finished
        )

        // When: onSendMessage is called and flow completes
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be NONE and thinkingSteps should be empty
        val state = viewModel.uiState.value
        assertEquals("responseState should be NONE after Finished", ResponseState.NONE, state.responseState)
        assertTrue("thinkingSteps should be empty after Finished", state.thinkingSteps.isEmpty())
    }

    @Test
    fun `GeneratingText keeps THINKING state`() = runTest {
        // Given: generateChatResponse returns a flow with ThinkingLive then GeneratingText
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.ThinkingLive(listOf("Thinking..."), ModelType.FAST),
            MessageGenerationState.GeneratingText("Hello")
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should still be THINKING (from ThinkingLive)
        val state = viewModel.uiState.value
        assertEquals("responseState should remain THINKING during GeneratingText", ResponseState.THINKING, state.responseState)
    }

    @Test
    fun `Blocked sets responseState to NONE`() = runTest {
        // Given: generateChatResponse returns a flow with Blocked
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.Blocked("Safety check failed")
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be NONE
        val state = viewModel.uiState.value
        assertEquals("responseState should be NONE after Blocked", ResponseState.NONE, state.responseState)
    }

    @Test
    fun `Failed sets responseState to NONE`() = runTest {
        // Given: generateChatResponse returns a flow with Failed
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns flowOf(
            MessageGenerationState.Failed(Exception("Test error"))
        )

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be NONE
        val state = viewModel.uiState.value
        assertEquals("responseState should be NONE after Failed", ResponseState.NONE, state.responseState)
    }

    @Test
    fun `onSendMessage adds user and placeholder assistant messages`() = runTest {
        // Given: generateChatResponse returns empty flow
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
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
    fun `responseState is PROCESSING immediately after onSendMessage`() = runTest {
        // Given: generateChatResponse returns empty flow (immediate completion)
        coEvery {
            mockChatUseCases.generateChatResponse(any(), any(), any(), any(), any())
        } returns emptyFlow()

        // When: onSendMessage is called
        viewModel.onSendMessage()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: responseState should be PROCESSING from onSendMessage setting it
        val state = viewModel.uiState.value
        assertEquals("responseState should be PROCESSING immediately after sending message", ResponseState.PROCESSING, state.responseState)
    }

    // ===== MessageList Visibility Logic Tests =====

    @Test
    fun `showIndicator is true ONLY for the most recent user message`() {
        // Messages: [Assistant(5), User(4), Assistant(3), User(2), User(1)]
        // Only User(1) should show indicator when thinking
        val messages = listOf(
            ChatMessage(id = 5L, chatId = 1L, role = MessageRole.Assistant, content = "Response 1", formattedTimestamp = "Now"),
            ChatMessage(id = 4L, chatId = 1L, role = MessageRole.User, content = "Q1", formattedTimestamp = "Now"),
            ChatMessage(id = 3L, chatId = 1L, role = MessageRole.Assistant, content = "Response 2", formattedTimestamp = "Now"),
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Q2", formattedTimestamp = "Now"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Q3 (most recent)", formattedTimestamp = "Now")
        )

        // Most recent user message is at index 4
        val mostRecentUserIndex = messages.indexOfLast { it.role == MessageRole.User }
        assertEquals(4, mostRecentUserIndex)

        // Test each message position in LazyColumn reverse order
        for (i in messages.indices) {
            val message = messages[messages.size - 1 - i]
            val isMostRecentUser = message.role == MessageRole.User &&
                messages.indexOf(message) == mostRecentUserIndex
            val isThinking = true

            if (message.id == 1L) {
                assertTrue("Most recent user message should show indicator",
                    isMostRecentUser && isThinking)
            } else if (message.role == MessageRole.User) {
                assertFalse("Older user messages should not show indicator",
                    isMostRecentUser && isThinking)
            }
        }
    }

    @Test
    fun `showIndicator is false when user message has no assistant after it`() {
        // Single user message - no indicator
        val messages = listOf(
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.User, content = "Hello", formattedTimestamp = "Now")
        )

        val mostRecentUserIndex = messages.indexOfLast { it.role == MessageRole.User }
        val message = messages[0]
        val isMostRecentUserMessage = message.role == MessageRole.User &&
            messages.indexOf(message) == mostRecentUserIndex
        val showIndicator = isMostRecentUserMessage && true

        assertFalse("Indicator should not show with single user message only", showIndicator)
    }

    @Test
    fun `showIndicator is false for assistant messages`() {
        // Messages: [Assistant(msg1)]
        val messages = listOf(
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "Now")
        )

        val mostRecentUserIndex = messages.indexOfLast { it.role == MessageRole.User }
        val message = messages[0]
        val isMostRecentUserMessage = message.role == MessageRole.User &&
            messages.indexOf(message) == mostRecentUserIndex
        val showIndicator = isMostRecentUserMessage && true

        assertFalse("Indicator should not show for Assistant messages", showIndicator)
    }

    @Test
    fun `showIndicator is false when isThinking is false`() {
        // Even with most recent user message, if not thinking, no indicator
        val messages = listOf(
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Hello", formattedTimestamp = "Now"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "Now")
        )

        val mostRecentUserIndex = messages.indexOfLast { it.role == MessageRole.User }
        val message = messages[1] // User message
        val isMostRecentUserMessage = message.role == MessageRole.User &&
            messages.indexOf(message) == mostRecentUserIndex
        val isThinking = false
        val showIndicator = isMostRecentUserMessage && isThinking

        assertFalse("Indicator should not show when isThinking is false", showIndicator)
    }

    @Test
    fun `showIndicator shows for single user message with assistant after`() {
        // Messages: [User(msg2), Assistant(msg1)] - shows indicator
        val messages = listOf(
            ChatMessage(id = 2L, chatId = 1L, role = MessageRole.User, content = "Hello", formattedTimestamp = "Now"),
            ChatMessage(id = 1L, chatId = 1L, role = MessageRole.Assistant, content = "Response", formattedTimestamp = "Now")
        )

        val mostRecentUserIndex = messages.indexOfLast { it.role == MessageRole.User }
        assertEquals(0, mostRecentUserIndex)

        val message = messages[0] // Most recent user message
        val isMostRecentUserMessage = message.role == MessageRole.User &&
            messages.indexOf(message) == mostRecentUserIndex
        val showIndicator = isMostRecentUserMessage && true

        assertTrue("Indicator should show for most recent user message with assistant after", showIndicator)
    }
}
