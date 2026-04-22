package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelNavigationTest {

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

        val listenToSpeechUseCase = mockk<ListenToSpeechUseCase>(relaxed = true)
        every { listenToSpeechUseCase.invoke(any()) } returns MutableSharedFlow()
        every { chatUseCases.listenToSpeechUseCase } returns listenToSpeechUseCase

        coEvery { chatUseCases.getChat(any()) } returns MutableStateFlow(emptyList())
        every { settingsUseCases.getSettings() } returns MutableStateFlow(SettingsData())
        every { inferenceLockManager.isInferenceBlocked } returns MutableStateFlow(false)
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        coEvery { modelDisplayNamesUseCase.invoke(any()) } returns "Test Model"
        every { chatUseCases.mergeMessagesUseCase(any(), any()) } answers { firstArg() }
    }

    private fun createViewModel(savedStateHandle: SavedStateHandle): ChatViewModel {
        return ChatViewModel(
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
        )
    }

    @Test
    fun `initialChatId parses valid string from SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "42"))
        val viewModel = createViewModel(savedStateHandle)

        assertEquals(ChatId("42"), viewModel.initialChatId)
    }

    @Test
    fun `initialChatId returns non-null for any string in SavedStateHandle`() {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "some_id"))
        val viewModel = createViewModel(savedStateHandle)

        assertEquals(ChatId("some_id"), viewModel.initialChatId)
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
            Message(id = MessageId("1"), chatId = ChatId("42"), content = Content("Hello"), role = Role.USER)
        )
        coEvery { chatUseCases.getChat(ChatId("42")) } returns flowOf(messages)
        
        val viewModel = createViewModel(savedStateHandle)
        
        // Start collecting uiState to trigger the lazy StateFlow
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
        
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ChatId("42"), state.chatId)
        assertEquals(1, state.messages.size)
        assertEquals("Hello", state.messages[0].content.text)
        
        collectJob.cancel()
    }

    @Test
    fun `uiState emits null when initialized without chatId`() = runTest {
        val savedStateHandle = SavedStateHandle()
        val viewModel = createViewModel(savedStateHandle)
        
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
        
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        val state = viewModel.uiState.value
        assertNull(state.chatId)
        assertEquals(0, state.messages.size)
        
        collectJob.cancel()
    }
    
    @Test
    fun `uiState emits chatId when initialized with invalid chatId string`() = runTest {
        // Since chatId is now a String (UUID), any string is technically accepted by the wrapper
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "invalid"))
        val viewModel = createViewModel(savedStateHandle)
        
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
        
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ChatId("invalid"), state.chatId)
        assertEquals(0, state.messages.size)
        
        collectJob.cancel()
    }

    @Test
    fun `uiState emits empty messages when chat ID not found in database`() = runTest {
        val savedStateHandle = SavedStateHandle(mapOf("chatId" to "999"))
        
        coEvery { chatUseCases.getChat(ChatId("999")) } returns flowOf(emptyList())
        
        val viewModel = createViewModel(savedStateHandle)
        
        val collectJob = backgroundScope.launch { viewModel.uiState.collect { } }
        
        runCurrent()
        advanceTimeBy(100)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(ChatId("999"), state.chatId)
        assertEquals(0, state.messages.size)
        
        collectJob.cancel()
    }
}
