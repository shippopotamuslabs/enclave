package com.browntowndev.enclave.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUiMapper
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUiMapper
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.usecase.chat.ChatCapablePresets
import com.browntowndev.enclave.domain.usecase.chat.GetChatCapablePresetsUseCase
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.inference.ModelType
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
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.media.VoiceChatAudioRoutePort
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
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.flowOf

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
        loggingPort: LoggingPort = mockk(relaxed = true),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        importSharedChatUseCase: ImportSharedChatUseCase = mockk(relaxed = true),
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        shouldModeratePromptUseCase: ShouldModeratePromptUseCase =
            mockk(relaxed = true) {
                coEvery { this@mockk.invoke(any<ModelType>()) } returns true
            },
        submitModerationReportUseCase: SubmitModerationReportUseCase = mockk(relaxed = true),
        uiSelectionRepository: UiSelectionRepository =
            mockk(relaxed = true) {
                every { selectedChatMode } returns flowOf(Mode.FAST)
            },
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
        loggingPort: LoggingPort = mockk(relaxed = true),
        activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        playbackController: TtsPlaybackControllerPort,
        studioRepository: StudioRepositoryPort,
        shareChatUseCase: ShareChatUseCase,
        importSharedChatUseCase: ImportSharedChatUseCase = mockk(relaxed = true),
        chatRepository: ChatRepository,
        exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
        stageImageAttachmentUseCase: StageImageAttachmentUseCase,
        shouldModeratePromptUseCase: ShouldModeratePromptUseCase =
            mockk(relaxed = true) {
                coEvery { this@mockk.invoke(any<ModelType>()) } returns true
            },
        submitModerationReportUseCase: SubmitModerationReportUseCase = mockk(relaxed = true),
        uiSelectionRepository: UiSelectionRepository =
            mockk(relaxed = true) {
                every { selectedChatMode } returns flowOf(Mode.FAST)
            },
        inferenceTelemetryRepositoryPort: FakeInferenceTelemetryRepositoryPort = FakeInferenceTelemetryRepositoryPort(),
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
                    pipelineExecutor = mockk(relaxed = true),
                    moderatePromptUseCase = ModeratePromptUseCase(AllowPromptModerationPort, NoOpLoggingPort),
                    loggingPort = loggingPort,
                    applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
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
        val getChatCapablePresetsUseCase = mockk<GetChatCapablePresetsUseCase>()
        every { getChatCapablePresetsUseCase.invoke() } returns
            flowOf(ChatCapablePresets(localAssets = emptyList(), apiAssets = emptyList()))
        every { chatUseCases.getChatCapablePresetsUseCase } returns getChatCapablePresetsUseCase

        val liveVoiceCoordinator =
            LiveVoiceCoordinator(
                liveVoiceSessionUseCase = mockk(relaxed = true),
                persistVoiceTurnUseCase = mockk(relaxed = true),
                realtimeAudioCapture = mockk<RealtimeAudioCapturePort>(relaxed = true),
                streamingAudioPlayer = mockk<StreamingAudioPlayerPort>(relaxed = true),
                errorHandler = errorHandler,
                loggingPort = loggingPort,
                toolExecutorPort = mockk(relaxed = true),
                voiceChatAudioRoute = mockk(relaxed = true),
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
                    activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
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
                    uiSelectionRepository = uiSelectionRepository,
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
