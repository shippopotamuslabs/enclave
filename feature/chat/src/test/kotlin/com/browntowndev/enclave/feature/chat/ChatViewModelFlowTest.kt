package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

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
    private lateinit var shareChatUseCase: ShareChatUseCase

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
        shareChatUseCase = mockk(relaxed = true)

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

        chatViewModel =
            ChatViewModelTestSupport.createViewModel(
                settingsUseCases = settingsUseCases,
                chatUseCases = chatUseCases,
                savedStateHandle = savedStateHandle,
                cancelInferenceUseCase = cancelInferenceUseCase,
                inferenceLockManager = inferenceLockManager,
                modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                activeModelProvider = activeModelProvider,
                toolExecutionEventPort = toolExecutionEventPort,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                activeChatTurnSnapshotPort = ActiveChatTurnStore(),
                playbackController = mockk(relaxed = true),
                studioRepository = mockk(relaxed = true),
                shareChatUseCase = shareChatUseCase,
                chatRepository = mockk(relaxed = true),
                exportArtifactDocumentsUseCase = mockk(relaxed = true),
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            )
    }

    @Test
    fun `isGenerating is false when no messages are in a generating state`() =
        runTest {
            // Initial state is no messages, so isGenerating should be false
            assertFalse(chatViewModel.uiState.value.isGenerating)
        }
}
