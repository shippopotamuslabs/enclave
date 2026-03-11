package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Base state for chat-specific mutable state (messages, input, etc.).
 * Excludes settings which come from a separate flow.
 */
private data class ChatBaseState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val selectedMode: Mode = Mode.FAST,
    val isInputExpanded: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingSteps: List<String> = emptyList(),
    val showUseTheCrewPopup: Boolean = false,
    val pendingUpgradeMessageId: Long? = null,
    // Track the current chat ID after sending messages to continue the conversation
    val currentChatId: Long? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * Optional chat ID for continuing an existing conversation.
     * Can be passed via navigation or set programmatically.
     */
    val initialChatId: Long?
        get() = savedStateHandle.get<Long>("chatId")

    private val _baseState = MutableStateFlow(ChatBaseState())

    val uiState: StateFlow<ChatUiState> = combine(
        _baseState,
        settingsUseCases.getSettings(),
    ) { base, settings ->
        ChatUiState(
            messages = base.messages,
            inputText = base.inputText,
            selectedMode = base.selectedMode,
            isInputExpanded = base.isInputExpanded,
            isThinking = base.isThinking,
            thinkingSteps = base.thinkingSteps,
            showUseTheCrewPopup = base.showUseTheCrewPopup,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(),
    )

    fun onInputChange(inputText: String) {
        _baseState.update { it.copy(inputText = inputText) }
    }

    fun onModeChange(mode: Mode) {
        _baseState.update { it.copy(selectedMode = mode) }
    }

    fun onExpandToggle() {
        _baseState.update { it.copy(isInputExpanded = !it.isInputExpanded) }
    }

    fun onSendMessage() {
        val currentState = _baseState.value
        if (currentState.inputText.isNotBlank()) {
            val userMessageContent = currentState.inputText

            // Determine the chat ID: prefer currentChatId (from previous messages),
            // then initialChatId (from navigation), otherwise 0 to create new chat
            val chatIdForMessage = currentState.currentChatId ?: initialChatId ?: 0L

            // Create domain message for persistence
            // The processPrompt call will create real DB entries and return real IDs
            // Use id = 0 to trigger Room's autoGenerate = true (0 is the default that triggers auto-generation)
            val domainMessage = Message(
                id = 0L,
                chatId = chatIdForMessage,
                content = userMessageContent,
                role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER
            )

            viewModelScope.launch {
                // Step 1: Save user message (creates new chat if needed)
                // Also creates placeholder assistant message, returns assistant message ID and chat ID
                val promptResult = chatUseCases.processPrompt(domainMessage)

                // Now add messages to UI with real IDs from the database
                val userMessage = ChatMessage(
                    id = promptResult.userMessageId,
                    chatId = promptResult.chatId,
                    role = MessageRole.User,
                    content = userMessageContent,
                    formattedTimestamp = "Now",
                )

                val assistantMessage = ChatMessage(
                    id = promptResult.assistantMessageId,
                    chatId = promptResult.chatId,
                    role = MessageRole.Assistant,
                    content = "",
                    formattedTimestamp = "Now",
                )

                _baseState.update {
                    it.copy(
                        messages = it.messages + userMessage + assistantMessage,
                        inputText = "",
                        isThinking = true,
                        thinkingSteps = emptyList(),
                        // Update currentChatId to continue this conversation
                        currentChatId = promptResult.chatId,
                    )
                }

                // Use mode to route to appropriate service, passing IDs for persistence and history rehydration
                chatUseCases.generateChatResponse(
                    prompt = userMessageContent,
                    userMessageId = promptResult.userMessageId,
                    assistantMessageId = promptResult.assistantMessageId,
                    chatId = promptResult.chatId,
                    mode = currentState.selectedMode
                ).collect { generationState ->
                    handleGenerationState(
                        generationState,
                        promptResult.assistantMessageId,
                        promptResult.chatId
                    )
                }
            }
        }
    }

    /**
     * Handles generation state from Fast Mode, Thinking Mode, or Crew Mode (inline execution).
     */
    private fun handleGenerationState(
        generationState: MessageGenerationState,
        assistantMessageId: Long,
        chatId: Long
    ) {
        when (generationState) {
            is MessageGenerationState.ThinkingLive -> {
                _baseState.update {
                    it.copy(
                        isThinking = true,
                        thinkingSteps = generationState.steps
                    )
                }
            }
            is MessageGenerationState.GeneratingText -> {
                _baseState.update { base ->
                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(content = msg.content + generationState.textDelta)
                        } else {
                            msg
                        }
                    }
                    base.copy(messages = updatedMessages)
                }
            }
            is MessageGenerationState.Finished -> {
                val assistantMsg = _baseState.value.messages.find { it.id == assistantMessageId }

                _baseState.update { base ->
                    if (assistantMsg?.content.isNullOrEmpty()) {
                        // No content generated - remove orphan placeholder
                        base.copy(
                            messages = base.messages.filter { it.id != assistantMessageId },
                            isThinking = false,
                            thinkingSteps = emptyList(),
                        )
                    } else {
                        // Show "Use the Crew" popup for Fast responses
                        val updatedBase = base.copy(
                            isThinking = false,
                            thinkingSteps = emptyList(),
                        )

                        // Only show popup if we haven't shown it recently
                        if (!base.showUseTheCrewPopup && base.selectedMode == Mode.FAST) {
                            updatedBase.copy(
                                showUseTheCrewPopup = true,
                                pendingUpgradeMessageId = assistantMessageId
                            )
                        } else {
                            updatedBase
                        }
                    }
                }
            }
            is MessageGenerationState.Blocked -> {
                _baseState.update { base ->
                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(content = "[Blocked: ${generationState.reason}]")
                        } else {
                            msg
                        }
                    }
                    base.copy(
                        messages = updatedMessages,
                        isThinking = false,
                        thinkingSteps = emptyList(),
                    )
                }
            }
            is MessageGenerationState.Failed -> {
                _baseState.update { base ->
                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            msg.copy(content = "[Error: ${generationState.error.message}]")
                        } else {
                            msg
                        }
                    }
                    base.copy(
                        messages = updatedMessages,
                        isThinking = false,
                        thinkingSteps = emptyList(),
                    )
                }
            }
        }
    }

    /**
     * Called when user taps "Use the Crew" popup to upgrade Fast response.
     */
    fun onUseTheCrewTapped() {
        val currentState = _baseState.value
        val pendingMessageId = currentState.pendingUpgradeMessageId

        if (pendingMessageId != null) {
            // Find the Fast response message
            val fastMessage = currentState.messages.find { it.id == pendingMessageId }
            if (fastMessage != null) {
                val userMessagePair = currentState.messages.lastOrNull { it.role == MessageRole.User }
                val userMessageContent = userMessagePair?.content ?: ""
                val userMessageId = userMessagePair?.id ?: 0L

                _baseState.update {
                    it.copy(
                        showUseTheCrewPopup = false,
                        pendingUpgradeMessageId = null,
                        isThinking = true,
                        thinkingSteps = emptyList(),
                    )
                }

                // Start Crew Mode - use the real chat ID from the message
                viewModelScope.launch {
                    chatUseCases.generateChatResponse(
                        prompt = userMessageContent,
                        userMessageId = userMessageId,
                        assistantMessageId = pendingMessageId,
                        chatId = fastMessage.chatId,
                        mode = Mode.CREW
                    ).collect { generationState ->
                        handleGenerationState(generationState, pendingMessageId, fastMessage.chatId)
                    }
                }
            }
        }
    }

    /**
     * Dismisses the "Use the Crew" popup.
     */
    fun onDismissUseTheCrewPopup() {
        _baseState.update {
            it.copy(
                showUseTheCrewPopup = false,
                pendingUpgradeMessageId = null,
            )
        }

        // Auto-dismiss after 5 seconds if not tapped
        viewModelScope.launch {
            delay(5000)
            _baseState.update { state ->
                // Only dismiss if still showing and not yet acted upon
                if (state.showUseTheCrewPopup && state.pendingUpgradeMessageId == null) {
                    state.copy(showUseTheCrewPopup = false)
                } else {
                    state
                }
            }
        }
    }

    fun onAttach() {
        // Stub
    }

    fun onShieldTap() {
        // Stub
    }
}
