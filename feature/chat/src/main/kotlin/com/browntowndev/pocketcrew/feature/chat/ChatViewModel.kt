package com.browntowndev.pocketcrew.feature.chat

import com.browntowndev.pocketcrew.domain.port.repository.StudioRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.ChevronRight
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.ATTACHED_IMAGE_INSPECT
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.WEB_EXTRACT
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.WEB_SEARCH
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportDestination
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportFormat
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactExportOutcome
import com.browntowndev.pocketcrew.domain.model.artifact.ExportArtifactDocumentsCommand
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackControllerPort
import com.browntowndev.pocketcrew.domain.port.media.TtsPlaybackStatus
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.usecase.artifact.ExportArtifactDocumentsUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.util.WebSearchResultParser
import com.browntowndev.pocketcrew.feature.chat.ChatModeMapper.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive


/**
 * ViewModel for the Chat screen.
 * Uses Flow-based state management - derives UI state from database and settings flows.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
@Suppress("LargeClass")
class ChatViewModel @Inject constructor(
    private val settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
    private val stageImageAttachmentUseCase: StageImageAttachmentUseCase,
    private val savedStateHandle: SavedStateHandle,
    private val cancelInferenceUseCase: CancelInferenceUseCase,
    inferenceLockManager: InferenceLockManager,
    private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
    private val activeModelProvider: ActiveModelProviderPort,
    private val toolExecutionEventPort: ToolExecutionEventPort,
    private val errorHandler: ViewModelErrorHandler,
    private val loggingPort: LoggingPort,
    private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
    private val playbackController: TtsPlaybackControllerPort,
    private val studioRepository: StudioRepositoryPort,
    private val shareChatUseCase: com.browntowndev.pocketcrew.domain.usecase.share.ShareChatUseCase,
    private val chatRepository: ChatRepository,
    private val exportArtifactDocumentsUseCase: ExportArtifactDocumentsUseCase,
) : ViewModel() {

    suspend fun persistArtifactExportPreference(
        artifactId: String,
        format: MimeType,
    ) {
        withContext(Dispatchers.IO) {
            chatRepository.updateArtifactExportPreference(artifactId = artifactId, format = format)
        }
    }

    fun onArtifactCardClick(messageId: MessageId, card: ArtifactCardUiModel) {
        _artifactExportSheet.value =
            ArtifactExportSheetState.Visible(
                messageId = messageId,
                artifactId = card.artifactId,
                title = card.title,
                step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                selectedFormat = null,
                selectedDestination = null,
                isExporting = false,
            )
    }

    fun dismissArtifactExportSheet() {
        _artifactExportSheet.value = ArtifactExportSheetState.Hidden
    }

    fun onArtifactExportSelectFormat(format: WizardExportFormat) {
        val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
        _artifactExportSheet.value = visible.copy(selectedFormat = format)
    }

    fun onArtifactExportSelectDestination(destination: WizardExportDestination) {
        val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
        _artifactExportSheet.value = visible.copy(selectedDestination = destination)
    }

    fun onArtifactExportWizardBack() {
        when (val sheet = _artifactExportSheet.value) {
            is ArtifactExportSheetState.Visible -> {
                when (sheet.step) {
                    ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
                        _artifactExportSheet.value =
                            sheet.copy(
                                step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                                selectedDestination = null,
                            )
                    }
                    ArtifactExportWizardStep.CHOOSE_FORMAT -> {
                        dismissArtifactExportSheet()
                    }
                }
            }
            else -> Unit
        }
    }

    fun onArtifactExportWizardNext() {
        val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
        if (visible.selectedFormat == null) return
        _artifactExportSheet.value =
            visible.copy(step = ArtifactExportWizardStep.CHOOSE_DESTINATION)
    }

    fun onConfirmArtifactExport() {
        val visible = _artifactExportSheet.value as? ArtifactExportSheetState.Visible ?: return
        if (visible.selectedFormat == null || visible.selectedDestination == null) return
        viewModelScope.launch {
            _artifactExportSheet.update { prev: ArtifactExportSheetState ->
                (prev as? ArtifactExportSheetState.Visible)?.copy(isExporting = true) ?: prev
            }
            val artifact =
                resolveArtifactGenerationRequest(
                    messageId = visible.messageId,
                    artifactId = visible.artifactId,
                )
            if (artifact == null) {
                _artifactExportMessages.emit("Could not find this document.")
                dismissArtifactExportSheet()
                return@launch
            }
            if (artifact.content.isBlank()) {
                _artifactExportMessages.emit("Document is still generating. Try again in a moment.")
                dismissArtifactExportSheet()
                return@launch
            }
            val format =
                when (visible.selectedFormat) {
                    WizardExportFormat.PDF -> ArtifactExportFormat.PDF
                    WizardExportFormat.WORD -> ArtifactExportFormat.WORD
                    WizardExportFormat.BOTH -> ArtifactExportFormat.BOTH
                }
            val destination =
                when (visible.selectedDestination) {
                    WizardExportDestination.SHARE -> ArtifactExportDestination.SHARE
                    WizardExportDestination.DOWNLOAD -> ArtifactExportDestination.DOWNLOAD
                }
            val result =
                exportArtifactDocumentsUseCase(
                    ExportArtifactDocumentsCommand(
                        documentJson = artifact.content,
                        baseTitle = artifact.title.ifBlank { "artifact" },
                        format = format,
                        destination = destination,
                    ),
                )
            if (result.isSuccess) {
                val outcome = result.getOrNull()!!
                when (outcome) {
                    is ArtifactExportOutcome.Shared ->
                        _artifactExportMessages.emit(
                            "Share sheet opened for ${outcome.fileCount} file(s).",
                        )
                    is ArtifactExportOutcome.Downloaded ->
                        _artifactExportMessages.emit(
                            "Saved to Downloads: ${outcome.savedDisplayNames.joinToString()}",
                        )
                }
                when (format) {
                    ArtifactExportFormat.PDF ->
                        persistArtifactExportPreference(artifact.artifactId, MimeType.PDF)
                    ArtifactExportFormat.WORD ->
                        persistArtifactExportPreference(artifact.artifactId, MimeType.WORD)
                    ArtifactExportFormat.BOTH -> Unit
                }
                dismissArtifactExportSheet()
            } else {
                val error = result.exceptionOrNull() ?: Exception("Export failed")
                _artifactExportMessages.emit(error.message ?: "Export failed")
                _artifactExportSheet.update { prev: ArtifactExportSheetState ->
                    (prev as? ArtifactExportSheetState.Visible)?.copy(isExporting = false) ?: prev
                }
            }
        }
    }

    private suspend fun resolveArtifactGenerationRequest(
        messageId: MessageId,
        artifactId: String,
    ): com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest? {
        val chatId = _currentChatId.value ?: initialChatId ?: return null
        val messages = chatRepository.getMessagesForChat(chatId).first()
        val message = messages.find { it.id == messageId } ?: return null
        return message.content.artifacts.find { it.artifactId == artifactId }
    }

    /**
     * Optional chat ID for continuing an existing conversation.
     * Can be passed via navigation or set programmatically.
     */
    val initialChatId: ChatId?
        get() = savedStateHandle.get<String>("chatId")?.let { ChatId(it) }

    private val _speechState = MutableStateFlow<SpeechState>(SpeechState.Idle)
    val speechState: StateFlow<SpeechState> = _speechState.asStateFlow()

    private val _stopSpeechSignal = MutableStateFlow(false)

    // Mutable state for input text (not persisted, managed locally)
    private val _inputText = MutableStateFlow("")

    // Mutable state for selected mode (not persisted, managed locally)
    private val _selectedMode = MutableStateFlow(ChatModeUi.FAST)

    private val _selectedImageUris = MutableStateFlow<List<String>>(emptyList())

    private val _selectedFileName = MutableStateFlow<String?>(null)
    private val _selectedFileText = MutableStateFlow<String?>(null)

    // Track current chat ID for continuing conversations
    private val _currentChatId = MutableStateFlow<ChatId?>(null)

    // Holds dynamically loaded display names
    private val _modelDisplayNames = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    private val _shareState = MutableStateFlow<ShareState>(ShareState.Idle)
    private val _isReadOnly = MutableStateFlow(false)
    private val _sharedMessages = MutableStateFlow<List<Message>>(emptyList())

    private val _artifactExportSheet = MutableStateFlow<ArtifactExportSheetState>(ArtifactExportSheetState.Hidden)
    private val _artifactExportMessages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val artifactExportMessages = _artifactExportMessages.asSharedFlow()
    
    // Caches share URLs by ShareOption key to avoid redundant uploads
    private val shareUrlCache = mutableMapOf<com.browntowndev.pocketcrew.domain.model.share.ShareOption, String>()

    // Tracks the current active tool call banner (transient, non-persisted)
    private val _activeToolCallBanner = MutableStateFlow<ToolCallBannerUi?>(null)

    // Tracks IDs of active tool events to enforce min 1s display rule
    private val activeToolIds = mutableSetOf<String>()
    private val activeToolJobs = mutableMapOf<String, Job>()
    private val toolStartTimes = mutableMapOf<String, Long>()
    private val activeToolNames = mutableMapOf<String, String>()
    private val pendingStartArtifacts = mutableMapOf<String, PendingStartArtifact>()

    private data class PendingStartArtifact(
        val title: String,
    )

    private val photoAttachmentPolicyFlow = combine(
        settingsUseCases.getSettings(),
        _selectedMode,
    ) { settings, selectedMode ->
        settings to selectedMode
    }.mapLatest { (settings, selectedMode) ->
        resolvePhotoAttachmentPolicy(selectedMode)
    }
    private val hasTtsProviderAssignedFlow = settingsUseCases.assignments.getDefaultModels()
        .map { assignments ->
            assignments.any { assignment ->
                assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
            }
        }
        .distinctUntilChanged()
    private val chatActionStateFlow = combine(
        _activeToolCallBanner,
        hasTtsProviderAssignedFlow,
    ) { activeToolCallBanner, hasTtsProviderAssigned ->
        ChatActionState(
            activeToolCallBanner = activeToolCallBanner,
            hasTtsProviderAssigned = hasTtsProviderAssigned,
        )
    }

    // Job for tracking inference flow collection (for cancellation in onCleared)
    private var inferenceJob: Job? = null
    private var speechJob: Job? = null

    // Tracks the current streaming TTS job for cancellation
    private var streamingTtsJob: Job? = null

    // Mutable state for TTS playback status (Media3-based)
    private val _isPlayingTts = MutableStateFlow(false)

    private val _requestedTurnKey = MutableStateFlow<ActiveChatTurnKey?>(null)
    private val _acknowledgedTurnKeys = MutableStateFlow<Set<ActiveChatTurnKey>>(emptySet())

    private val activeChatIdFlow = _currentChatId
        .map { chatId -> chatId ?: initialChatId }
        .distinctUntilChanged()

    private val dbMessagesFlow = activeChatIdFlow.flatMapLatest { id: ChatId? ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            chatUseCases.getChat(id)
        }
    }

    private val activeTurnCandidateFlow = combine(
        activeChatIdFlow,
        dbMessagesFlow,
        _requestedTurnKey,
    ) { chatId, messages, requestedTurnKey ->
        val incompleteKey = chatId?.let { id ->
            messages
                .lastOrNull { message ->
                    message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
                }
                ?.let { message -> ActiveChatTurnKey(id, message.id) }
        }
        val requestedKey = requestedTurnKey?.takeIf { key ->
            key.chatId == chatId &&
                messages.none { message ->
                    message.id == key.assistantMessageId && message.messageState == MessageState.COMPLETE
                }
        }
        ActiveTurnCandidate(
            chatId = chatId,
            messages = messages,
            key = requestedKey ?: incompleteKey,
        )
    }

    private val activeTurnKeyFlow = combine(
        activeTurnCandidateFlow,
        _acknowledgedTurnKeys,
    ) { candidate, acknowledgedKeys ->
        candidate to acknowledgedKeys
    }.scan(null as ActiveChatTurnKey?) { currentKey, (candidate, acknowledgedKeys) ->
        val candidateKey = candidate.key?.takeUnless { key -> key in acknowledgedKeys }
        when {
            currentKey != null && currentKey in acknowledgedKeys -> null
            candidateKey != null -> candidateKey
            currentKey != null && candidate.chatId == currentKey.chatId &&
                candidate.messages.any { message ->
                    message.id == currentKey.assistantMessageId &&
                        message.messageState == MessageState.COMPLETE
                } -> currentKey
            else -> null
        }
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null,
        )

    private val activeTurnSnapshotFlow = activeTurnKeyFlow.flatMapLatest { key ->
        if (key == null) {
            flowOf(ActiveTurnSnapshotState(key = null, snapshot = null))
        } else {
            activeChatTurnSnapshotPort.observe(key).map { snapshot ->
                ActiveTurnSnapshotState(key = key, snapshot = snapshot)
            }
        }
    }

    init {
        // Load display names asynchronously
        viewModelScope.launch {
            val names = mutableMapOf<ModelType, String>()
            for (type in ModelType.entries) {
                names[type] = modelDisplayNamesUseCase(type)
            }
            _modelDisplayNames.value = names
        }

        // Observe tool execution events to show transient UI banners
        observeToolEvents()

        // Observe speech recognition results
        observeSpeechResults()
    }

    private fun observeSpeechResults() {
        _speechState
            .onEach { state ->
                when (state) {
                    is SpeechState.FinalText -> {
                        _inputText.value = state.text
                    }
                    is SpeechState.Error -> {
                        loggingPort.error(TAG, "Speech recognition error: ${state.message}")
                        errorHandler.handleError(
                            tag = TAG,
                            message = "Speech Recognition Failed",
                            throwable = Exception(state.message),
                            userMessage = state.message
                        )
                    }
                    else -> {}
                }
            }
            .launchIn(viewModelScope)
    }

    /**
     * Observes tool lifecycle events and manages banner visibility with a 1s minimum gate.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun observeToolEvents() {
        toolExecutionEventPort.events.onEach { event ->
            val currentChatIdVal = _currentChatId.value ?: initialChatId
            val turnChatId = event.chatId

            loggingPort.debug(
                "ChatViewModel",
                "Received tool execution event: type=${event::class.java.simpleName} " +
                    "eventId=${event.eventId} turnChatId=${turnChatId?.value} " +
                    "currentChatId=${currentChatIdVal?.value}"
            )

            // Only show if it matches the current conversation context
            if (turnChatId != currentChatIdVal) return@onEach

            when (event) {
                is ToolExecutionEvent.Started -> {
                    activeToolJobs[event.eventId]?.cancel()
                    activeToolIds.add(event.eventId)
                    toolStartTimes[event.eventId] = System.currentTimeMillis()
                    val toolName = event.toolName.lowercase()
                    activeToolNames[event.eventId] = toolName
                    val toolDef = ToolDefinition.fromName(toolName)
                    _activeToolCallBanner.value = ToolCallBannerUi(
                        kind = when (toolName) {
                            WEB_SEARCH.name -> ToolCallBannerKind.SEARCH
                            WEB_EXTRACT.name -> ToolCallBannerKind.EXTRACT
                            ATTACHED_IMAGE_INSPECT.name -> ToolCallBannerKind.IMAGE
                            ToolDefinition.SEARCH_CHAT_HISTORY.name -> ToolCallBannerKind.MEMORY
                            ToolDefinition.SEARCH_CHAT.name -> ToolCallBannerKind.MEMORY
                            ToolDefinition.START_ARTIFACT.name -> ToolCallBannerKind.ARTIFACT
                            ToolDefinition.APPEND_BLOCKS.name -> ToolCallBannerKind.ARTIFACT
                            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name -> ToolCallBannerKind.ARTIFACT
                            ToolDefinition.X_SEARCH.name -> ToolCallBannerKind.X_SEARCH
                            ToolDefinition.GENERATE_IMAGE.name -> ToolCallBannerKind.MEDIA
                            ToolDefinition.GENERATE_VIDEO.name -> ToolCallBannerKind.MEDIA
                            ToolDefinition.GENERATE_MUSIC.name -> ToolCallBannerKind.MEDIA
                            else -> ToolCallBannerKind.SEARCH
                        },
                        label = toolDef?.displayName ?: when (toolName) {
                            WEB_SEARCH.name.lowercase() -> "Searching the web"
                            WEB_EXTRACT.name.lowercase() -> "Reading webpage content"
                            ATTACHED_IMAGE_INSPECT.name.lowercase() -> "Inspecting image"
                            ToolDefinition.SEARCH_CHAT_HISTORY.name.lowercase() -> "Searching chat history"
                            ToolDefinition.SEARCH_CHAT.name.lowercase() -> "Searching chat"
                            ToolDefinition.START_ARTIFACT.name.lowercase() -> "Starting Artifact"
                            ToolDefinition.APPEND_BLOCKS.name.lowercase() -> "Writing Artifact"
                            ToolDefinition.SEARCH_CURRENT_ARTIFACT.name.lowercase() -> "Searching Artifact"
                            ToolDefinition.X_SEARCH.name.lowercase() -> "Searching X"
                            ToolDefinition.GENERATE_IMAGE.name.lowercase() -> "Generating Image"
                            ToolDefinition.GENERATE_VIDEO.name.lowercase() -> "Generating Video"
                            ToolDefinition.GENERATE_MUSIC.name.lowercase() -> "Generating Music"
                            else -> "Executing tool"
                        }
                    )

                    if (toolName == ToolDefinition.START_ARTIFACT.name.lowercase()) {
                        try {
                            val params = jsonParser.decodeFromString<
                                com.browntowndev.pocketcrew.domain.model.inference.StartArtifactParams
                                >(
                                com.browntowndev.pocketcrew.domain.model.inference
                                    .StartArtifactParamsArgumentNormalizer.normalize(event.argumentsJson),
                            )
                            pendingStartArtifacts[event.eventId] = PendingStartArtifact(title = params.title)
                        } catch (e: Exception) {
                            loggingPort.error(TAG, "Failed to parse start_artifact request", e)
                        }
                    }

                    if (toolName == ToolDefinition.APPEND_BLOCKS.name.lowercase()) {
                        try {
                            val params = jsonParser.decodeFromString<
                                com.browntowndev.pocketcrew.domain.model.inference.AppendBlocksParams
                                >(event.argumentsJson)
                            // We don't necessarily need to update activeArtifacts here 
                            // because it will be emitted as a final result when complete.
                        } catch (e: Exception) {
                            loggingPort.error(TAG, "Failed to parse append_blocks request", e)
                        }
                    }
                }

                is ToolExecutionEvent.Extracting -> {
                    _activeToolCallBanner.value = ToolCallBannerUi(
                        kind = ToolCallBannerKind.EXTRACT,
                        label = "Reading ${event.url}"
                    )
                    // Mark matching sources as extracted in the active turn snapshot.
                    markWebSearchSourceExtracted(event.url)
                }

                is ToolExecutionEvent.Finished -> {
                    val startTime = toolStartTimes[event.eventId] ?: return@onEach
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = (1000 - elapsed).coerceAtLeast(0)

                    // Capture the current key and artifact BEFORE the delay to avoid race conditions
                    val capturedKey = activeTurnKeyFlow.value
                    val capturedStart = pendingStartArtifacts[event.eventId]
                    val toolNameAtFinish = (activeToolNames[event.eventId] ?: "").lowercase()

                    // Collect sources for the banner if available
                    val sources = event.resultJson?.let {
                        WebSearchResultParser.parse(MessageId("temp"), it)
                    } ?: emptyList()

                    val artifactId = event.resultJson
                        ?.let { raw -> runCatching { jsonParser.parseToJsonElement(raw).jsonObject["artifactId"]?.jsonPrimitive?.content }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }

                    activeToolJobs[event.eventId] = viewModelScope.launch {
                        if (sources.isNotEmpty()) {
                            _activeToolCallBanner.value = _activeToolCallBanner.value?.copy(webSearchSources = sources)
                        }

                        // Attach artifact placeholder immediately so final persistence can pick it up.
                        if (toolNameAtFinish == ToolDefinition.START_ARTIFACT.name.lowercase() &&
                            capturedKey != null &&
                            capturedStart != null &&
                            artifactId != null
                        ) {
                            val artifact = com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest(
                                artifactId = artifactId,
                                title = capturedStart.title,
                                content = "",
                            )
                            activeChatTurnSnapshotPort.attachArtifact(
                                key = capturedKey,
                                assistantMessageId = capturedKey.assistantMessageId,
                                artifact = artifact
                            )
                            pendingStartArtifacts.remove(event.eventId)
                        }

                        // Keep the banner visible for at least ~1s, but do not block artifact attachment.
                        delay(remaining)

                        activeToolIds.remove(event.eventId)
                        activeToolNames.remove(event.eventId)
                        // Only clear if no other tools are active to prevent UI flickering
                        if (activeToolIds.isEmpty()) {
                            _activeToolCallBanner.value = null
                        }
                        toolStartTimes.remove(event.eventId)
                        activeToolJobs.remove(event.eventId)
                    }
                }
            }
        }.launchIn(viewModelScope)
    }

    /**
     * Main UI state flow.
     * Combines settings, database messages, inference lock state, and active turn snapshots.
     * Uses nested combine for 6 flows (Kotlin only supports up to 5-arg combine natively).
     */
    private val uiInputsFlow = combine(
        settingsUseCases.getSettings(),
        _inputText,
        _selectedMode,
        _selectedImageUris,
        _selectedFileName,
        _speechState,
    ) { args: Array<Any?> ->
        UiInputs(
            settings = args[0] as SettingsData,
            inputText = args[1] as String,
            selectedMode = args[2] as ChatModeUi,
            selectedImageUris = args[3] as List<String>,
            selectedFileName = args[4] as String?,
            speechState = args[5] as SpeechState
        )
    }

    private val uiInputsWithPolicyFlow = combine(
        uiInputsFlow,
        photoAttachmentPolicyFlow,
    ) { inputs, attachmentPolicy ->
        UiInputsWithPolicy(inputs, attachmentPolicy)
    }

    private val ttsPlaybackFlow = combine(
        chatActionStateFlow,
        _isPlayingTts,
    ) { chatActionState, isPlayingTts ->
        chatActionState to isPlayingTts
    }

    private val studioMediaFlow = combine(
        studioRepository.observeEphemeralMedia(),
        studioRepository.observeAllMedia()
    ) { ephemeral, persisted ->
        (ephemeral + persisted).associateBy { it.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        uiInputsWithPolicyFlow,
        inferenceLockManager.isInferenceBlocked,
        dbMessagesFlow,
        activeTurnSnapshotFlow,
        ttsPlaybackFlow,
        studioMediaFlow,
        _shareState,
        _isReadOnly,
        _sharedMessages,
        _artifactExportSheet,
    ) { args: Array<Any?> ->
        val inputsWithPolicy = args[0] as UiInputsWithPolicy
        val isBlocked = args[1] as Boolean
        val dbMessages = args[2] as List<Message>
        val activeTurnSnapshotState = args[3] as ActiveTurnSnapshotState
        val ttsPlayback = args[4] as Pair<ChatActionState, Boolean>
        val studioMediaMap = args[5] as Map<String, StudioMediaAsset>
        val shareState = args[6] as ShareState
        val isReadOnly = args[7] as Boolean
        val sharedMessages = args[8] as List<Message>
        val artifactExportSheet = args[9] as ArtifactExportSheetState

        val messages = if (isReadOnly) sharedMessages else dbMessages

        val chatActionState = ttsPlayback.first
        val isPlayingTts = ttsPlayback.second
        val inputs = inputsWithPolicy.inputs
        val attachmentPolicy = inputsWithPolicy.attachmentPolicy
        val settings: SettingsData = inputs.settings
        val inputText: String = inputs.inputText
        val selectedMode: ChatModeUi = inputs.selectedMode
        val speechState: SpeechState = inputs.speechState

        val projectedMessages = projectChatMessages(
            dbMessages = messages,
            activeSnapshot = activeTurnSnapshotState.snapshot,
            activeKey = activeTurnSnapshotState.key,
        )
        acknowledgeProjectedHandoffIfNeeded(
            activeKey = activeTurnSnapshotState.key,
            handoffReady = projectedMessages.handoffReady,
        )

        // Reconcile media URIs with Studio Repository
        val reconciledMessages = projectedMessages.messages.map { message ->
            if (message.content.generatedMediaAssetIds.isEmpty()) message
            else {
                val reconciledUris = message.content.generatedMediaAssetIds.mapIndexed { index, id ->
                    studioMediaMap[id]?.localUri ?: message.content.generatedMediaUris.getOrNull(index) ?: ""
                }
                message.copy(
                    content = message.content.copy(
                        generatedMediaUris = reconciledUris
                    )
                )
            }
        }

        // Sort chronologically and map domain messages to UI messages
        var isGenerating = false
        var canStop = true
        var hasActiveIndicator = false
        var activeIndicatorMessageId: MessageId? = null
        val chatMessages: List<ChatMessage> = reconciledMessages
            .map { message: Message ->
                val chatMessage = mapToChatMessage(message)
                val state = chatMessage.indicatorState
                if (state != null && state !is IndicatorState.None) {
                    hasActiveIndicator = true
                    activeIndicatorMessageId = chatMessage.id
                    val isActiveGenerationState = state is IndicatorState.Generating ||
                        state is IndicatorState.Thinking ||
                        state is IndicatorState.Processing ||
                        state is IndicatorState.EngineLoading
                    if (!isGenerating && isActiveGenerationState) {
                        isGenerating = true
                        if (state is IndicatorState.EngineLoading) {
                            canStop = false
                        }
                    }
                }
                chatMessage
            }

        ChatUiState(
            messages = chatMessages,
            inputText = inputText,
            speechState = speechState,
            selectedImageUris = inputs.selectedImageUris,
            selectedFileName = inputs.selectedFileName,
            isPhotoAttachmentEnabled = attachmentPolicy.isEnabled,
            photoAttachmentMaxItems = attachmentPolicy.maxItems,
            photoAttachmentDisabledReason = attachmentPolicy.disabledReason,
            selectedMode = selectedMode,
            isGlobalInferenceBlocked = isBlocked,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse,
            chatId = _currentChatId.value ?: initialChatId,
            isGenerating = isGenerating,
            canStop = canStop,
            hasActiveIndicator = hasActiveIndicator,
            activeIndicatorMessageId = activeIndicatorMessageId,
            activeToolCallBanner = chatActionState.activeToolCallBanner,
            backgroundInferenceEnabled = settings.backgroundInferenceEnabled,
            hasTtsProviderAssigned = chatActionState.hasTtsProviderAssigned,
            isPlayingTts = isPlayingTts,
            shareState = shareState,
            isReadOnly = isReadOnly,
            artifactExportSheet = artifactExportSheet,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

    private fun acknowledgeProjectedHandoffIfNeeded(
        activeKey: ActiveChatTurnKey?,
        handoffReady: Boolean,
    ) {
        if (activeKey == null || !handoffReady) return
        if (activeKey in _acknowledgedTurnKeys.value) return

        _acknowledgedTurnKeys.update { keys -> keys + activeKey }
        if (_requestedTurnKey.value == activeKey) {
            _requestedTurnKey.update { null }
        }
    }

    /**
     * Maps domain Message to ChatMessage for the UI layer.
     */
    private fun mapToChatMessage(message: Message): ChatMessage {
        val role = when (message.role) {
            Role.USER -> MessageRole.User
            Role.ASSISTANT -> MessageRole.Assistant
            Role.SYSTEM -> MessageRole.Assistant // Map SYSTEM to Assistant for display
        }
        val modelDisplayName = message.modelType?.let { modelType ->
            _modelDisplayNames.value[modelType]
        } ?: "Agent"

        return ChatMessage(
            id = message.id,
            chatId = requireNotNull(message.chatId) { "mapToChatMessage called with null chatId on message ${message.id.value}" },
            role = role,
            content = ContentUi(
                text = message.content.text,
                pipelineStep = message.content.pipelineStep,
                imageUris = message.content.imageUris,
                webSearchSources = message.webSearchSources,
                artifacts = message.content.artifacts.map { it.toArtifactCardUiModel() },
                fileAttachments = message.content.fileAttachments,
                generatedMediaUris = message.content.generatedMediaUris,
                generatedMediaAssetIds = message.content.generatedMediaAssetIds,
                generatedMediaTypes = message.content.generatedMediaTypes,
                generatedMediaPrompts = message.content.generatedMediaPrompts,
            ),
            formattedTimestamp = formatTimestamp(message.createdAt),
            indicatorState = computeIndicatorState(message),
            modelDisplayName = modelDisplayName,
            webSearchSources = message.webSearchSources,
        )
    }

    /**
     * Computes the indicator state based on the message's messageState.
     * This derives UI indicator state from the database state.
     *
     * Note: duration may be null during THINKING state because thinkingEndTime
     * is only set when thinking completes. In this case, we return the indicator
     * without duration (or compute from timestamps if available).
     */
    private fun computeIndicatorState(message: Message): IndicatorState? {
        if (message.role == Role.USER) return IndicatorState.None
        
        return when (message.messageState) {
            MessageState.ENGINE_LOADING -> {
                IndicatorState.EngineLoading
            }
            MessageState.PROCESSING -> {
                IndicatorState.Processing
            }
            MessageState.THINKING -> {
                // Duration may be null during thinking - compute from timestamps if available
                val duration = message.thinkingDurationSeconds
                IndicatorState.Thinking(
                    thinkingRaw = message.thinkingRaw ?: "",
                    thinkingDurationSeconds = duration ?: 0L,
                    thinkingStartTime = message.thinkingStartTime ?: 0L
                )
            }
            MessageState.GENERATING -> {
                val duration = message.thinkingDurationSeconds
                val raw = message.thinkingRaw
                val startTime = message.thinkingStartTime ?: 0L
                IndicatorState.Generating(
                    thinkingData = if (duration != null && raw != null)
                        ThinkingDataUi(duration, raw, startTime)
                    else null
                )
            }
            MessageState.COMPLETE -> {
                val duration = message.thinkingDurationSeconds
                val raw = message.thinkingRaw
                val startTime = message.thinkingStartTime ?: 0L
                IndicatorState.Complete(
                    thinkingData = if (duration != null && raw != null)
                        ThinkingDataUi(duration, raw, startTime)
                    else null
                )
            }
        }
    }

    /**
     * Formats a timestamp for display.
     */
    private fun formatTimestamp(timestamp: Long?): String {
        if (timestamp == null || timestamp == 0L) return ""
        // Simple timestamp formatting - could be enhanced
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    fun onInputChange(inputText: String) {
        _inputText.value = inputText
    }

    fun onEditMessage(message: String) {
        _inputText.value = message
    }

    fun onPlayTts(text: String) {
        if (!uiState.value.hasTtsProviderAssigned) return

        // Cancel any existing TTS playback
        streamingTtsJob?.cancel()
        streamingTtsJob = null
        playbackController.stop()

        streamingTtsJob = viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(TAG, "Failed to play TTS audio", "Failed to play audio")
        ) {
            playbackController.play(text)
                .collect { status ->
                    when (status) {
                        is TtsPlaybackStatus.Initializing -> {
                            loggingPort.debug(TAG, "TTS initializing")
                        }
                        is TtsPlaybackStatus.Playing -> {
                            _isPlayingTts.value = true
                            loggingPort.debug(TAG, "TTS playing")
                        }
                        is TtsPlaybackStatus.Completed -> {
                            _isPlayingTts.value = false
                            streamingTtsJob = null
                            loggingPort.debug(TAG, "TTS completed")
                        }
                        is TtsPlaybackStatus.Error -> {
                            _isPlayingTts.value = false
                            streamingTtsJob = null
                            loggingPort.error(TAG, "TTS failed: ${status.message}", status.cause)
                        }
                        is TtsPlaybackStatus.Stopped -> {
                            _isPlayingTts.value = false
                            streamingTtsJob = null
                            loggingPort.debug(TAG, "TTS stopped")
                        }
                    }
                }
        }
    }

    fun onStopTts() {
        streamingTtsJob?.cancel()
        streamingTtsJob = null
        playbackController.stop()
        _isPlayingTts.value = false
    }

    fun onModeChange(mode: ChatModeUi) {
        _selectedMode.value = mode
        viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(
                TAG,
                "Failed to evaluate attachment policy",
                "Could not update the selected mode.",
            )
        ) {
            val policy = resolvePhotoAttachmentPolicy(mode)
            if (!policy.isEnabled) {
                _selectedImageUris.value = emptyList()
            } else if (_selectedImageUris.value.size > policy.maxItems) {
                _selectedImageUris.value = _selectedImageUris.value.take(policy.maxItems)
            }
        }
    }

    fun onImagesSelected(uris: List<String>) {
        if (uris.isEmpty()) {
            return
        }

        viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(
                TAG,
                "Failed to prepare images",
                "Could not attach the selected images. Please try again.",
            )
        ) {
            val policy = resolvePhotoAttachmentPolicy(_selectedMode.value)
            if (!policy.isEnabled) {
                _selectedImageUris.value = emptyList()
                return@launch
            }
            
            // We append new staged URIs to the existing list, up to the max limit
            val currentStagedUris = _selectedImageUris.value
            val availableSlots = policy.maxItems - currentStagedUris.size
            
            if (availableSlots <= 0) return@launch
            
            val limitedNewUris = uris.take(availableSlots)
            val newlyStagedUris = limitedNewUris.map { stageImageAttachmentUseCase(it) }
            
            _selectedImageUris.value = currentStagedUris + newlyStagedUris
        }
    }

    fun removeImage(uri: String) {
        _selectedImageUris.update { current -> current.filter { it != uri } }
    }

    fun clearSelectedImages() {
        _selectedImageUris.value = emptyList()
    }

    fun onFileSelected(uri: String?) {
        if (uri == null) {
            _selectedFileName.value = null
            _selectedFileText.value = null
            return
        }

        viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(
                TAG,
                "Failed to process file",
                "Could not read the selected file. Please try again.",
            )
        ) {
            val metadata = chatUseCases.processFileAttachmentUseCase(uri)
            _selectedFileName.value = metadata.name
            _selectedFileText.value = metadata.content
        }
    }

    fun clearSelectedFile() {
        _selectedFileName.value = null
        _selectedFileText.value = null
    }

    fun stopGeneration() {
        val currentUiState = uiState.value
        if (!currentUiState.isGenerating || !currentUiState.canStop) {
            loggingPort.debug(
                TAG,
                "stopGeneration ignored isGenerating=${currentUiState.isGenerating} canStop=${currentUiState.canStop}",
            )
            return
        }

        inferenceJob?.cancel()
        cancelInferenceUseCase()
        activeTurnKeyFlow.value?.let { key ->
            viewModelScope.launch {
                activeChatTurnSnapshotPort.clear(key)
                _requestedTurnKey.update { current -> if (current == key) null else current }
                _acknowledgedTurnKeys.update { keys -> keys + key }
            }
        }
        _activeToolCallBanner.value = null
    }

    fun createNewChat() {
        stopListening()
        activeTurnKeyFlow.value?.let { key ->
            viewModelScope.launch {
                activeChatTurnSnapshotPort.clear(key)
                _acknowledgedTurnKeys.update { keys -> keys + key }
            }
        }
        _currentChatId.value = null
        _inputText.value = ""
        _selectedImageUris.value = emptyList()
        _requestedTurnKey.value = null
        inferenceJob?.cancel()
        // Ensure SavedStateHandle is also cleared so chatId doesn't persist on recreation
        savedStateHandle["chatId"] = null
    }

    fun onSendMessage() {
        val input = _inputText.value
        val fileText = _selectedFileText.value
        val fileName = _selectedFileName.value

        viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(
                TAG,
                "Failed to send message",
                "Could not send message. Please try again."
            )
        ) {
            val settings = settingsUseCases.getSettings().first()
            val selectedImageUris = if (resolvePhotoAttachmentPolicy(_selectedMode.value).isEnabled) {
                _selectedImageUris.value
            } else {
                emptyList()
            }

            val fileAttachments = if (fileText != null && fileName != null) {
                listOf(FileAttachment(name = fileName, content = fileText))
            } else {
                emptyList()
            }

            if (input.isNotBlank() || selectedImageUris.isNotEmpty() || fileAttachments.isNotEmpty()) {
                // Determine the chat ID: prefer currentChatId, then initialChatId, otherwise empty
                val chatIdForMessage: ChatId? = _currentChatId.value ?: initialChatId

                // Create domain message for persistence
                val domainMessage = Message(
                    id = MessageId(UUID.randomUUID().toString()),
                    chatId = chatIdForMessage,
                    content = Content(
                        text = input,
                        imageUris = selectedImageUris,
                        fileAttachments = fileAttachments
                    ),
                    role = Role.USER,
                    messageState = MessageState.COMPLETE,
                    createdAt = System.currentTimeMillis()
                )

                // Step 1: Save user message (creates new chat if needed)
                // Also creates placeholder assistant message, returns assistant message ID and chat ID
                val promptResult = chatUseCases.processPrompt(domainMessage)

                // Update current chat ID to continue this conversation
                _currentChatId.value = promptResult.chatId
                savedStateHandle["chatId"] = promptResult.chatId.value
                val turnKey = ActiveChatTurnKey(
                    chatId = promptResult.chatId,
                    assistantMessageId = promptResult.assistantMessageId,
                )
                _acknowledgedTurnKeys.update { keys -> keys - turnKey }
                _requestedTurnKey.value = turnKey

                // Clear input text
                _inputText.value = ""
                _selectedImageUris.value = emptyList()
                _selectedFileName.value = null
                _selectedFileText.value = null

                // Use mode to route to appropriate service
                // Collect the flow to keep direct inference alive and surface errors.
                inferenceJob = chatUseCases.generateChatResponse(
                    prompt = input,
                    userMessageId = promptResult.userMessageId,
                    assistantMessageId = promptResult.assistantMessageId,
                    chatId = promptResult.chatId,
                    mode = _selectedMode.value.toDomain(),
                    backgroundInferenceEnabled = settings.backgroundInferenceEnabled,
                ).onEach {
                    // Snapshot publication is owned by the generation pipeline through ActiveChatTurnSnapshotPort.
                }.catch { cause ->
                    errorHandler.handleError(TAG, "Inference failed", cause, "Failed to generate response. Please try again.")
                }.launchIn(viewModelScope)
            }
        }
    }

    fun onAttach() {
        // Stub - was used for lifecycle handling
    }

    fun onShieldTap() {
        // Placeholder for future shield/security info interaction
    }

    private val _shareUiEvents = kotlinx.coroutines.flow.MutableSharedFlow<ShareUiEvent>(extraBufferCapacity = 1)
    val shareUiEvents = _shareUiEvents.asSharedFlow()

    private var pendingShareMessageId: MessageId? = null

    fun onShareClick(messageId: MessageId) {
        pendingShareMessageId = messageId
        val chatId = _currentChatId.value ?: initialChatId ?: return
        
        // Check if we already have a cached URL for this single turn
        val option = com.browntowndev.pocketcrew.domain.model.share.ShareOption.SingleTurn(chatId, messageId)
        val cachedUrl = shareUrlCache[option]
        
        if (cachedUrl != null) {
            _shareState.value = ShareState.Success(cachedUrl)
        } else {
            _shareState.value = ShareState.ShowingSheet
        }
    }

    fun onShareTurn() {
        val chatId = _currentChatId.value ?: initialChatId ?: return
        val messageId = pendingShareMessageId ?: return
        val option = com.browntowndev.pocketcrew.domain.model.share.ShareOption.SingleTurn(chatId, messageId)

        viewModelScope.launch {
            _shareState.value = ShareState.Loading
            shareChatUseCase(option)
                .onSuccess { sharedChat ->
                    shareUrlCache[option] = sharedChat.shareUrl
                    _shareState.value = ShareState.Success(sharedChat.shareUrl)
                    _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(sharedChat.shareUrl))
                }
                .onFailure { error ->
                    _shareState.value = ShareState.Error(error.message ?: "Failed to share chat")
                }
        }
    }

    fun onShareFullChat() {
        val chatId = _currentChatId.value ?: initialChatId ?: return
        val option = com.browntowndev.pocketcrew.domain.model.share.ShareOption.FullConversation(chatId)

        viewModelScope.launch {
            val cachedUrl = shareUrlCache[option]
            if (cachedUrl != null) {
                _shareState.value = ShareState.Success(cachedUrl)
                _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(cachedUrl))
                return@launch
            }

            _shareState.value = ShareState.Loading
            shareChatUseCase(option)
                .onSuccess { sharedChat ->
                    shareUrlCache[option] = sharedChat.shareUrl
                    _shareState.value = ShareState.Success(sharedChat.shareUrl)
                    _shareUiEvents.emit(ShareUiEvent.OpenShareSheet(sharedChat.shareUrl))
                }
                .onFailure { error ->
                    _shareState.value = ShareState.Error(error.message ?: "Failed to share chat")
                }
        }
    }

    fun onDismissShareSheet() {
        pendingShareMessageId = null
        _shareState.value = ShareState.Idle
    }

    fun onLoadSharedChat(shareId: String) {
        viewModelScope.launch {
            shareChatUseCase.getSharedChat(shareId)
                .onSuccess { sharedChat ->
                    sharedChat?.let { chat ->
                        val mappedMessages = withContext(Dispatchers.Default) {
                            chat.messages.mapIndexed { index, sharedMessage ->
                                Message(
                                    id = MessageId("shared-$index"),
                                    chatId = ChatId("shared-chat"),
                                    role = Role.fromApiValue(sharedMessage.role),
                                    content = Content(text = sharedMessage.content),
                                    createdAt = sharedMessage.timestamp,
                                    messageState = MessageState.COMPLETE
                                )
                            }
                        }
                        _sharedMessages.value = mappedMessages
                        _isReadOnly.value = true
                        _shareState.value = ShareState.Idle
                    }
                }
        }
    }

    fun onMicClick() {
        val currentState = _speechState.value
        if (
            currentState is SpeechState.ModelLoading ||
            currentState is SpeechState.Listening
        ) {
            _stopSpeechSignal.value = true
        } else {
            startListening()
        }
    }

    private fun startListening() {
        _stopSpeechSignal.value = false
        speechJob?.cancel()
        speechJob = chatUseCases.listenToSpeechUseCase(_inputText.value, _stopSpeechSignal.asStateFlow())
            .onEach { state ->
                _speechState.value = state
            }
            .catch { cause ->
                _speechState.value = SpeechState.Error(cause.message ?: "Speech transcription failed.")
            }
            .launchIn(viewModelScope)
    }

    private fun stopListening() {
        speechJob?.cancel()
        speechJob = null
        _speechState.value = SpeechState.Idle
    }

    /**
     * Returns the shield reason if inference is blocked, null otherwise.
     * Part of the simplified state - derived from isGlobalInferenceBlocked.
     */
    fun getShieldReason(): String? {
        return if (uiState.value.isGlobalInferenceBlocked) {
            "Inference is currently blocked"
        } else {
            null
        }
    }

    private fun markWebSearchSourceExtracted(url: String) {
        val key = activeTurnKeyFlow.value ?: return
        viewModelScope.launch {
            activeChatTurnSnapshotPort.markWebSearchSourcesExtracted(
                key = key,
                urls = listOf(url),
            )
        }
    }

    /**
     * Internal test helper to allow unit tests to verify mapping logic.
     */
    internal fun mapToChatMessageForTesting(message: Message): ChatMessage = mapToChatMessage(message)

    private data class UiInputs(
        val settings: SettingsData,
        val inputText: String,
        val selectedMode: ChatModeUi,
        val selectedImageUris: List<String>,
        val selectedFileName: String?,
        val speechState: SpeechState,
    )

    private data class PhotoAttachmentPolicy(
        val isEnabled: Boolean,
        val maxItems: Int = 1,
        val disabledReason: String? = null,
    )

    private data class UiInputsWithPolicy(
        val inputs: UiInputs,
        val attachmentPolicy: PhotoAttachmentPolicy,
    )

    private data class ChatActionState(
        val activeToolCallBanner: ToolCallBannerUi?,
        val hasTtsProviderAssigned: Boolean,
    )

    private data class ActiveTurnCandidate(
        val chatId: ChatId?,
        val messages: List<Message>,
        val key: ActiveChatTurnKey?,
    )

    private data class ActiveTurnSnapshotState(
        val key: ActiveChatTurnKey?,
        val snapshot: AccumulatedMessages?,
    )

    private suspend fun resolvePhotoAttachmentPolicy(
        selectedMode: ChatModeUi,
    ): PhotoAttachmentPolicy {
        val activeConfig = when (selectedMode) {
            ChatModeUi.FAST -> activeModelProvider.getActiveConfiguration(ModelType.FAST)
            ChatModeUi.THINKING -> activeModelProvider.getActiveConfiguration(ModelType.THINKING)
            ChatModeUi.CREW -> null
        }
        val visionConfig = activeModelProvider.getActiveConfiguration(ModelType.VISION)
        val apiVisionConfigured = visionConfig?.let { it.isLocal == false && it.isMultimodal } ?: false
        val activeVisionCapable = activeConfig?.isMultimodal == true

        val maxItems = when {
            activeVisionCapable -> when {
                activeConfig?.isLocal == true -> 1
                activeConfig?.apiProvider == ApiProvider.GOOGLE -> 3600
                else -> 20
            }
            apiVisionConfigured -> when {
                visionConfig?.apiProvider == ApiProvider.GOOGLE -> 3600
                else -> 20
            }
            else -> 0
        }

        return when (selectedMode) {
            ChatModeUi.FAST, ChatModeUi.THINKING -> {
                if (activeVisionCapable || apiVisionConfigured) {
                    PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                } else {
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        maxItems = 0,
                        disabledReason = "Photo attachments require a vision-capable Fast/Thinking model or a configured API vision model.",
                    )
                }
            }
            ChatModeUi.CREW -> {
                if (apiVisionConfigured) {
                    PhotoAttachmentPolicy(isEnabled = true, maxItems = maxItems)
                } else {
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        maxItems = 0,
                        disabledReason = "Crew mode requires a configured API vision model for photo attachments.",
                    )
                }
            }
        }
    }

    companion object {
        private const val TAG = "ChatViewModel"
        private val jsonParser = Json { ignoreUnknownKeys = true }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing inference flow
        inferenceJob?.cancel()
        stopListening()
    }
}
