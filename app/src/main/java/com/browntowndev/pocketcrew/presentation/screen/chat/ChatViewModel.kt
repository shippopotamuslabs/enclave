package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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
    val responseState: ResponseState = ResponseState.NONE,
    val thinkingSteps: List<String> = emptyList(),
    val thinkingStartTime: Long = 0L,
    val thinkingEndTime: Long = 0L,  // Time when thinking ended (before text generation)
    val thinkingModelDisplayName: String = "",
    val showUseTheCrewPopup: Boolean = false,
    val pendingUpgradeMessageId: Long? = null,
    // Track the current chat ID after sending messages to continue the conversation
    val currentChatId: Long? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
    private val savedStateHandle: SavedStateHandle,
    private val modelRegistry: ModelRegistryPort,
) : ViewModel() {

    /**
     * Optional chat ID for continuing an existing conversation.
     * Can be passed via navigation or set programmatically.
     */
    val initialChatId: Long?
        get() = savedStateHandle.get<Long>("chatId")

    /**
     * Gets the display name of the model for the given mode.
     */
    private fun getModelDisplayName(mode: Mode): String {
        val modelType = when (mode) {
            Mode.FAST -> ModelType.FAST
            Mode.THINKING -> ModelType.THINKING
            Mode.CREW -> ModelType.DRAFT_ONE // First drafter name it begins with
        }
        return modelRegistry.getRegisteredModelSync(modelType)?.metadata?.displayName ?: ""
    }

    /**
     * Gets the display name of the model for the given ModelType.
     */
    private fun getModelDisplayName(modelType: ModelType): String {
        return modelRegistry.getRegisteredModelSync(modelType)?.metadata?.displayName ?: ""
    }

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
            responseState = base.responseState,
            thinkingSteps = base.thinkingSteps,
            thinkingStartTime = base.thinkingStartTime,
            thinkingModelDisplayName = base.thinkingModelDisplayName,
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
                        responseState = ResponseState.PROCESSING,
                        thinkingSteps = emptyList(),
                        thinkingStartTime = 0L,
                        thinkingEndTime = 0L,
                        thinkingModelDisplayName = getModelDisplayName(currentState.selectedMode),
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
    ) {
        when (generationState) {
            is MessageGenerationState.ThinkingLive -> {
                val modelDisplayName = getModelDisplayName(generationState.modelType)
                _baseState.update {
                    it.copy(
                        responseState = ResponseState.THINKING,
                        thinkingSteps = generationState.steps,
                        thinkingModelDisplayName = modelDisplayName,
                        // Record start time when thinking begins
                        thinkingStartTime = if (it.thinkingStartTime == 0L) System.currentTimeMillis() else it.thinkingStartTime
                    )
                }
            }
            is MessageGenerationState.GeneratingText -> {
                val currentThinkingStartTime = _baseState.value.thinkingStartTime
                val currentThinkingSteps = _baseState.value.thinkingSteps
                val currentModelDisplayName = _baseState.value.thinkingModelDisplayName

                _baseState.update { base ->
                    // Record thinking end time when text generation starts
                    val now = System.currentTimeMillis()

                    // Calculate thinking duration and create thinkingData if there were thinking steps
                    val thinkingData = if (currentThinkingStartTime > 0 && currentThinkingSteps.isNotEmpty()) {
                        val durationSeconds = ((now - currentThinkingStartTime) / 1000).toInt()
                        ThinkingData(
                            durationSeconds = durationSeconds,
                            steps = currentThinkingSteps,
                            modelDisplayName = currentModelDisplayName
                        )
                    } else {
                        null
                    }

                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            // Attach thinkingData when text generation starts so "Thought for" appears
                            msg.copy(
                                content = msg.content + generationState.textDelta,
                                thinkingData = thinkingData ?: msg.thinkingData
                            )
                        } else {
                            msg
                        }
                    }
                    // Set to NONE when text generation starts - the "Thought for Xs" indicator
                    // will now appear on the message itself via thinkingData
                    base.copy(
                        messages = updatedMessages,
                        responseState = ResponseState.NONE,
                        thinkingEndTime = now,  // Mark when thinking ended
                    )
                }
            }
            is MessageGenerationState.Finished -> {
                val assistantMsg = _baseState.value.messages.find { it.id == assistantMessageId }
                val currentThinkingStartTime = _baseState.value.thinkingStartTime
                val currentThinkingEndTime = _baseState.value.thinkingEndTime
                val currentThinkingSteps = _baseState.value.thinkingSteps
                val currentModelDisplayName = _baseState.value.thinkingModelDisplayName

                _baseState.update { base ->
                    if (assistantMsg?.content.isNullOrEmpty()) {
                        // No content generated - remove orphan placeholder
                        base.copy(
                            messages = base.messages.filter { it.id != assistantMessageId },
                            responseState = ResponseState.NONE,
                            thinkingSteps = emptyList(),
                            thinkingStartTime = 0L,
                            thinkingEndTime = 0L,
                            thinkingModelDisplayName = "",
                        )
                    } else {
                        // Use thinkingData that was already set when GeneratingText started
                        // (which captures only thinking time, not generation time)
                        val existingThinkingData = assistantMsg.thinkingData

                        // Only create new thinkingData if it wasn't already set
                        val thinkingData = existingThinkingData ?: if (currentThinkingStartTime > 0 && currentThinkingSteps.isNotEmpty()) {
                            val endTime = if (currentThinkingEndTime > 0) currentThinkingEndTime else System.currentTimeMillis()
                            val durationSeconds = ((endTime - currentThinkingStartTime) / 1000).toInt()
                            ThinkingData(
                                durationSeconds = durationSeconds,
                                steps = currentThinkingSteps,
                                modelDisplayName = currentModelDisplayName
                            )
                        } else {
                            null
                        }

                        // Update the assistant message with thinkingData (if any)
                        val updatedMessages = base.messages.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(thinkingData = thinkingData)
                            } else {
                                msg
                            }
                        }

                        // Show "Use the Crew" popup for Fast responses (no thinking steps)
                        val updatedBase = base.copy(
                            messages = updatedMessages,
                            responseState = ResponseState.NONE,
                            thinkingSteps = emptyList(),
                            thinkingStartTime = 0L,
                            thinkingEndTime = 0L,
                            thinkingModelDisplayName = "",
                        )

                        // Only show popup if we haven't shown it recently and this was a Fast mode response
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
                        responseState = ResponseState.NONE,
                        thinkingSteps = emptyList(),
                        thinkingStartTime = 0L,
                        thinkingEndTime = 0L,
                        thinkingModelDisplayName = "",
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
                        responseState = ResponseState.NONE,
                        thinkingSteps = emptyList(),
                        thinkingStartTime = 0L,
                        thinkingEndTime = 0L,
                        thinkingModelDisplayName = "",
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
                        responseState = ResponseState.PROCESSING,
                        thinkingSteps = emptyList(),
                        thinkingStartTime = 0L,
                        thinkingEndTime = 0L,
                        // Initial model is DRAFT_ONE - will be updated via ThinkingLive.modelType
                        thinkingModelDisplayName = getModelDisplayName(ModelType.DRAFT_ONE),
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
                        handleGenerationState(generationState, pendingMessageId)
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
