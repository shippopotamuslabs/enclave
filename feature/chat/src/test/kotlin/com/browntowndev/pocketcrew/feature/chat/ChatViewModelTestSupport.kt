package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase
import com.browntowndev.pocketcrew.feature.chat.delegates.ArtifactExportCoordinator
import com.browntowndev.pocketcrew.feature.chat.delegates.ChatInputCoordinator
import com.browntowndev.pocketcrew.feature.chat.delegates.ChatShareCoordinator
import com.browntowndev.pocketcrew.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.pocketcrew.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.pocketcrew.feature.chat.delegates.SpeechInputCoordinator
import com.browntowndev.pocketcrew.feature.chat.delegates.ToolExecutionBannerCoordinator
import com.browntowndev.pocketcrew.feature.chat.delegates.TtsController
import com.browntowndev.pocketcrew.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.pocketcrew.feature.chat.mapping.PhotoAttachmentPolicyResolver
import io.mockk.mockk

object ChatViewModelTestSupport {
    fun createViewModel(
        settingsUseCases: SettingsUseCases,
        chatUseCases: ChatUseCases,
        savedStateHandle: SavedStateHandle,
        cancelInferenceUseCase: CancelInferenceUseCase,
        inferenceLockManager: InferenceLockManager,
        modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
        activeModelProvider: com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort,
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
    ): ChatViewModel {
        val chatMessageUiMapper = ChatMessageUiMapper()
        val inferenceOrchestrator =
            InferenceOrchestrator(
                chatUseCases = chatUseCases,
                settingsUseCases = settingsUseCases,
                cancelInferenceUseCase = cancelInferenceUseCase,
                activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                errorHandler = errorHandler,
                savedStateHandle = savedStateHandle,
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

        return ChatViewModel(
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
        )
    }
}
