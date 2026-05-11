package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.testing.MainDispatcherRule
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
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
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

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
            playbackController = mockk(relaxed = true),
            studioRepository = mockk(relaxed = true),
            shareChatUseCase = shareChatUseCase,
            chatRepository = mockk(relaxed = true),
            exportArtifactDocumentsUseCase = mockk(relaxed = true),
        )
    }

    @Test
    fun `isGenerating is false when no messages are in a generating state`() = runTest {
        // Initial state is no messages, so isGenerating should be false
        assertFalse(chatViewModel.uiState.value.isGenerating)
    }
}
