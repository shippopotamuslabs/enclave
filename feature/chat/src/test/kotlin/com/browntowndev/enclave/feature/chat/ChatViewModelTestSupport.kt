package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUiMapper
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUiMapper
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.domain.telemetry.FakeInferenceTelemetryRepositoryPort
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveChatTelemetryUseCase
import com.browntowndev.enclave.domain.usecase.telemetry.ObserveMessageTelemetryUseCase
import com.browntowndev.enclave.domain.util.SystemClock
import com.browntowndev.enclave.feature.chat.delegates.ArtifactExportCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatShareCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatTelemetryCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.chat.delegates.LiveVoiceCoordinator
import com.browntowndev.enclave.feature.chat.delegates.SpeechInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ToolExecutionBannerCoordinator
import com.browntowndev.enclave.feature.chat.delegates.TtsController
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicyResolver
import com.browntowndev.enclave.feature.chat.stats.ChatTelemetryUiMapper
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeToolExecutor
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ChatViewModelTestSupport {
    data class Harness(
        val viewModel: ChatViewModel,
        val inferenceOrchestrator: InferenceOrchestrator,
        val chatInputCoordinator: ChatInputCoordinator,
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
        loggingPort: LoggingPort = FakeLoggingPort(),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        importSharedChatUseCase: ImportSharedChatUseCase = FakeImportSharedChatUseCase(),
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        shouldModeratePromptUseCase: ShouldModeratePromptUseCase = FakeShouldModeratePromptUseCase(),
        submitModerationReportUseCase: SubmitModerationReportUseCase = fakeSubmitModerationReportUseCase(),
        uiSelectionRepository: UiSelectionRepository = FakeUiSelectionRepository(),
        inferenceTelemetryRepositoryPort: FakeInferenceTelemetryRepositoryPort = FakeInferenceTelemetryRepositoryPort(),
    ): ChatViewModel =
        createHarness(
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
            importSharedChatUseCase = importSharedChatUseCase,
            chatRepository = chatRepository,
            exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
            stageImageAttachmentUseCase = stageImageAttachmentUseCase,
            shouldModeratePromptUseCase = shouldModeratePromptUseCase,
            submitModerationReportUseCase = submitModerationReportUseCase,
            uiSelectionRepository = uiSelectionRepository,
            inferenceTelemetryRepositoryPort = inferenceTelemetryRepositoryPort,
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
        loggingPort: LoggingPort = FakeLoggingPort(),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        importSharedChatUseCase: ImportSharedChatUseCase = FakeImportSharedChatUseCase(),
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        shouldModeratePromptUseCase: ShouldModeratePromptUseCase = FakeShouldModeratePromptUseCase(),
        submitModerationReportUseCase: SubmitModerationReportUseCase = fakeSubmitModerationReportUseCase(),
        uiSelectionRepository: UiSelectionRepository = FakeUiSelectionRepository(),
        inferenceTelemetryRepositoryPort: FakeInferenceTelemetryRepositoryPort = FakeInferenceTelemetryRepositoryPort(),
    ): Harness {
        val chatMessageUiMapper = ChatMessageUiMapper()
        val inferenceOrchestrator =
            RecordingInferenceOrchestrator(
                    chatUseCases = chatUseCases,
                    settingsUseCases = settingsUseCases,
                    cancelInferenceUseCase = cancelInferenceUseCase,
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
                    chatRepository = chatRepository,
                    errorHandler = errorHandler,
                    savedStateHandle = savedStateHandle,
                    pipelineExecutor = FakePipelineExecutorPort(),
                    moderatePromptUseCase = ModeratePromptUseCase(AllowPromptModerationPort, NoOpLoggingPort),
                    loggingPort = loggingPort,
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
                clock = SystemClock(),
            )
        val artifactExportCoordinator =
            ArtifactExportCoordinator(
                chatRepository = chatRepository,
                exportArtifactDocumentsUseCase = exportArtifactDocumentsUseCase,
                loggingPort = loggingPort,
            )
        val chatShareCoordinator =
            ChatShareCoordinator(shareChatUseCase = shareChatUseCase, importSharedChatUseCase = importSharedChatUseCase)
        val chatTelemetryCoordinator =
            ChatTelemetryCoordinator(
                observeMessageTelemetryUseCase =
                    ObserveMessageTelemetryUseCase(inferenceTelemetryRepositoryPort),
                observeChatTelemetryUseCase =
                    ObserveChatTelemetryUseCase(inferenceTelemetryRepositoryPort),
                mapper = ChatTelemetryUiMapper(),
            )
        val chatUiStateReducer = ChatUiStateReducer(chatMessageUiMapper = chatMessageUiMapper)
        val photoAttachmentPolicyResolver =
            PhotoAttachmentPolicyResolver(activeModelProvider = activeModelProvider)
        val liveVoiceCoordinator =
            LiveVoiceCoordinator(
                liveVoiceSessionUseCase = FakeLiveVoiceSessionUseCase(),
                persistVoiceTurnUseCase = FakePersistVoiceTurnUseCase(),
                realtimeAudioCapture = FakeRealtimeAudioCapturePort(),
                streamingAudioPlayer = FakeStreamingAudioPlayerPort(),
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                toolExecutorPort = FakeToolExecutor(),
                voiceChatAudioRoute = FakeVoiceChatAudioRoutePort(),
            )

        return Harness(
            inferenceOrchestrator = inferenceOrchestrator,
            chatInputCoordinator = chatInputCoordinator,
            viewModel =
                ChatViewModel(
                    settingsUseCases = settingsUseCases,
                    chatUseCases = chatUseCases,
                    savedStateHandle = savedStateHandle,
                    inferenceLockManager = inferenceLockManager,
                    modelDisplayNamesUseCase = modelDisplayNamesUseCase,
                    clearActiveChatTurnSnapshot = com.browntowndev.enclave.domain.usecase.chat.ClearActiveChatTurnSnapshotUseCase(activeChatTurnSnapshotPort),
                    observeActiveChatTurnSnapshot = com.browntowndev.enclave.domain.usecase.chat.ObserveActiveChatTurnSnapshotUseCase(activeChatTurnSnapshotPort),
                    markWebSearchSourcesExtracted = com.browntowndev.enclave.domain.usecase.chat.MarkWebSearchSourcesExtractedUseCase(activeChatTurnSnapshotPort),
                    errorHandler = errorHandler,
                    loggingPort = loggingPort,
                    observeChatStudioMedia = com.browntowndev.enclave.domain.usecase.studio.ObserveChatStudioMediaUseCase(studioRepository),
                    shouldModeratePromptUseCase = shouldModeratePromptUseCase,
                    submitModerationReportUseCase = submitModerationReportUseCase,
                    inferenceOrchestrator = inferenceOrchestrator,
                    chatInputCoordinator = chatInputCoordinator,
                    speechInputCoordinator = speechInputCoordinator,
                    ttsController = ttsController,
                    toolExecutionBannerCoordinator = toolExecutionBannerCoordinator,
                    artifactExportCoordinator = artifactExportCoordinator,
                    chatShareCoordinator = chatShareCoordinator,
                    chatTelemetryCoordinator = chatTelemetryCoordinator,
                    chatUiStateReducer = chatUiStateReducer,
                    chatMessageUiMapper = chatMessageUiMapper,
                    photoAttachmentPolicyResolver = photoAttachmentPolicyResolver,
                    liveVoiceCoordinator = liveVoiceCoordinator,
                    getSelectedChatMode = com.browntowndev.enclave.domain.usecase.chat.GetSelectedChatModeUseCase(uiSelectionRepository),
                    setSelectedChatMode = com.browntowndev.enclave.domain.usecase.chat.SetSelectedChatModeUseCase(uiSelectionRepository),
                    localModelAssetUiMapper = LocalModelAssetUiMapper(),
                    apiModelAssetUiMapper = ApiModelAssetUiMapper(),
                ),
        )
    }

    private object AllowPromptModerationPort : PromptModerationPort {
        override suspend fun classify(prompt: String): PromptModerationScores = PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
    }

    private object NoOpLoggingPort : LoggingPort {
        override fun debug(tag: String, message: String) = Unit
        override fun info(tag: String, message: String) = Unit
        override fun warning(tag: String, message: String) = Unit
        override fun error(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
