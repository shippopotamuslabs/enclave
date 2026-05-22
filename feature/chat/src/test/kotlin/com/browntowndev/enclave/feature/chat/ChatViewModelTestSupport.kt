package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.feature.chat.delegates.ArtifactExportCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatShareCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.chat.delegates.SpeechInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ToolExecutionBannerCoordinator
import com.browntowndev.enclave.feature.chat.delegates.TtsController
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicyResolver
import io.mockk.mockk
import io.mockk.spyk

object ChatViewModelTestSupport {
    data class Harness(
        val viewModel: ChatViewModel,
        val inferenceOrchestrator: InferenceOrchestrator,
    )

    fun createViewModel(
        settingsUseCases: SettingsUseCases,
        chatUseCases: ChatUseCases,
        savedStateHandle: SavedStateHandle,
        cancelInferenceUseCase: CancelInferenceUseCase,
        inferenceLockManager: InferenceLockManager,
        modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
        activeModelProvider: com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort,
        toolExecutionEventPort: ToolExecutionEventPort,
        errorHandler: ViewModelErrorHandler,
        loggingPort: LoggingPort = mockk(relaxed = true),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
    ): ChatViewModel = createHarness(
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
        chatRepository = chatRepository,
        exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase = stageImageAttachmentUseCase,
    ).viewModel

    fun createHarness(
        settingsUseCases: SettingsUseCases,
        chatUseCases: ChatUseCases,
        savedStateHandle: SavedStateHandle,
        cancelInferenceUseCase: CancelInferenceUseCase,
        inferenceLockManager: InferenceLockManager,
        modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
        activeModelProvider: com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort,
        toolExecutionEventPort: ToolExecutionEventPort,
        errorHandler: ViewModelErrorHandler,
        loggingPort: LoggingPort = mockk(relaxed = true),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
    ): Harness {
        val chatMessageUiMapper = ChatMessageUiMapper()
        val inferenceOrchestrator =
            spyk(
                InferenceOrchestrator(
                    chatUseCases = chatUseCases,
                    settingsUseCases = settingsUseCases,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                    chatRepository = chatRepository,
                    errorHandler = errorHandler,
                    savedStateHandle = savedStateHandle,
                ),
            )
        val chatInputCoordinator =
            ChatInputCoordinator(
                stageImageAttachmentUseCase = stageImageAttachmentUseCase,
                chatUseCases = chatUseCases,
                errorHandler = errorHandler,
            )
        val speechInputCoordinator =
            SpeechInputCoordinator(
                chatUseCases = chatUseCases,
                errorHandler = errorHandler,
                loggingPort = loggingPort,
            )
        val ttsController =
            TtsController(
                playbackController = playbackController,
                loggingPort = loggingPort,
                errorHandler = errorHandler,
            )
        val toolExecutionBannerCoordinator =
            ToolExecutionBannerCoordinator(
                toolExecutionEventPort = toolExecutionEventPort,
                loggingPort = loggingPort,
                activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            )
        val artifactExportCoordinator =
            ArtifactExportCoordinator(
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                loggingPort = loggingPort,
            )
        val chatShareCoordinator = ChatShareCoordinator(shareChatUseCase = shareChatUseCase)
        val chatUiStateReducer = ChatUiStateReducer(chatMessageUiMapper = chatMessageUiMapper)
        val photoAttachmentPolicyResolver =
            PhotoAttachmentPolicyResolver(activeModelProvider = activeModelProvider)

        return Harness(
            inferenceOrchestrator = inferenceOrchestrator,
            viewModel =
                ChatViewModel(
            settingsUseCases = settingsUseCases,
            chatUseCases = chatUseCases,
            savedStateHandle = savedStateHandle,
            inferenceLockManager = inferenceLockManager,
            modelDisplayNamesUseCase = modelDisplayNamesUseCase,
            activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
            errorHandler = errorHandler,
            loggingPort = loggingPort,
            studioRepository = studioRepository,
            inferenceOrchestrator = inferenceOrchestrator,
            chatInputCoordinator = chatInputCoordinator,
            speechInputCoordinator = speechInputCoordinator,
            ttsController = ttsController,
            toolExecutionBannerCoordinator = toolExecutionBannerCoordinator,
            artifactExportCoordinator = artifactExportCoordinator,
            chatShareCoordinator = chatShareCoordinator,
            chatUiStateReducer = chatUiStateReducer,
            chatMessageUiMapper = chatMessageUiMapper,
            photoAttachmentPolicyResolver = photoAttachmentPolicyResolver,
                ),
        )
    }
}
