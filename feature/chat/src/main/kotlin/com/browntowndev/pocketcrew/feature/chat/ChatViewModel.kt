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
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.usecase.chat.StageImageAttachmentUseCase
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
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
    inferenceLockManager: InferenceLockManager,
    private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
    private val activeModelProvider: ActiveModelProviderPort,
    private val errorHandler: ViewModelErrorHandler,
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

    // Mutable state for input text (not persisted, managed locally)
    private val _inputText = MutableStateFlow("")

    // Mutable state for selected mode (not persisted, managed locally)
    private val _selectedMode = MutableStateFlow(ChatModeUi.FAST)

    private val _selectedImageUri = MutableStateFlow<String?>(null)

    // Track current chat ID for continuing conversations
    private val _currentChatId = MutableStateFlow<ChatId?>(null)

    // Accumulates real-time inference updates before database persistence
    // Merged with database messages in uiState for real-time UI updates
    private val _inFlightMessages = MutableStateFlow<Map<MessageId, MessageSnapshot>>(emptyMap())

    // Holds dynamically loaded display names
    private val _modelDisplayNames = MutableStateFlow<Map<ModelType, String>>(emptyMap())

    private val photoAttachmentPolicyFlow = combine(
        settingsUseCases.getSettings(),
        _selectedMode,
    ) { settings, selectedMode ->
        settings to selectedMode
    }.mapLatest { (settings, selectedMode) ->
        resolvePhotoAttachmentPolicy(settings, selectedMode)
    }

    // Job for tracking inference flow collection (for cancellation in onCleared)
    private var inferenceJob: Job? = null

    init {
        // Load display names asynchronously
        viewModelScope.launch {
            val names = mutableMapOf<ModelType, String>()
            for (type in ModelType.entries) {
                names[type] = modelDisplayNamesUseCase(type)
            }
            _modelDisplayNames.value = names
        }
    }

    /**
     * Main UI state flow.
     * Combines settings, messages from database, inference lock state, and in-flight messages.
     * Uses nested combine for 6 flows (Kotlin only supports up to 5-arg combine natively).
     */
    private val uiInputsFlow = combine(
        settingsUseCases.getSettings(),
        _inputText,
        _selectedMode,
        _selectedImageUri,
    ) { settings: SettingsData, inputText: String, selectedMode: ChatModeUi, selectedImageUri: String? ->
        UiInputs(settings, inputText, selectedMode, selectedImageUri)
    }

    private val uiInputsWithPolicyFlow = combine(
        uiInputsFlow,
        photoAttachmentPolicyFlow,
    ) { inputs, attachmentPolicy ->
        UiInputsWithPolicy(inputs, attachmentPolicy)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        uiInputsWithPolicyFlow,
        inferenceLockManager.isInferenceBlocked,
        _currentChatId.flatMapLatest { chatId: ChatId? ->
            val id: ChatId? = chatId ?: initialChatId
            if (id == null) {
                flowOf(emptyList())
            } else {
                chatUseCases.getChat(id).debounce(50)
            }
        },
        _inFlightMessages
    ) {
        inputsWithPolicy: UiInputsWithPolicy,
        isBlocked: Boolean,
        messages: List<Message>,
        inFlight: Map<MessageId, MessageSnapshot> ->

        val inputs = inputsWithPolicy.inputs
        val attachmentPolicy = inputsWithPolicy.attachmentPolicy
        val settings: SettingsData = inputs.settings
        val inputText: String = inputs.inputText
        val selectedMode: ChatModeUi = inputs.selectedMode
        
        // Convert DB messages to map for merging
        val dbMessagesMap: Map<MessageId, Message> = messages.associateBy { m: Message -> m.id }
        
        // Convert in-flight MessageSnapshots to Message objects for merging
        val inFlightMessagesMap: Map<MessageId, Message> = inFlight.mapValues { entry: Map.Entry<MessageId, MessageSnapshot> ->
            val snapshot: MessageSnapshot = entry.value
            Message(
                id = snapshot.messageId,
                chatId = _currentChatId.value ?: initialChatId ?: ChatId(""),
                role = Role.ASSISTANT,
                content = Content(text = snapshot.content, pipelineStep = snapshot.pipelineStep),
                thinkingRaw = snapshot.thinkingRaw.ifBlank { null },
                thinkingDurationSeconds = snapshot.thinkingDurationSeconds,
                thinkingStartTime = snapshot.thinkingStartTime.takeIf { st: Long -> st != 0L },
                thinkingEndTime = snapshot.thinkingEndTime.takeIf { et: Long -> et != 0L },
                createdAt = 0L,
                messageState = snapshot.messageState,
                modelType = snapshot.modelType
            )
        }
        
        // Merge using use case - DB COMPLETE wins, otherwise use in-flight
        val mergedMessages: Map<MessageId, Message> = dbMessagesMap.mapValues { (id, dbMessage) ->
            val inFlightMessage = inFlightMessagesMap[id]
            chatUseCases.mergeMessagesUseCase(dbMessage, inFlightMessage) ?: dbMessage
        }
        
        // Also include any in-flight messages that don't have a DB counterpart
        val allMergedMessages = mergedMessages + inFlightMessagesMap.filterKeys { it !in mergedMessages }
        
        // Sort chronologically and map domain messages to UI messages
        var isGenerating = false
        var hasActiveIndicator = false
        val chatMessages: List<ChatMessage> = allMergedMessages.values
            .sortedBy { it.createdAt }
            .map { message: Message ->
                val chatMessage = mapToChatMessage(message)
                val state = chatMessage.indicatorState
                if (state != null && state !is IndicatorState.None) {
                    hasActiveIndicator = true
                    if (!isGenerating && (state is IndicatorState.Generating ||
                        state is IndicatorState.Thinking ||
                        state is IndicatorState.Processing)
                    ) {
                        isGenerating = true
                    }
                }
                chatMessage
            }

        ChatUiState(
            messages = chatMessages,
            inputText = inputText,
            selectedImageUri = inputs.selectedImageUri,
            isPhotoAttachmentEnabled = attachmentPolicy.isEnabled,
            photoAttachmentDisabledReason = attachmentPolicy.disabledReason,
            selectedMode = selectedMode,
            isGlobalInferenceBlocked = isBlocked,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse,
            chatId = _currentChatId.value ?: initialChatId,
            isGenerating = isGenerating,
            hasActiveIndicator = hasActiveIndicator
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState()
    )

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
            ),
            formattedTimestamp = formatTimestamp(message.createdAt),
            indicatorState = computeIndicatorState(message),
            modelDisplayName = modelDisplayName
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
    private fun formatTimestamp(timestamp: Long): String {
        // Handle 0 timestamp (not set)
        if (timestamp == 0L) return "Now"
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

    fun createNewChat() {
        _currentChatId.value = null
        _inputText.value = ""
        _selectedImageUri.value = null
        _inFlightMessages.value = emptyMap()
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
                    role = Role.USER
                )

                // Step 1: Save user message (creates new chat if needed)
                // Also creates placeholder assistant message, returns assistant message ID and chat ID
                val promptResult = chatUseCases.processPrompt(domainMessage)

                // Update current chat ID to continue this conversation
                _currentChatId.value = promptResult.chatId

                // Clear input text
                _inputText.value = ""
                _selectedImageUri.value = null

                // Clear previous in-flight messages
                _inFlightMessages.value = emptyMap()

                // Use mode to route to appropriate service
                // Collect flow to update _inFlightMessages for real-time UI updates
                inferenceJob = chatUseCases.generateChatResponse(
                    prompt = input,
                    userMessageId = promptResult.userMessageId,
                    assistantMessageId = promptResult.assistantMessageId,
                    chatId = promptResult.chatId,
                    mode = _selectedMode.value.toDomain()
                ).onEach { state ->
                    // Merge new messages with existing in-flight messages
                    // In-flight messages override database messages for same messageId
                    _inFlightMessages.value += state.messages
                }.catch { cause ->
                    errorHandler.handleError(TAG, "Inference failed", cause, "Failed to generate response. Please try again.")
                }.onCompletion { cause ->
                    // Clear in-flight after flow completion
                    // Database has been updated with final state via persistAccumulatedMessages
                    _inFlightMessages.value = emptyMap()
                }.launchIn(viewModelScope)
            }
        }
    }

    fun onAttach() {
        // Stub - was used for lifecycle handling
    }

    fun onShieldTap() {
        // Stub - was used for shield functionality
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

    /**
     * Internal test helper to allow unit tests to verify mapping logic.
     */
    internal fun mapToChatMessageForTesting(message: Message): ChatMessage = mapToChatMessage(message)

    private data class UiInputs(
        val settings: SettingsData,
        val inputText: String,
        val selectedMode: ChatModeUi,
        val selectedImageUri: String?,
    )

    private data class PhotoAttachmentPolicy(
        val isEnabled: Boolean,
        val disabledReason: String? = null,
    )

    private data class UiInputsWithPolicy(
        val inputs: UiInputs,
        val attachmentPolicy: PhotoAttachmentPolicy,
    )

    private suspend fun resolvePhotoAttachmentPolicy(
        settings: SettingsData,
        selectedMode: ChatModeUi,
    ): PhotoAttachmentPolicy {
        val apiVisionConfigured = activeModelProvider.getActiveConfiguration(ModelType.VISION)
            ?.let { config -> config.isLocal == false && config.visionCapable }
            ?: false
        val activeVisionCapable = when (selectedMode) {
            ChatModeUi.FAST -> activeModelProvider.getActiveConfiguration(ModelType.FAST)?.visionCapable == true
            ChatModeUi.THINKING -> activeModelProvider.getActiveConfiguration(ModelType.THINKING)?.visionCapable == true
            ChatModeUi.CREW -> false
        }

        return when (selectedMode) {
            ChatModeUi.FAST, ChatModeUi.THINKING -> {
                if (settings.alwaysUseVisionModel && !apiVisionConfigured) {
                    PhotoAttachmentPolicy(
                        isEnabled = false,
                        disabledReason = "Always Use Vision Model requires a configured API vision model.",
                    )
                } else if (activeVisionCapable || apiVisionConfigured) {
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
    }
}
