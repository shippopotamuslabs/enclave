package com.browntowndev.pocketcrew.feature.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutionEventPort
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.ATTACHED_IMAGE_INSPECT
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.TAVILY_EXTRACT
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition.Companion.TAVILY_WEB_SEARCH
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.PlayStreamingTtsAudioUseCase

import com.browntowndev.pocketcrew.domain.usecase.chat.StreamingPlaybackStatus
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.feature.chat.ChatModeMapper.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


/**
 * ViewModel for the Chat screen.
 * Uses Flow-based state management - derives UI state from database and settings flows.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
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
    private val playStreamingTtsAudioUseCase: PlayStreamingTtsAudioUseCase,
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
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

    private val _isGenerating = MutableStateFlow(false)

    // Mutable state for input text (not persisted, managed locally)
    private val _inputText = MutableStateFlow("")

    // Mutable state for selected mode (not persisted, managed locally)
    private val _selectedMode = MutableStateFlow(ChatModeUi.FAST)

    private val _selectedImageUri = MutableStateFlow<String?>(null)

    // Track current chat ID for continuing conversations
    private val _currentChatId = MutableStateFlow<ChatId?>(null)

    // Holds dynamically loaded display names
    private val _modelDisplayNames = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    // Tracks the current active tool call banner (transient, non-persisted)
    private val _activeToolCallBanner = MutableStateFlow<ToolCallBannerUi?>(null)

    // Tracks IDs of active tool events to enforce min 1s display rule
    private val activeToolIds = mutableSetOf<String>()
    private val activeToolJobs = mutableMapOf<String, Job>()
    private val toolStartTimes = mutableMapOf<String, Long>()

    private val photoAttachmentPolicyFlow = combine(
        settingsUseCases.getSettings(),
        _selectedMode,
    ) { settings, selectedMode ->
        settings to selectedMode
    }.mapLatest { (settings, selectedMode) ->
        resolvePhotoAttachmentPolicy(settings, selectedMode)
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

    // Mutable state for TTS playback status
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
    private fun observeToolEvents() {
        toolExecutionEventPort.events.onEach { event ->
            val currentChatIdVal = _currentChatId.value ?: initialChatId
            val turnChatId = event.chatId

            loggingPort.debug("ChatViewModel", "Received tool execution event: type=${event::class.java.simpleName} eventId=${event.eventId} turnChatId=${turnChatId?.value} currentChatId=${currentChatIdVal?.value}")

            // Only show if it matches the current conversation context
            if (turnChatId != currentChatIdVal) return@onEach

            when (event) {
                is ToolExecutionEvent.Started -> {
                    // Cancel any pending cleanup for this tool if it matches a previous ID
                    activeToolJobs[event.eventId]?.cancel()
                    activeToolIds.add(event.eventId)
                    toolStartTimes[event.eventId] = System.currentTimeMillis()

                    val toolName = event.toolName.lowercase()
                    _activeToolCallBanner.value = ToolCallBannerUi(
                        kind = when (toolName) {
                            TAVILY_WEB_SEARCH.name -> ToolCallBannerKind.SEARCH
                            TAVILY_EXTRACT.name -> ToolCallBannerKind.EXTRACT
                            ATTACHED_IMAGE_INSPECT.name -> ToolCallBannerKind.IMAGE
                            ToolDefinition.SEARCH_CHAT_HISTORY.name -> ToolCallBannerKind.MEMORY
                            ToolDefinition.SEARCH_CHAT.name -> ToolCallBannerKind.MEMORY
                            else -> ToolCallBannerKind.SEARCH
                        },
                        label = when (toolName) {
                            TAVILY_WEB_SEARCH.name -> "Searching with Tavily"
                            TAVILY_EXTRACT.name -> "Reading webpage content"
                            ATTACHED_IMAGE_INSPECT.name -> "Inspecting image"
                            ToolDefinition.SEARCH_CHAT_HISTORY.name -> "Searching chat history"
                            ToolDefinition.SEARCH_CHAT.name -> "Searching chat"
                            else -> "Executing tool"
                        }
                    )
                }

                is ToolExecutionEvent.Extracting -> {
                    _activeToolCallBanner.value = ToolCallBannerUi(
                        kind = ToolCallBannerKind.EXTRACT,
                        label = "Reading ${event.url}"
                    )
                    // Mark matching sources as extracted in the active turn snapshot.
                    markSourceExtracted(event.url)
                }

                is ToolExecutionEvent.Finished -> {
                    val startTime = toolStartTimes[event.eventId] ?: return@onEach
                    val elapsed = System.currentTimeMillis() - startTime
                    val remaining = (1000 - elapsed).coerceAtLeast(0)

                    // Collect sources for the banner if available
                    val sources = event.resultJson?.let {
                        com.browntowndev.pocketcrew.domain.util.TavilyResultParser.parse(MessageId("temp"), it)
                    } ?: emptyList()

                    activeToolJobs[event.eventId] = viewModelScope.launch {
                        if (sources.isNotEmpty()) {
                            _activeToolCallBanner.value = _activeToolCallBanner.value?.copy(tavilySources = sources)
                        }
                        delay(remaining)
                        activeToolIds.remove(event.eventId)
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
        _selectedImageUri,
        _speechState,
    ) { settings: SettingsData, inputText: String, selectedMode: ChatModeUi, selectedImageUri: String?, speechState: SpeechState ->
        UiInputs(settings, inputText, selectedMode, selectedImageUri, speechState)
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        uiInputsWithPolicyFlow,
        inferenceLockManager.isInferenceBlocked,
        dbMessagesFlow,
        activeTurnSnapshotFlow,
        ttsPlaybackFlow,
    ) {
        inputsWithPolicy: UiInputsWithPolicy,
        isBlocked: Boolean,
        messages: List<Message>,
        activeTurnSnapshotState: ActiveTurnSnapshotState,
        ttsPlayback: Pair<ChatActionState, Boolean> ->

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

        // Sort chronologically and map domain messages to UI messages
        var isGenerating = false
        var canStop = true
        var hasActiveIndicator = false
        var activeIndicatorMessageId: MessageId? = null
        val chatMessages: List<ChatMessage> = projectedMessages.messages
            .map { message: Message ->
                val chatMessage = mapToChatMessage(message)
                val state = chatMessage.indicatorState
                if (state != null && state !is IndicatorState.None) {
                    hasActiveIndicator = true
                    activeIndicatorMessageId = chatMessage.id
                    if (!isGenerating && (state is IndicatorState.Generating ||
                        state is IndicatorState.Thinking ||
                        state is IndicatorState.Processing ||
                        state is IndicatorState.EngineLoading)
                    ) {
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
            selectedImageUri = inputs.selectedImageUri,
            isPhotoAttachmentEnabled = attachmentPolicy.isEnabled,
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
            chatId = message.chatId,
            role = role,
            content = ContentUi(
                text = message.content.text,
                pipelineStep = message.content.pipelineStep,
                imageUri = message.content.imageUri,
                tavilySources = message.tavilySources,
            ),
            formattedTimestamp = formatTimestamp(message.createdAt),
            indicatorState = computeIndicatorState(message),
            modelDisplayName = modelDisplayName,
            tavilySources = message.tavilySources,
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

        // Cancel any existing TTS playback and release resources
        streamingTtsJob?.cancel()
        streamingTtsJob = null
        playStreamingTtsAudioUseCase.stop()

        streamingTtsJob = viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(TAG, "Failed to play TTS audio", "Failed to play audio")
        ) {
            playStreamingTtsAudioUseCase(text)
                .collect { status ->
                    when (status) {
                        is StreamingPlaybackStatus.Initializing -> {
                            loggingPort.debug(TAG, "TTS streaming initializing")
                        }
                        is StreamingPlaybackStatus.Playing -> {
                            _isPlayingTts.value = true
                            loggingPort.debug(TAG, "TTS streaming playing")
                        }
                        is StreamingPlaybackStatus.Completed -> {
                            _isPlayingTts.value = false
                            streamingTtsJob = null
                            // Release AudioTrack resources after playback completes
                            playStreamingTtsAudioUseCase.stop()
                            loggingPort.debug(TAG, "TTS streaming completed")
                        }
                        is StreamingPlaybackStatus.Error -> {
                            _isPlayingTts.value = false
                            streamingTtsJob = null
                            playStreamingTtsAudioUseCase.stop()
                            loggingPort.error(TAG, "TTS streaming failed: ${status.message}", status.cause)
                        }
                    }
                }
        }
    }

    fun onStopTts() {
        streamingTtsJob?.cancel()
        streamingTtsJob = null
        playStreamingTtsAudioUseCase.stop()
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
            val settings = settingsUseCases.getSettings().first()
            if (!resolvePhotoAttachmentPolicy(settings, mode).isEnabled) {
                _selectedImageUri.value = null
            }
        }
    }

    fun onImageSelected(uri: String?) {
        if (uri == null) {
            _selectedImageUri.value = null
            return
        }

        viewModelScope.launch(
            errorHandler.coroutineExceptionHandler(
                TAG,
                "Failed to prepare image",
                "Could not attach the selected image. Please try again.",
            )
        ) {
            val settings = settingsUseCases.getSettings().first()
            val policy = resolvePhotoAttachmentPolicy(settings, _selectedMode.value)
            if (!policy.isEnabled) {
                _selectedImageUri.value = null
                return@launch
            }
            _selectedImageUri.value = stageImageAttachmentUseCase(uri)
        }
    }

    fun clearSelectedImage() {
        _selectedImageUri.value = null
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
        _selectedImageUri.value = null
        _requestedTurnKey.value = null
        inferenceJob?.cancel()
        // Ensure SavedStateHandle is also cleared so chatId doesn't persist on recreation
        savedStateHandle["chatId"] = null
    }

    fun onSendMessage() {
        val input = _inputText.value
        viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to send message", "Could not send message. Please try again.")) {
            val settings = settingsUseCases.getSettings().first()
            val selectedImageUri = if (resolvePhotoAttachmentPolicy(settings, _selectedMode.value).isEnabled) {
                _selectedImageUri.value
            } else {
                null
            }

            if (input.isNotBlank() || selectedImageUri != null) {
                // Determine the chat ID: prefer currentChatId, then initialChatId, otherwise empty
                val chatIdForMessage = _currentChatId.value ?: initialChatId ?: ChatId("")

                // Create domain message for persistence
                val domainMessage = Message(
                    id = MessageId(UUID.randomUUID().toString()),
                    chatId = chatIdForMessage,
                    content = Content(text = input, imageUri = selectedImageUri),
                    role = Role.USER,
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
                _selectedImageUri.value = null

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

    private fun markSourceExtracted(url: String) {
        val key = activeTurnKeyFlow.value ?: return
        viewModelScope.launch {
            activeChatTurnSnapshotPort.markSourcesExtracted(
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
        val selectedImageUri: String?,
        val speechState: SpeechState,
    )

    private data class PhotoAttachmentPolicy(
        val isEnabled: Boolean,
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
        settings: SettingsData,
        selectedMode: ChatModeUi,
    ): PhotoAttachmentPolicy {
        val apiVisionConfigured = activeModelProvider.getActiveConfiguration(ModelType.VISION)
            ?.let { config -> config.isLocal == false && config.isMultimodal }
            ?: false
        val activeVisionCapable = when (selectedMode) {
            ChatModeUi.FAST -> activeModelProvider.getActiveConfiguration(ModelType.FAST)?.isMultimodal == true
            ChatModeUi.THINKING -> activeModelProvider.getActiveConfiguration(ModelType.THINKING)?.isMultimodal == true
            ChatModeUi.CREW -> false
        }

        return when (selectedMode) {
            ChatModeUi.FAST, ChatModeUi.THINKING -> {
                if (activeVisionCapable || apiVisionConfigured) {
                    PhotoAttachmentPolicy(isEnabled = true)
                } else {
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        disabledReason = "Photo attachments require a vision-capable Fast/Thinking model or a configured API vision model.",
                    )
                }
            }
            ChatModeUi.CREW -> {
                if (apiVisionConfigured) {
                    PhotoAttachmentPolicy(isEnabled = true)
                } else {
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        disabledReason = "Crew mode requires a configured API vision model for photo attachments.",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing inference flow
        inferenceJob?.cancel()
        stopListening()
    }
}
