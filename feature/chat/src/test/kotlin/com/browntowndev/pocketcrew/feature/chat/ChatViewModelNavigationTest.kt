package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ChatViewModelNavigationTest {

    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelDisplayNamesUseCase: GetModelDisplayNameUseCase
    private lateinit var errorHandler: ViewModelErrorHandler

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        modelDisplayNamesUseCase = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)

        every { settingsUseCases.getSettings() } returns flowOf(SettingsData())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { chatUseCases.mergeMessagesUseCase(any(), any()) } answers { firstArg() }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): ChatViewModel {
        return ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            errorHandler = errorHandler
        )
    }

    @Test
    fun `initialChatId parses valid string from SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "42"))
        val viewModel = createViewModel(savedStateHandle)

        assertEquals(42L, viewModel.initialChatId)
    }

    @Test
    fun `initialChatId returns null for invalid string in SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "invalid_id"))
        val viewModel = createViewModel(savedStateHandle)

        assertNull(viewModel.initialChatId)
    }

    @Test
    fun `initialChatId returns null when missing from SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)

        assertNull(viewModel.initialChatId)
    }

    @Test
    fun `uiState emits current chatId when initialized with valid id`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "42"))
        
        val messages = listOf(
            Message(id = 1L, chatId = 42L, content = Content("Hello"), role = Role.USER)
        )
        coEvery { chatUseCases.getChat(42L) } returns flowOf(messages)
        
        val viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(42L, state.chatId)
            assertEquals(1, state.messages.size)
            assertEquals("Hello", state.messages[0].content.text)
        }
    }

    @Test
    fun `uiState emits -1L when initialized without chatId`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(-1L, state.chatId)
            assertEquals(0, state.messages.size)
        }
    }
    
    @Test
    fun `uiState emits -1L when initialized with invalid chatId`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "invalid"))
        val viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(-1L, state.chatId)
            assertEquals(0, state.messages.size)
        }
    }

    @Test
    fun `uiState emits empty messages when chat ID not found in database`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "999"))
        
        coEvery { chatUseCases.getChat(999L) } returns flowOf(emptyList())
        
        val viewModel = createViewModel(savedStateHandle)

        viewModel.uiState.test {
            val state = awaitItem()
            assertEquals(999L, state.chatId)
            assertEquals(0, state.messages.size)
        }
    }
}
