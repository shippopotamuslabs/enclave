package com.browntowndev.enclave.feature.chat

import com.browntowndev.enclave.core.testing.FakeActiveModelProvider
import com.browntowndev.enclave.core.testing.FakeApiKeyProvider
import com.browntowndev.enclave.core.testing.FakeApiModelRepository
import com.browntowndev.enclave.core.testing.FakeAudioCapture
import com.browntowndev.enclave.core.testing.FakeChatRepository
import com.browntowndev.enclave.core.testing.FakeCloudSyncPort
import com.browntowndev.enclave.core.testing.FakeDefaultModelRepository
import com.browntowndev.enclave.core.testing.FakeDistributionConfiguration
import com.browntowndev.enclave.core.testing.FakeEmbeddingEngine
import com.browntowndev.enclave.core.testing.FakeExtractedUrlTracker
import com.browntowndev.enclave.core.testing.FakeLiveVoiceProviderRepository
import com.browntowndev.enclave.core.testing.FakeLocalModelRepository
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeMemoriesRepository
import com.browntowndev.enclave.core.testing.FakeMessageRepository
import com.browntowndev.enclave.core.testing.FakeChatInferenceExecutorPort
import com.browntowndev.enclave.core.testing.FakeModerationReportRepository
import com.browntowndev.enclave.core.testing.FakeOnDeviceInferenceCancellationPort
import com.browntowndev.enclave.core.testing.FakeShareMediaPort
import com.browntowndev.enclave.core.testing.FakeUiSelectionRepository
import com.browntowndev.enclave.core.testing.FakeTransactionProvider
import com.browntowndev.enclave.core.testing.FakeTrustedDomainRepository
import com.browntowndev.enclave.core.testing.FakeTtsProviderRepository
import com.browntowndev.enclave.core.testing.FakeUtilityModelFile
import com.browntowndev.enclave.core.testing.FakeWhisperInference
import com.browntowndev.enclave.core.testing.TestClock
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolExecutionEvent
import com.browntowndev.enclave.domain.model.share.SharedChat
import com.browntowndev.enclave.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.enclave.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.enclave.domain.port.cloud.CloudDocumentExtractorPort
import com.browntowndev.enclave.domain.port.docgen.DocumentGeneratorPort
import com.browntowndev.enclave.domain.port.inference.ChatTitleGeneratorPort
import com.browntowndev.enclave.domain.port.inference.PipelineExecutorPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationPort
import com.browntowndev.enclave.domain.port.inference.PromptModerationScores
import com.browntowndev.enclave.domain.usecase.inference.PromptModerationResult
import com.browntowndev.enclave.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.enclave.domain.port.media.FileAttachmentMetadata
import com.browntowndev.enclave.domain.port.media.FileAttachmentPort
import com.browntowndev.enclave.domain.port.media.ImageAttachmentStoragePort
import com.browntowndev.enclave.domain.port.media.LiveVoiceEvent
import com.browntowndev.enclave.domain.port.media.LiveVoicePort
import com.browntowndev.enclave.domain.port.media.LiveVoiceSession
import com.browntowndev.enclave.domain.port.media.RealtimeAudioCapturePort
import com.browntowndev.enclave.domain.port.media.RealtimeCaptureEvent
import com.browntowndev.enclave.domain.port.media.SaveBinaryToDownloadsPort
import com.browntowndev.enclave.domain.port.media.StreamingAudioConfig
import com.browntowndev.enclave.domain.port.media.StreamingAudioPlayerPort
import com.browntowndev.enclave.domain.port.media.VoiceChatAudioRoutePort
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.ShareRepositoryPort
import com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.enclave.domain.usecase.byok.GetDefaultModelsUseCase
import com.browntowndev.enclave.domain.usecase.byok.SetDefaultModelUseCase
import com.browntowndev.enclave.domain.usecase.chat.AssistantPlaceholderResult
import com.browntowndev.enclave.domain.usecase.chat.ChatCapablePresets
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.CreateAssistantPlaceholderUseCase
import com.browntowndev.enclave.domain.usecase.chat.CreateUserMessageUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatResponseUseCase
import com.browntowndev.enclave.domain.usecase.chat.GenerateChatTitleUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatCapablePresetsUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetChatUseCase
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.ListenToSpeechUseCase
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionOpenResult
import com.browntowndev.enclave.domain.usecase.chat.LiveVoiceSessionUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeDbMessagesWithOptimisticUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeMessagesUseCase
import com.browntowndev.enclave.domain.usecase.chat.PersistVoiceTurnUseCase
import com.browntowndev.enclave.domain.usecase.chat.ProcessFileAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.RollbackPromptSubmissionUseCase
import com.browntowndev.enclave.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptRequest
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptUseCase
import com.browntowndev.enclave.domain.usecase.chat.UserPromptResult
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.InferenceType
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetLiveVoiceProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetSettingsUseCase
import com.browntowndev.enclave.domain.usecase.settings.GetTtsProvidersUseCase
import com.browntowndev.enclave.domain.usecase.settings.ResolvedAssignedModelSelection
import com.browntowndev.enclave.domain.usecase.settings.ResolveAssignedModelSelectionUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsApiProviderUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAssignmentUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsAuthUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsDataControlsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsDeletionUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLiveVoiceUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsLocalModelUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMediaUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsPreferencesUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsTtsUseCases
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.domain.usecase.share.ImportSharedChatUseCase
import com.browntowndev.enclave.domain.usecase.share.ShareChatUseCase
import com.browntowndev.enclave.domain.model.share.ShareOption
import com.browntowndev.enclave.core.testing.FakeActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.media.SpeechState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf

// ---------- Ports ----------

/** Fake [InferenceLockManager] (ENC-28). */
class FakeInferenceLockManager : InferenceLockManager {
    override val isInferenceBlocked: MutableStateFlow<Boolean> = MutableStateFlow(false)
    var acquireResult: Boolean = true
    val acquiredTypes = mutableListOf<InferenceType>()
    val releasedTypes = mutableListOf<InferenceType>()
    var forceReleaseCount = 0

    override fun acquireLock(inferenceType: InferenceType): Boolean {
        acquiredTypes += inferenceType
        return acquireResult
    }

    override fun releaseLock(inferenceType: InferenceType) {
        releasedTypes += inferenceType
    }

    override fun forceReleaseOnDeviceLock() {
        forceReleaseCount++
    }
}

/** Fake [ToolExecutionEventPort] (ENC-28). */
class FakeToolExecutionEventPort : ToolExecutionEventPort {
    override val events: MutableSharedFlow<ToolExecutionEvent> = MutableSharedFlow(extraBufferCapacity = 16)
}

/** Fake [PipelineExecutorPort] (ENC-28). */
class FakePipelineExecutorPort : PipelineExecutorPort {
    var pipelineFlow: Flow<MessageGenerationState> = emptyFlow()
    val executeCalls = mutableListOf<String>()
    val stopCalls = mutableListOf<String>()
    val resumeCalls = mutableListOf<String>()

    override fun executePipeline(
        chatId: String,
        userMessage: String,
        userMessageId: MessageId?,
        assistantMessageId: MessageId?,
        toolingEnabled: Boolean,
    ): Flow<MessageGenerationState> {
        executeCalls += chatId
        return pipelineFlow
    }

    override suspend fun stopPipeline(pipelineId: String) {
        stopCalls += pipelineId
    }

    override suspend fun resumeFromState(
        chatId: String,
        pipelineId: String,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit,
    ): Flow<MessageGenerationState> {
        resumeCalls += chatId
        return pipelineFlow
    }
}

/** Dummy [ChatTitleGeneratorPort] (ENC-28). */
object FakeChatTitleGeneratorPort : ChatTitleGeneratorPort {
    override suspend fun generateTitle(prompt: String): String? = null
}

/** Dummy [FileAttachmentPort] (ENC-28). */
object FakeFileAttachmentPort : FileAttachmentPort {
    override suspend fun readFileContent(uri: String): FileAttachmentMetadata =
        FileAttachmentMetadata(name = "file", mimeType = null, content = "")

    override suspend fun readBytes(uri: String): ByteArray = ByteArray(0)
}

/** Dummy [CloudDocumentExtractorPort] (ENC-28). */
object FakeCloudDocumentExtractorPort : CloudDocumentExtractorPort {
    override suspend fun extractText(uri: String, mimeType: String): FileAttachmentMetadata =
        FileAttachmentMetadata(name = "file", mimeType = mimeType, content = "")
}

/** Dummy [ImageAttachmentStoragePort] (ENC-28). */
object FakeImageAttachmentStoragePort : ImageAttachmentStoragePort {
    override suspend fun stageImage(sourceUri: String): String = sourceUri
}

/** Fake [LiveVoiceSession] (ENC-28). */
class FakeLiveVoiceSession : LiveVoiceSession {
    val eventsFlow = MutableSharedFlow<LiveVoiceEvent>(extraBufferCapacity = 8)
    var closeCount = 0

    override fun events(): Flow<LiveVoiceEvent> = eventsFlow

    override suspend fun sendAudio(pcm16: ByteArray) = Unit

    override suspend fun commitTurn() = Unit

    override suspend fun cancelAssistantResponse() = Unit

    override suspend fun close() {
        closeCount++
    }

    override suspend fun sendToolResult(callId: String, name: String, resultJson: String) = Unit
}

/** Fake [LiveVoicePort] (ENC-28). */
class FakeLiveVoicePort : LiveVoicePort {
    val session = FakeLiveVoiceSession()
    var openResult: Result<LiveVoiceSession> = Result.success(session)
    var openCount = 0

    override suspend fun openSession(
        asset: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset,
        apiKey: String,
        tools: List<com.browntowndev.enclave.domain.model.inference.ToolDefinition>,
    ): Result<LiveVoiceSession> {
        openCount++
        return openResult
    }
}

/** Fake [RealtimeAudioCapturePort] (ENC-28). */
class FakeRealtimeAudioCapturePort : RealtimeAudioCapturePort {
    var eventsFlow: Flow<RealtimeCaptureEvent> = emptyFlow()

    override fun captureEvents(sampleRate: Int): Flow<RealtimeCaptureEvent> = eventsFlow
}

/** Fake [StreamingAudioPlayerPort] (ENC-28). */
class FakeStreamingAudioPlayerPort : StreamingAudioPlayerPort {
    var initialized: Boolean = false
    val enqueuedChunks = mutableListOf<ByteArray>()
    var startCount = 0
    var stopCount = 0

    override suspend fun initialize(config: StreamingAudioConfig) {
        initialized = true
    }

    override suspend fun enqueueChunk(audioChunk: ByteArray) {
        enqueuedChunks += audioChunk
    }

    override fun startPlayback() {
        startCount++
    }

    override fun stop() {
        stopCount++
    }

    override fun isInitialized(): Boolean = initialized
}

/** Fake [VoiceChatAudioRoutePort] (ENC-28). */
class FakeVoiceChatAudioRoutePort : VoiceChatAudioRoutePort {
    var activateCount = 0
    var deactivateCount = 0

    override fun activateSpeakerphone() {
        activateCount++
    }

    override fun deactivateSpeakerphone() {
        deactivateCount++
    }
}

/** Dummy [ShareRepositoryPort] (ENC-28). */
class FakeShareRepositoryPort : ShareRepositoryPort {
    var sharedChat: SharedChat =
        SharedChat(id = "share-1", messages = emptyList(), createdAt = 0L, expiresAt = 0L, shareUrl = "https://share.example/1", sourceChatId = null)

    override suspend fun shareChat(messages: List<Message>, chatId: ChatId?): SharedChat = sharedChat

    override suspend fun getSharedChat(shareId: String, decryptionKey: String?): SharedChat? = null
}

// ---------- Chat use-case fakes ----------

/** Recording [SubmitPromptUseCase] fake (ENC-28). */
class FakeSubmitPromptUseCase :
    SubmitPromptUseCase(
        CreateUserMessageUseCase(FakeTransactionProvider(), FakeMessageRepository(), FakeChatRepository()),
        FakeCreateAssistantPlaceholderUseCase(),
        RollbackPromptSubmissionUseCase(FakeMessageRepository(), FakeChatRepository(), FakeTransactionProvider()),
        FakeCloudSyncPort(),
        FakeGenerateChatTitleUseCase(),
    ) {
    val calls = mutableListOf<SubmitPromptRequest>()

    /** When set, invoke returns this; when null, a default result built from the request ids is used. */
    var result: UserPromptResult? = null

    /** When set, invoke throws this after recording the call. */
    var failure: Throwable? = null

    /** When set, fully controls invoke (gates, dynamic results). Takes precedence over result/failure. */
    var behavior: (suspend (SubmitPromptRequest) -> UserPromptResult)? = null

    override suspend fun invoke(request: SubmitPromptRequest): UserPromptResult {
        calls += request
        behavior?.let { return it(request) }
        failure?.let { throw it }
        return result
            ?: UserPromptResult(
                userMessageId = MessageId("user-1"),
                chatId = ChatId("chat-1"),
                userMessageCreatedAtMillis = 100L,
            )
    }
}

/** Recording [CreateAssistantPlaceholderUseCase] fake (ENC-28). */
class FakeCreateAssistantPlaceholderUseCase :
    CreateAssistantPlaceholderUseCase(FakeMessageRepository(), FakeChatRepository()) {
    val calls = mutableListOf<MessageId>()

    override suspend fun invoke(
        chatId: ChatId,
        userMessageCreatedAtMillis: Long,
        assistantMessageId: MessageId,
        scheduleCloudSync: Boolean,
    ): AssistantPlaceholderResult {
        calls += assistantMessageId
        return AssistantPlaceholderResult(assistantMessageId = assistantMessageId)
    }
}

/** Recording [GenerateChatResponseUseCase] fake (ENC-28). */
class FakeGenerateChatResponseUseCase :
    GenerateChatResponseUseCase(
        pipelineExecutor = FakePipelineExecutorPort(),
        chatRepository = FakeChatRepository(),
        messageRepository = FakeMessageRepository(),
        loggingPort = FakeLoggingPort(),
        activeModelProvider = FakeActiveModelProvider(),
        extractedUrlTracker = FakeExtractedUrlTracker(),
        chatInferenceExecutor = FakeChatInferenceExecutorPort(),
        embeddingEngine = FakeEmbeddingEngine(),
        activeChatTurnSnapshotPort = FakeActiveChatTurnSnapshotPort(),
        inferenceLockManager = FakeInferenceLockManager(),
    ) {
    data class Args(
        val prompt: String,
        val userMessageId: MessageId,
        val assistantMessageId: MessageId,
        val chatId: ChatId,
        val responseMode: ChatResponseMode,
        val backgroundInferenceEnabled: Boolean,
    )

    val calls = mutableListOf<Args>()
    var flow: Flow<AccumulatedMessages> = emptyFlow()

    override fun invoke(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        responseMode: ChatResponseMode,
        backgroundInferenceEnabled: Boolean,
    ): Flow<AccumulatedMessages> {
        calls += Args(prompt, userMessageId, assistantMessageId, chatId, responseMode, backgroundInferenceEnabled)
        return flow
    }
}

/** Flow-backed [GetChatUseCase] fake (ENC-28). */
class FakeGetChatUseCase : GetChatUseCase(FakeChatRepository()) {
    var flow: Flow<List<Message>> = MutableStateFlow(emptyList())

    /** Per-chat flows; consulted before [flow] so navigation tests can serve distinct chats. */
    val flowsByChatId = mutableMapOf<ChatId, Flow<List<Message>>>()
    val calls = mutableListOf<ChatId>()

    override fun invoke(chatId: ChatId): Flow<List<Message>> {
        calls += chatId
        return flowsByChatId[chatId] ?: flow
    }
}

/** Flow-backed [GetChatCapablePresetsUseCase] fake (ENC-28). */
class FakeGetChatCapablePresetsUseCase :
    GetChatCapablePresetsUseCase(FakeLocalModelRepository(), FakeApiModelRepository()) {
    var flow: Flow<ChatCapablePresets> =
        flowOf(ChatCapablePresets(localAssets = emptyList(), apiAssets = emptyList()))
    var callCount = 0

    override fun invoke(): Flow<ChatCapablePresets> {
        callCount++
        return flow
    }
}

/** Recording [GenerateChatTitleUseCase] fake (ENC-28). */
class FakeGenerateChatTitleUseCase :
    GenerateChatTitleUseCase(FakeChatTitleGeneratorPort, FakeChatRepository(), FakeLoggingPort()) {
    val calls = mutableListOf<ChatId>()

    override suspend fun invoke(chatId: ChatId, prompt: String) {
        calls += chatId
    }
}

/** Recording [ProcessFileAttachmentUseCase] fake (ENC-28). */
class FakeProcessFileAttachmentUseCase :
    ProcessFileAttachmentUseCase(FakeFileAttachmentPort, FakeCloudDocumentExtractorPort) {
    val calls = mutableListOf<String>()
    var result: FileAttachmentMetadata = FileAttachmentMetadata(name = "file", mimeType = null, content = "")

    override suspend fun invoke(uri: String): FileAttachmentMetadata {
        calls += uri
        return result
    }
}

/** Recording [ListenToSpeechUseCase] fake (ENC-28), mirroring the studio fake. */
class FakeListenToSpeechUseCase :
    ListenToSpeechUseCase(FakeAudioCapture(), FakeWhisperInference(), FakeUtilityModelFile(), TestClock()) {
    val events = MutableSharedFlow<SpeechState>(extraBufferCapacity = 8)
    var callCount = 0
    val stopSignals = mutableListOf<StateFlow<Boolean>>()

    override fun invoke(initialText: String, stopSignal: StateFlow<Boolean>): Flow<SpeechState> {
        callCount++
        stopSignals += stopSignal
        return events
    }
}

/** Recording [StageImageAttachmentUseCase] fake (ENC-28). */
class FakeStageImageAttachmentUseCase : StageImageAttachmentUseCase(FakeImageAttachmentStoragePort) {
    val calls = mutableListOf<String>()
    var result: String? = null

    /** Per-source-URI staged results; consulted before [result]. */
    val resultsByUri = mutableMapOf<String, String>()

    override suspend fun invoke(sourceUri: String): String {
        calls += sourceUri
        return resultsByUri[sourceUri] ?: result ?: sourceUri
    }
}

/** Recording [GetModelDisplayNameUseCase] fake (ENC-28). */
class FakeGetModelDisplayNameUseCase : GetModelDisplayNameUseCase(FakeActiveModelProvider()) {
    var result: String = "Test Model"
    val calls = mutableListOf<ModelType>()

    override suspend fun invoke(modelType: ModelType): String {
        calls += modelType
        return result
    }
}

/** Recording [CancelInferenceUseCase] fake (ENC-28). */
class FakeCancelInferenceUseCase :
    CancelInferenceUseCase(
        FakeChatInferenceExecutorPort(),
        FakeOnDeviceInferenceCancellationPort(),
        FakeInferenceLockManager(),
    ) {
    var callCount = 0

    override fun invoke() {
        callCount++
    }
}

/** Recording [LiveVoiceSessionUseCase] fake (ENC-28). */
class FakeLiveVoiceSessionUseCase :
    LiveVoiceSessionUseCase(
        FakeResolveAssignedModelSelectionUseCase(),
        FakeLiveVoicePort(),
        FakeApiKeyProvider(),
    ) {
    var callCount = 0
    var result: Result<LiveVoiceSessionOpenResult> =
        Result.failure(IllegalStateException("FakeLiveVoiceSessionUseCase result not set"))

    override suspend fun invoke(): Result<LiveVoiceSessionOpenResult> {
        callCount++
        return result
    }
}

/** Recording [PersistVoiceTurnUseCase] fake (ENC-28). */
class FakePersistVoiceTurnUseCase :
    PersistVoiceTurnUseCase(
        CreateUserMessageUseCase(FakeTransactionProvider(), FakeMessageRepository(), FakeChatRepository()),
        FakeMessageRepository(),
        FakeChatRepository(),
        FakeGenerateChatTitleUseCase(),
    ) {
    data class Args(
        val chatId: ChatId?,
        val userText: String,
        val assistantText: String,
        val webSearchSources: List<com.browntowndev.enclave.domain.model.chat.WebSearchSource>,
        val assistantMessageId: MessageId?,
    )

    var callCount = 0
    val calls = mutableListOf<Args>()

    /** When set, invoke returns this instead of the passed chatId. */
    var resultChatId: ChatId? = null

    override suspend fun invoke(
        chatId: ChatId?,
        userText: String,
        assistantText: String,
        webSearchSources: List<com.browntowndev.enclave.domain.model.chat.WebSearchSource>,
        assistantMessageId: MessageId?,
    ): ChatId {
        callCount++
        calls += Args(chatId, userText, assistantText, webSearchSources, assistantMessageId)
        return resultChatId ?: chatId ?: ChatId("voice-chat")
    }
}

/** Recording [ShareChatUseCase] fake (ENC-28). */
class FakeShareChatUseCase :
    ShareChatUseCase(FakeShareRepositoryPort(), FakeChatUseCases(), FakeLoggingPort()) {
    val calls = mutableListOf<ShareOption>()
    var result: Result<SharedChat> =
        Result.success(SharedChat(id = "share-1", messages = emptyList(), createdAt = 0L, expiresAt = 0L, shareUrl = "https://share.example/1", sourceChatId = null))

    override suspend fun invoke(option: ShareOption): Result<SharedChat> {
        calls += option
        return result
    }

    val getSharedChatCalls = mutableListOf<Pair<String, String?>>()
    var getSharedChatResult: Result<SharedChat?> = Result.success(null)

    override suspend fun getSharedChat(
        shareReference: String,
        decryptionKey: String?,
    ): Result<SharedChat?> {
        getSharedChatCalls += shareReference to decryptionKey
        return getSharedChatResult
    }
}

/** Dummy [DocumentGeneratorPort] (ENC-28). */
object FakeDocumentGeneratorPort : DocumentGeneratorPort {
    override suspend fun generateWordDocument(json: String): Result<ByteArray> = Result.success(ByteArray(0))

    override suspend fun generatePdfDocument(json: String): Result<ByteArray> = Result.success(ByteArray(0))
}

/** Dummy [SaveBinaryToDownloadsPort] (ENC-28). */
class FakeSaveBinaryToDownloadsPort : SaveBinaryToDownloadsPort {
    val savedFiles = mutableListOf<String>()

    override suspend fun save(
        bytes: ByteArray,
        displayName: String,
        mimeType: String,
    ): Result<Unit> {
        savedFiles += displayName
        return Result.success(Unit)
    }
}

/** Recording [ExportArtifactDocumentsUseCase] fake (ENC-28). */
class FakeExportArtifactDocumentsUseCase :
    ExportArtifactDocumentsUseCase(
        FakeDocumentGeneratorPort,
        FakeShareMediaPort(),
        FakeSaveBinaryToDownloadsPort(),
    ) {
    val calls = mutableListOf<ExportArtifactDocumentsCommand>()
    var result: Result<ArtifactExportOutcome> = Result.success(ArtifactExportOutcome.Shared(fileCount = 0))

    override suspend fun invoke(command: ExportArtifactDocumentsCommand): Result<ArtifactExportOutcome> {
        calls += command
        return result
    }
}

/** Recording [ImportSharedChatUseCase] fake (ENC-28). */
class FakeImportSharedChatUseCase : ImportSharedChatUseCase(FakeChatRepository(), FakeMessageRepository()) {
    val calls = mutableListOf<SharedChat>()
    var result: ChatId = ChatId("imported-chat")

    override suspend fun invoke(sharedChat: SharedChat): ChatId {
        calls += sharedChat
        return result
    }
}

/** Recording [ModeratePromptUseCase] fake (ENC-28). */
class FakeModeratePromptUseCase :
    ModeratePromptUseCase(
        object : PromptModerationPort {
            override suspend fun classify(prompt: String): PromptModerationScores =
                PromptModerationScores(scoresByLabel = mapOf("OK" to 1.0))
        },
        FakeLoggingPort(),
    ) {
    val calls = mutableListOf<String>()
    var result: PromptModerationResult =
        PromptModerationResult(
            blocked = false,
            scoresByLabel = emptyMap(),
            canonicalScores = emptyMap(),
            triggeredLabels = emptyList(),
            combinedIllegalScore = 0.0,
        )

    override suspend fun invoke(text: String): PromptModerationResult {
        calls += text
        return result
    }
}

/** Recording [ShouldModeratePromptUseCase] fake (ENC-28). */
class FakeShouldModeratePromptUseCase :
    ShouldModeratePromptUseCase(
        FakeDistributionConfiguration(),
        FakeActiveModelProvider(),
        FakeTrustedDomainRepository(),
        FakeLoggingPort(),
    ) {
    var result: Boolean = true
    val calls = mutableListOf<ModelType>()

    override suspend fun invoke(modelType: ModelType): Boolean {
        calls += modelType
        return result
    }
}

/** Real [SubmitModerationReportUseCase] over the fake repository (ENC-28). */
fun fakeSubmitModerationReportUseCase(): SubmitModerationReportUseCase =
    SubmitModerationReportUseCase(FakeModerationReportRepository())

/** Real [com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase] over dummy ports (ENC-28). */
fun fakeExportArtifactDocumentsUseCase(): com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase =
    com.browntowndev.enclave.domain.usecase.artifact.ExportArtifactDocumentsUseCase(
        documentGeneratorPort = FakeDocumentGeneratorPort,
        shareMediaPort = com.browntowndev.enclave.core.testing.FakeShareMediaPort(),
        saveBinaryToDownloadsPort =
            com.browntowndev.enclave.domain.port.media.SaveBinaryToDownloadsPort { _, _, _ -> Result.success(Unit) },
    )

// ---------- Settings-side fakes used by chat tests ----------

/** Flow-backed [GetSettingsUseCase] fake for chat tests (ENC-28). */
class FakeChatGetSettingsUseCase :
    GetSettingsUseCase(com.browntowndev.enclave.domain.usecase.FakeSettingsRepository()) {
    var flow: Flow<SettingsData> = MutableStateFlow(SettingsData())

    override fun invoke(): Flow<SettingsData> = flow
}

/** Flow-backed [GetDefaultModelsUseCase] fake for chat tests (ENC-28). */
class FakeChatGetDefaultModelsUseCase : GetDefaultModelsUseCase {
    var flow: Flow<List<com.browntowndev.enclave.domain.model.config.DefaultModelAssignment>> =
        MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<com.browntowndev.enclave.domain.model.config.DefaultModelAssignment>> = flow
}

/** Recording [SetDefaultModelUseCase] fake for chat tests (ENC-28). */
class FakeChatSetDefaultModelUseCase : SetDefaultModelUseCase {
    data class Args(
        val modelType: ModelType,
        val localConfigId: com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId?,
        val apiConfigId: com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId?,
        val ttsProviderId: com.browntowndev.enclave.domain.model.config.TtsProviderId?,
        val mediaProviderId: com.browntowndev.enclave.domain.model.config.MediaProviderId?,
        val liveVoiceProviderId: LiveVoiceProviderId?,
    )

    val calls = mutableListOf<Args>()

    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId?,
        apiConfigId: com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId?,
        ttsProviderId: com.browntowndev.enclave.domain.model.config.TtsProviderId?,
        mediaProviderId: com.browntowndev.enclave.domain.model.config.MediaProviderId?,
        liveVoiceProviderId: LiveVoiceProviderId?,
    ) {
        calls += Args(modelType, localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId)
    }
}

/** Recording clear-default fake for chat tests (ENC-28). */
class FakeChatClearDefaultModelUseCase :
    com.browntowndev.enclave.domain.usecase.byok.ClearDefaultModelUseCase(FakeDefaultModelRepository()) {
    val calls = mutableListOf<ModelType>()

    override suspend fun invoke(modelType: ModelType) {
        calls += modelType
    }
}

/** Dummy [GetTtsProvidersUseCase] for super-graphs (ENC-28). */
class FakeChatGetTtsProvidersUseCase : GetTtsProvidersUseCase(FakeTtsProviderRepository()) {
    var flow: Flow<List<com.browntowndev.enclave.domain.model.config.TtsProviderAsset>> = MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<com.browntowndev.enclave.domain.model.config.TtsProviderAsset>> = flow
}

/** Dummy [GetLiveVoiceProvidersUseCase] for super-graphs (ENC-28). */
class FakeChatGetLiveVoiceProvidersUseCase : GetLiveVoiceProvidersUseCase(FakeLiveVoiceProviderRepository()) {
    var flow: Flow<List<com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset>> =
        MutableStateFlow(emptyList())

    override fun invoke(): Flow<List<com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset>> = flow
}

/** Recording [ResolveAssignedModelSelectionUseCase] fake for chat tests (ENC-28). */
class FakeResolveAssignedModelSelectionUseCase :
    ResolveAssignedModelSelectionUseCase(
        FakeChatGetDefaultModelsUseCase(),
        com.browntowndev.enclave.domain.usecase.modelconfig.GetLocalModelAssetsUseCaseImpl(
            FakeLocalModelRepository(),
        ),
        com.browntowndev.enclave.domain.usecase.byok.GetApiModelAssetsUseCaseImpl(
            FakeApiModelRepository(),
        ),
        FakeChatGetTtsProvidersUseCase(),
        FakeChatGetLiveVoiceProvidersUseCase(),
    ) {
    var result: ResolvedAssignedModelSelection? = null
    val calls = mutableListOf<ModelType>()

    override suspend fun invoke(modelType: ModelType): ResolvedAssignedModelSelection? {
        calls += modelType
        return result
    }
}

/** [SettingsAssignmentUseCases] fake for chat tests (ENC-28). */
class FakeChatSettingsAssignmentUseCases(
    override val getDefaultModels: FakeChatGetDefaultModelsUseCase = FakeChatGetDefaultModelsUseCase(),
    override val setDefaultModel: FakeChatSetDefaultModelUseCase = FakeChatSetDefaultModelUseCase(),
    override val clearDefaultModel: FakeChatClearDefaultModelUseCase = FakeChatClearDefaultModelUseCase(),
    override val resolveAssignedModelSelection: FakeResolveAssignedModelSelectionUseCase =
        FakeResolveAssignedModelSelectionUseCase(),
) : SettingsAssignmentUseCases

/** [SettingsPreferencesUseCases] fake for chat tests: only getSettings is exercised (ENC-28). */
class FakeChatSettingsPreferencesUseCases(
    override val getSettings: FakeChatGetSettingsUseCase = FakeChatGetSettingsUseCase(),
) : SettingsPreferencesUseCases {
    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by chat tests")

    override val updateTheme: com.browntowndev.enclave.domain.usecase.settings.UpdateThemeUseCase get() = unused()
    override val updateHapticResponse: com.browntowndev.enclave.domain.usecase.settings.UpdateHapticResponseUseCase get() = unused()
    override val updateCustomizationEnabled: com.browntowndev.enclave.domain.usecase.settings.UpdateCustomizationEnabledUseCase get() = unused()
    override val updateSelectedPromptOption: com.browntowndev.enclave.domain.usecase.settings.UpdateSelectedPromptOptionUseCase get() = unused()
    override val updateCustomPromptText: com.browntowndev.enclave.domain.usecase.settings.UpdateCustomPromptTextUseCase get() = unused()
    override val updateAllowMemories: com.browntowndev.enclave.domain.usecase.settings.UpdateAllowMemoriesUseCase get() = unused()
    override val updateSearchEnabled: com.browntowndev.enclave.domain.usecase.settings.UpdateSearchEnabledUseCase get() = unused()
    override val updateSearchProvider: com.browntowndev.enclave.domain.usecase.settings.UpdateSearchProviderUseCase get() = unused()
    override val saveTavilyApiKey: com.browntowndev.enclave.domain.usecase.settings.SaveTavilyApiKeyUseCase get() = unused()
    override val clearTavilyApiKey: com.browntowndev.enclave.domain.usecase.settings.ClearTavilyApiKeyUseCase get() = unused()
    override val saveExaApiKey: com.browntowndev.enclave.domain.usecase.settings.SaveExaApiKeyUseCase get() = unused()
    override val clearExaApiKey: com.browntowndev.enclave.domain.usecase.settings.ClearExaApiKeyUseCase get() = unused()
    override val updateXSearchEnabled: com.browntowndev.enclave.domain.usecase.settings.UpdateXSearchEnabledUseCase get() = unused()
    override val saveXApiKey: com.browntowndev.enclave.domain.usecase.settings.SaveXApiKeyUseCase get() = unused()
    override val clearXApiKey: com.browntowndev.enclave.domain.usecase.settings.ClearXApiKeyUseCase get() = unused()
    override val updateShowSearchProviderSwitchAlert: com.browntowndev.enclave.domain.usecase.settings.UpdateShowSearchProviderSwitchAlertUseCase get() = unused()
    override val updateBackgroundInferenceEnabled: com.browntowndev.enclave.domain.usecase.settings.UpdateBackgroundInferenceEnabledUseCase get() = unused()
    override val updateAutomaticErrorTrackingEnabled: com.browntowndev.enclave.domain.usecase.settings.UpdateAutomaticErrorTrackingEnabledUseCase get() = unused()
}

/** [SettingsUseCases] umbrella fake for chat tests (ENC-28). */
class FakeChatSettingsUseCases(
    override val preferences: FakeChatSettingsPreferencesUseCases = FakeChatSettingsPreferencesUseCases(),
    override val assignments: FakeChatSettingsAssignmentUseCases = FakeChatSettingsAssignmentUseCases(),
    override val memories: SettingsMemoriesUseCases =
        com.browntowndev.enclave.domain.usecase.settings.SettingsMemoriesUseCasesImpl(FakeMemoriesRepository()),
) : SettingsUseCases {
    private fun unused(): Nothing = throw UnsupportedOperationException("Not used by chat tests")

    override val localModels: SettingsLocalModelUseCases get() = unused()
    override val apiProviders: SettingsApiProviderUseCases get() = unused()
    override val deletion: SettingsDeletionUseCases get() = unused()
    override val tts: SettingsTtsUseCases get() = unused()
    override val liveVoice: SettingsLiveVoiceUseCases get() = unused()
    override val media: SettingsMediaUseCases get() = unused()
    override val auth: SettingsAuthUseCases get() = unused()
    override val dataControls: SettingsDataControlsUseCases get() = unused()
}

/**
 * [com.browntowndev.enclave.domain.port.repository.UiSelectionRepository] wrapper that
 * delegates to [FakeUiSelectionRepository] and records setChatMode calls (ENC-28).
 */
class RecordingUiSelectionRepository(
    private val delegate: FakeUiSelectionRepository = FakeUiSelectionRepository(),
) : com.browntowndev.enclave.domain.port.repository.UiSelectionRepository by delegate {
    val setChatModeCalls = mutableListOf<com.browntowndev.enclave.domain.model.chat.Mode>()

    override suspend fun setChatMode(mode: com.browntowndev.enclave.domain.model.chat.Mode) {
        setChatModeCalls += mode
        delegate.setChatMode(mode)
    }
}

/**
 * [com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator] subclass that
 * records clearOptimisticUser() calls (replaces the former spyk) (ENC-28).
 */
class RecordingInferenceOrchestrator(
    chatUseCases: ChatUseCases,
    settingsUseCases: SettingsUseCases,
    cancelInferenceUseCase: CancelInferenceUseCase,
    activeChatTurnSnapshotPort: com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort,
    chatRepository: com.browntowndev.enclave.domain.port.repository.ChatRepository,
    errorHandler: com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler,
    savedStateHandle: androidx.lifecycle.SavedStateHandle,
    pipelineExecutor: PipelineExecutorPort,
    moderatePromptUseCase: ModeratePromptUseCase,
    loggingPort: com.browntowndev.enclave.domain.port.inference.LoggingPort,
    applicationScope: kotlinx.coroutines.CoroutineScope,
) : com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator(
        chatUseCases = chatUseCases,
        settingsUseCases = settingsUseCases,
        cancelInferenceUseCase = cancelInferenceUseCase,
        activeChatTurnSnapshotPort = activeChatTurnSnapshotPort,
        chatRepository = chatRepository,
        errorHandler = errorHandler,
        savedStateHandle = savedStateHandle,
        pipelineExecutor = pipelineExecutor,
        moderatePromptUseCase = moderatePromptUseCase,
        loggingPort = loggingPort,
        applicationScope = applicationScope,
    ) {
    var clearOptimisticUserCalls = 0

    override fun clearOptimisticUser() {
        clearOptimisticUserCalls++
        super.clearOptimisticUser()
    }
}

// ---------- ChatUseCases bundle fake ----------

/** [ChatUseCases] fake with individually configurable member fakes (ENC-28). */
class FakeChatUseCases(
    override val createAssistantPlaceholderUseCase: FakeCreateAssistantPlaceholderUseCase =
        FakeCreateAssistantPlaceholderUseCase(),
    override val submitPromptUseCase: FakeSubmitPromptUseCase = FakeSubmitPromptUseCase(),
    override val mergeDbMessagesWithOptimisticUseCase: MergeDbMessagesWithOptimisticUseCase =
        MergeDbMessagesWithOptimisticUseCase(),
    override val generateChatResponseUseCase: FakeGenerateChatResponseUseCase = FakeGenerateChatResponseUseCase(),
    override val getChatCapablePresetsUseCase: FakeGetChatCapablePresetsUseCase = FakeGetChatCapablePresetsUseCase(),
    override val generateChatTitleUseCase: FakeGenerateChatTitleUseCase = FakeGenerateChatTitleUseCase(),
    override val getChatUseCase: FakeGetChatUseCase = FakeGetChatUseCase(),
    override val listenToSpeechUseCase: FakeListenToSpeechUseCase = FakeListenToSpeechUseCase(),
    override val mergeMessagesUseCase: MergeMessagesUseCase = MergeMessagesUseCase(),
    override val processFileAttachmentUseCase: FakeProcessFileAttachmentUseCase = FakeProcessFileAttachmentUseCase(),
) : ChatUseCases
