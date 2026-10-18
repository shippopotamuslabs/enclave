package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeStudioRepository
import com.browntowndev.enclave.core.testing.FakeTtsPlaybackController
import com.browntowndev.enclave.core.testing.FakeViewModelErrorHandler
import com.browntowndev.enclave.core.testing.MainDispatcherRule
import com.browntowndev.enclave.feature.inference.ActiveChatTurnStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MainDispatcherRule::class)
class ChatViewModelFlowTest {
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var settingsUseCases: FakeChatSettingsUseCases
    private lateinit var chatUseCases: FakeChatUseCases
    private lateinit var inferenceLockManager: FakeInferenceLockManager
    private lateinit var modelDisplayNamesUseCase: FakeGetModelDisplayNameUseCase
    private lateinit var stageImageAttachmentUseCase: FakeStageImageAttachmentUseCase
    private lateinit var activeModelProvider: FakeActiveModelProvider
    private lateinit var errorHandler: FakeViewModelErrorHandler
    private lateinit var cancelInferenceUseCase: FakeCancelInferenceUseCase
    private lateinit var toolExecutionEventPort: FakeToolExecutionEventPort
    private lateinit var loggingPort: FakeLoggingPort
    private lateinit var shareChatUseCase: FakeShareChatUseCase

    @BeforeEach
    fun setup() {
        settingsUseCases = FakeChatSettingsUseCases()
        chatUseCases = FakeChatUseCases()
        inferenceLockManager = FakeInferenceLockManager()
        modelDisplayNamesUseCase = FakeGetModelDisplayNameUseCase()
        stageImageAttachmentUseCase = FakeStageImageAttachmentUseCase()
        activeModelProvider = FakeActiveModelProvider()
        errorHandler = FakeViewModelErrorHandler()
        cancelInferenceUseCase = FakeCancelInferenceUseCase()
        toolExecutionEventPort = FakeToolExecutionEventPort()
        loggingPort = FakeLoggingPort()
        shareChatUseCase = FakeShareChatUseCase()

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
                playbackController = FakeTtsPlaybackController(),
                studioRepository = FakeStudioRepository(),
                shareChatUseCase = shareChatUseCase,
                chatRepository = FakeChatRepository(),
                exportArtifactDocumentsUseCase = FakeExportArtifactDocumentsUseCase(),
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
