package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.GetChatUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Chat screen.
 * Uses Flow-based state management - derives UI state from database and settings flows.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
    private val savedStateHandle: SavedStateHandle,
    inferenceLockManager: InferenceLockManager,
    private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
) : ViewModel() {

    /**
     * Optional chat ID for continuing an existing conversation.
     * Can be passed via navigation or set programmatically.
     */
    val initialChatId: Long?
        get() = savedStateHandle.get<Long>("chatId")

    // Mutable state for input text (not persisted, managed locally)
    private val _inputText = MutableStateFlow("")

    // Mutable state for selected mode (not persisted, managed locally)
    private val _selectedMode = MutableStateFlow(Mode.FAST)

    // Track current chat ID for continuing conversations
    private val _currentChatId = MutableStateFlow<Long?>(null)

    /**
     * Main UI state flow.
     * Combines settings, messages from database, and inference lock state.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ChatUiState> = combine(
        settingsUseCases.getSettings(),
        _inputText,
        _selectedMode,
        inferenceLockManager.isInferenceBlocked,
        _currentChatId.flatMapLatest { chatId ->
            val id = chatId ?: initialChatId ?: 0L
            if (id == 0L) {
                flowOf(emptyList())
            } else {
                chatUseCases.getChat(id)
            }
        }
    ) { settings, inputText, selectedMode, isBlocked, messages ->
        // Map domain messages to UI messages
        val chatMessages = messages.map { message ->
            mapToChatMessage(message)
        }

        ChatUiState(
            messages = chatMessages,
            inputText = inputText,
            selectedMode = selectedMode,
            isGlobalInferenceBlocked = isBlocked,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse
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
     */
    private fun computeIndicatorState(message: Message): IndicatorState? {
        if (message.role == Role.USER) return IndicatorState.None
        
        return when (message.messageState) {
            MessageState.PROCESSING -> {
                IndicatorState.Processing
            }
            MessageState.THINKING -> {
                IndicatorState.Thinking(message.thinkingSteps, requireNotNull(message.thinkingDurationSeconds))
            }
            MessageState.GENERATING -> {
                IndicatorState.Generating(
                    thinkingData = if (message.thinkingDurationSeconds != null && message.thinkingRaw != null)
                        ThinkingDataUi(message.thinkingDurationSeconds, message.thinkingSteps)
                    else null
                )
            }
            MessageState.COMPLETE -> {
                IndicatorState.Complete(
                    thinkingData = if (message.thinkingDurationSeconds != null && message.thinkingRaw != null)
                        ThinkingDataUi(message.thinkingDurationSeconds, message.thinkingSteps)
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
        val sdf = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    fun onInputChange(inputText: String) {
        _inputText.value = inputText
    }

    fun onModeChange(mode: Mode) {
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

            viewModelScope.launch {
                // Step 1: Save user message (creates new chat if needed)
                // Also creates placeholder assistant message, returns assistant message ID and chat ID
                val promptResult = chatUseCases.processPrompt(domainMessage)

                // Update current chat ID to continue this conversation
                _currentChatId.value = promptResult.chatId

                // Clear input text
                _inputText.value = ""

                // Use mode to route to appropriate service
                chatUseCases.generateChatResponse(
                    prompt = input,
                    userMessageId = promptResult.userMessageId,
                    assistantMessageId = promptResult.assistantMessageId,
                    chatId = promptResult.chatId,
                    mode = _selectedMode.value
                ).launchIn(this)
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
}
