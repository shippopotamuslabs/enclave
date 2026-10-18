package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.domain.model.chat.ChatId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ChatViewModelNavigationTest {
    private lateinit var settingsUseCases: FakeChatSettingsUseCases
    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var stageImageAttachmentUseCase: FakeStageImageAttachmentUseCase
    private lateinit var cancelInferenceUseCase: FakeCancelInferenceUseCase
    private lateinit var inferenceLockManager: FakeInferenceLockManager
    private lateinit var modelDisplayNamesUseCase: FakeGetModelDisplayNameUseCase
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var toolExecutionEventPort: FakeToolExecutionEventPort
    private lateinit var errorHandler: FakeViewModelErrorHandler
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var activeChatTurnSnapshotPort: FakeActiveChatTurnSnapshotPort
    private lateinit var playbackController: FakeTtsPlaybackController
    private lateinit var studioRepository: FakeStudioRepository
    private lateinit var shareChatUseCase: FakeShareChatUseCase

    @BeforeEach
    fun setup() {
        settingsUseCases = FakeChatSettingsUseCases()
        chatUseCases = FakeChatUseCases()
        stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase()
        cancelInferenceUseCase = FakeCancelInferenceUseCase()
        inferenceLockManager = FakeInferenceLockManager()
        modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
        activeModelProvider = FakeActiveModelProvider()
        toolExecutionEventPort = FakeToolExecutionEventPort()
        errorHandler = FakeViewModelErrorHandler()
        loggingPort = FakeLoggingPort()
        activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort()
        playbackController = FakeTtsPlaybackController()
        studioRepository = FakeStudioRepository()
        shareChatUseCase = FakeShareChatUseCase()
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
            chatRepository = FakeChatRepository(),
            exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
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
