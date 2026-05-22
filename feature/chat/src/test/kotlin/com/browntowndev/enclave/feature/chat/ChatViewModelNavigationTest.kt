package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ChatViewModelNavigationTest {
    private lateinit var settingsUseCases: SettingsUseCases
    private lateinit var chatUseCases: ChatUseCases
    private lateinit var stageImageAttachmentUseCase: StageImageAttachmentUseCase
    private lateinit var cancelInferenceUseCase: CancelInferenceUseCase
    private lateinit var inferenceLockManager: InferenceLockManager
    private lateinit var modelDisplayNamesUseCase: GetModelDisplayNameUseCase
    private lateinit var activeModelProvider: ActiveModelProviderPort
    private lateinit var toolExecutionEventPort: ToolExecutionEventPort
    private lateinit var errorHandler: ViewModelErrorHandler
    private lateinit var loggingPort: LoggingPort
    private lateinit var activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort
    private lateinit var playbackController: TtsPlaybackControllerPort
    private lateinit var studioRepository: StudioRepositoryPort
    private lateinit var shareChatUseCase: ShareChatUseCase

    @BeforeEach
    fun setup() {
        settingsUseCases = mockk(relaxed = true)
        chatUseCases = mockk(relaxed = true)
        stageImageAttachmentUseCase = mockk(relaxed = true)
        cancelInferenceUseCase = mockk(relaxed = true)
        inferenceLockManager = mockk(relaxed = true)
        modelDisplayNamesUseCase = mockk(relaxed = true)
        activeModelProvider = mockk(relaxed = true)
        toolExecutionEventPort = mockk(relaxed = true)
        errorHandler = mockk(relaxed = true)
        loggingPort = mockk(relaxed = true)
        activeChatTurnSnapshotPort = mockk(relaxed = true)
        playbackController = mockk(relaxed = true)
        studioRepository = mockk(relaxed = true)
        shareChatUseCase = mockk(relaxed = true)

        every { settingsUseCases.getSettings() } returns flowOf(SettingsData())
        every { settingsUseCases.assignments.getDefaultModels() } returns flowOf(emptyList())
        every { toolExecutionEventPort.events } returns MutableSharedFlow()
        every { activeChatTurnSnapshotPort.observe(any()) } returns flowOf(null)
        every { studioRepository.observeEphemeralMedia() } returns flowOf(emptyList())
        every { studioRepository.observeAllMedia() } returns flowOf(emptyList())
    }

    private fun createViewModel(chatId: String?): ChatViewModel {
        val savedStateHandle =
            SavedStateHandle().apply {
                if (chatId != null) set("chatId", chatId)
            }
        return ChatViewModelTestSupport.createViewModel(
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
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            playbackController = playbackController,
            studioRepository = studioRepository,
            shareChatUseCase = shareChatUseCase,
            chatRepository = mockk(relaxed = true),
            exportArtifactDocumentsUseCase = mockk(relaxed = true),
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
        )
    }

    @Test
    fun `initialChatId is correctly retrieved from SavedStateHandle`() {
        val viewModel = createViewModel("test-chat-id")
        assertEquals(ChatId("test-chat-id"), viewModel.initialChatId)
    }

    @Test
    fun `initialChatId is null when not in SavedStateHandle`() {
        val viewModel = createViewModel(null)
        assertEquals(null, viewModel.initialChatId)
    }
}
