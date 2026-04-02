package com.browntowndev.pocketcrew.feature.chat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.feature.chat.ChatModeMapper.toDomain
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch


/**
 * ViewModel for the Chat screen.
 * Uses Flow-based state management - derives UI state from database and settings flows.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChatViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
    private val savedStateHandle: SavedStateHandle,
    inferenceLockManager: InferenceLockManager,
    private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
    private val errorHandler: ViewModelErrorHandler,
) : ViewModel() {

    companion object {
        private const val TAG = "ChatViewModel"
    }

    /**
     * Optional chat ID for continuing an existing conversation.
     * Can be passed via navigation or set programmatically.
     */
    val initialChatId: Long?
        get() = savedStateHandle.get<String>("chatId")?.toLongOrNull()

    // Mutable state for input text (not persisted, managed locally)
    private val _inputText = MutableStateFlow("")

    // Mutable state for selected mode (not persisted, managed locally)
    private val _selectedMode = MutableStateFlow(ChatModeUi.FAST)

    // Track current chat ID for continuing conversations
    private val _currentChatId = MutableStateFlow<Long?>(null)

    // Accumulates real-time inference updates before database persistence
    // Merged with database messages in uiState for real-time UI updates
    private val _inFlightMessages = MutableStateFlow<Map<Long, MessageSnapshot>>(emptyMap())

    // Job for tracking inference flow collection (for cancellation in onCleared)
    private var inferenceJob: Job? = null

    /**
     * Main UI state flow.
     * Combines settings, messages from database, inference lock state, and in-flight messages.
     * Uses nested combine for 6 flows (Kotlin only supports up to 5-arg combine natively).
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        combine(
            settingsUseCases.getSettings(),
            _inputText,
            _selectedMode
        ) { settings: SettingsData, inputText: String, selectedMode: ChatModeUi ->
            Triple(settings, inputText, selectedMode)
        },
        inferenceLockManager.isInferenceBlocked,
        _currentChatId.flatMapLatest { chatId: Long? ->
            val id: Long = chatId ?: initialChatId ?: 0L
            if (id == 0L) {
                flowOf(emptyList())
            } else {
                chatUseCases.getChat(id).debounce(50)
            }
        },
        _inFlightMessages
    ) { 
        triple: Triple<SettingsData, String, ChatModeUi>,
        isBlocked: Boolean,
        messages: List<Message>,
        inFlight: Map<Long, MessageSnapshot> ->
        
        val settings: SettingsData = triple.first
        val inputText: String = triple.second
        val selectedMode: ChatModeUi = triple.third
        
        // Convert DB messages to map for merging
        val dbMessagesMap: Map<Long, Message> = messages.associateBy { m: Message -> m.id }
        
        // Convert in-flight MessageSnapshots to Message objects for merging
        val inFlightMessagesMap: Map<Long, Message> = inFlight.mapValues { entry: Map.Entry<Long, MessageSnapshot> ->
            val snapshot: MessageSnapshot = entry.value
            Message(
                id = snapshot.messageId,
                chatId = snapshot.messageId,
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
        val mergedMessages: Map<Long, Message> = dbMessagesMap.mapValues { (id, dbMessage) ->
            val inFlightMessage = inFlightMessagesMap[id]
            chatUseCases.mergeMessagesUseCase(dbMessage, inFlightMessage) ?: dbMessage
        }
        
        // Also include any in-flight messages that don't have a DB counterpart
        val allMergedMessages = mergedMessages + inFlightMessagesMap.filterKeys { it !in mergedMessages }
        
        // Map domain messages to UI messages
        var isGenerating = false
        var hasActiveIndicator = false
        val chatMessages: List<ChatMessage> = allMergedMessages.values.map { message: Message ->
            val chatMessage = mapToChatMessage(message)
            if (chatMessage.indicatorState != null && chatMessage.indicatorState !is IndicatorState.None) {
                hasActiveIndicator = true
                if (chatMessage.indicatorState is IndicatorState.Generating ||
                    chatMessage.indicatorState is IndicatorState.Thinking ||
                    chatMessage.indicatorState is IndicatorState.Processing
                ) {
                    isGenerating = true
                }
            }
            chatMessage
        }

        ChatUiState(
            messages = chatMessages,
            inputText = inputText,
            selectedMode = selectedMode,
            isGlobalInferenceBlocked = isBlocked,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse,
            chatId = _currentChatId.value ?: initialChatId ?: -1L,
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
            modelDisplayNamesUseCase(modelType)
        } ?: "Agent"

        return ChatMessage(
            id = message.id,
            chatId = message.chatId,
            role = role,
            content = ContentUi(
                text = message.content.text,
                pipelineStep = message.content.pipelineStep
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
    }

    fun onSendMessage() {
        val input = _inputText.value
        if (input.isNotBlank()) {
            // Determine the chat ID: prefer currentChatId, then initialChatId, otherwise 0
            val chatIdForMessage = _currentChatId.value ?: initialChatId ?: 0L

            // Create domain message for persistence
            val domainMessage = Message(
                id = 0L,
                chatId = chatIdForMessage,
                content = Content(text = input),
                role = Role.USER
            )

            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to send message", "Could not send message. Please try again.")) {
                // Step 1: Save user message (creates new chat if needed)
                // Also creates placeholder assistant message, returns assistant message ID and chat ID
                val promptResult = chatUseCases.processPrompt(domainMessage)

                // Update current chat ID to continue this conversation
                _currentChatId.value = promptResult.chatId

                // Clear input text
                _inputText.value = ""

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

    override fun onCleared() {
        super.onCleared()
        // Cancel any ongoing inference flow
        inferenceJob?.cancel()
    }
}
