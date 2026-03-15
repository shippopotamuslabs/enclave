package com.browntowndev.pocketcrew.presentation.screen.chat

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.presentation.screen.chat.StepCompletionData
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
    val thinkingDurationSeconds: Int = 0,  // Thinking duration - set when thinking ends
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
    private val inferenceLockManager: InferenceLockManager,
) : ViewModel() {

    /**
     * Computes what thinking indicator to show based on response state and thinking data.
     * Returns Pair of (ProcessingIndicatorState, ThinkingData).
     * Business logic in ViewModel - UI just renders what it's given.
     */
    private fun computeIndicatorState(
        responseState: ResponseState,
        mode: Mode,
        thinkingSteps: List<String>,
        thinkingStartTime: Long,
        thinkingDurationSeconds: Int,
        thinkingModelDisplayName: String
    ): Pair<ProcessingIndicatorState, ThinkingData?> {
        return when (responseState) {
            ResponseState.NONE -> ProcessingIndicatorState.NONE to null

            ResponseState.PROCESSING -> {
                // If there was previous thinking that completed (thinkingDurationSeconds > 0),
                // preserve the thinkingData so the UI can still show "Thought For" header
                if (thinkingDurationSeconds > 0 && thinkingSteps.isNotEmpty()) {
                    ProcessingIndicatorState.PROCESSING to ThinkingData(
                        thinkingDurationSeconds = thinkingDurationSeconds,
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName
                    )
                } else {
                    ProcessingIndicatorState.PROCESSING to null
                }
            }

            ResponseState.THINKING -> {
                if (thinkingSteps.isNotEmpty() && thinkingStartTime > 0) {
                    // Active thinking - show animated indicator with current steps
                    ProcessingIndicatorState.NONE to ThinkingData(
                        thinkingDurationSeconds = 0, // still thinking
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName,
                        thinkingStartTime = thinkingStartTime
                    )
                } else {
                    ProcessingIndicatorState.PROCESSING to null
                }
            }

            ResponseState.GENERATING -> {
                // If thinking completed but generation still running, show "Thought For"
                // thinkingDurationSeconds > 0 means thinking has completed
                if (thinkingDurationSeconds > 0) {
                    ProcessingIndicatorState.GENERATING to ThinkingData(
                        thinkingDurationSeconds = thinkingDurationSeconds,
                        steps = thinkingSteps,
                        modelDisplayName = thinkingModelDisplayName
                    )
                } else {
                    ProcessingIndicatorState.GENERATING to null
                }
            }
        }
    }

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
        inferenceLockManager.isInferenceBlocked,
    ) { base, settings, isGlobalBlocked ->
        // Compute what thinking indicator to show based on response state and thinking data
        val (processingIndicatorState, thinkingData) = computeIndicatorState(
            responseState = base.responseState,
            mode = base.selectedMode,
            thinkingSteps = base.thinkingSteps,
            thinkingStartTime = base.thinkingStartTime,
            thinkingDurationSeconds = base.thinkingDurationSeconds,
            thinkingModelDisplayName = base.thinkingModelDisplayName
        )

        // Compute whether to show the "Thought for Xs" header
        // Show when thinking has completed (duration > 0) and there are steps to display
        val showThoughtForHeader = thinkingData != null &&
            thinkingData.steps.isNotEmpty() &&
            thinkingData.thinkingDurationSeconds > 0

        ChatUiState(
            messages = base.messages,
            inputText = base.inputText,
            selectedMode = base.selectedMode,
            isInputExpanded = base.isInputExpanded,
            responseState = base.responseState,
            thinkingSteps = base.thinkingSteps,
            thinkingStartTime = base.thinkingStartTime,
            thinkingDurationSeconds = base.thinkingDurationSeconds,
            processingIndicatorState = processingIndicatorState,
            thinkingData = thinkingData,
            thinkingModelDisplayName = base.thinkingModelDisplayName,
            showThoughtForHeader = showThoughtForHeader,
            showUseTheCrewPopup = base.showUseTheCrewPopup,
            hapticPress = settings.hapticPress,
            hapticResponse = settings.hapticResponse,
            isGlobalInferenceBlocked = isGlobalBlocked,
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
                        thinkingDurationSeconds = 0,
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
                        currentState.selectedMode
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
        mode: Mode
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
                val currentThinkingDurationSeconds = _baseState.value.thinkingDurationSeconds
                val currentModelDisplayName = _baseState.value.thinkingModelDisplayName

                _baseState.update { base ->
                    // Record thinking end time when text generation starts
                    val now = System.currentTimeMillis()

                    val assistantMsg = base.messages.find { it.id == assistantMessageId }
                    val isCrewMode = mode == Mode.CREW

                    // Determine if we're on the FINAL step (4th step in 4-stage pipeline)
                    // Pipeline: DRAFT_ONE(0), DRAFT_TWO(1), SYNTHESIS(2), FINAL(3)
                    // When 3 steps are already completed, we're on FINAL
                    val completedStepsCount = assistantMsg?.completedSteps?.size ?: 0
                    val isFinalStep = completedStepsCount >= 3

                    // FIX: In Crew mode, only update message.content for FINAL step
                    // Non-FINAL step outputs are stored in StepCompletionData.stepOutput, not in content
                    // Non-Crew mode: always update content (original behavior)
                    val shouldUpdateContent = !isCrewMode || isFinalStep

                    // Preserve thinkingData if there's previous thinking (for both THINKING and CREW modes)
                    // For CREW mode: need to preserve so "Thought For" header shows after StepCompleted
                    val thinkingData = if (currentThinkingStartTime > 0 && currentThinkingSteps.isNotEmpty()) {
                        // Active thinking - calculate duration
                        val thinkingDurationSeconds = ((now - currentThinkingStartTime) / 1000).toInt()
                        ThinkingData(
                            thinkingDurationSeconds = thinkingDurationSeconds,
                            steps = currentThinkingSteps,
                            modelDisplayName = currentModelDisplayName
                        )
                    } else if (base.thinkingDurationSeconds > 0 && currentThinkingSteps.isNotEmpty()) {
                        // Thinking already completed (e.g., after StepCompleted) - preserve duration
                        ThinkingData(
                            thinkingDurationSeconds = base.thinkingDurationSeconds,
                            steps = currentThinkingSteps,
                            modelDisplayName = currentModelDisplayName
                        )
                    } else {
                        null
                    }

                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            // In Crew mode, don't overwrite thinkingData - completedSteps has the thinking
                            // Only update content for non-Crew mode OR FINAL step in Crew mode
                            val newContent = if (shouldUpdateContent) {
                                msg.content + generationState.textDelta
                            } else {
                                msg.content  // Non-FINAL Crew step: content stays empty, output goes to stepOutput
                            }
                            msg.copy(
                                content = newContent,
                                thinkingData = if (isCrewMode) msg.thinkingData else (thinkingData ?: msg.thinkingData)
                            )
                        } else {
                            msg
                        }
                    }
                    // FIX: Set responseState based on mode and step
                    // - Non-Crew mode: PROCESSING (original behavior)
                    // - Crew mode non-FINAL (Draft One, Draft Two, Synthesis): show GENERATING (text not visible)
                    // - Crew mode FINAL (3+ completed): show NONE (text streams to screen)
                    val newResponseState = when {
                        !isCrewMode -> ResponseState.PROCESSING
                        isFinalStep -> ResponseState.NONE  // FINAL: text streams to screen
                        base.responseState == ResponseState.NONE -> ResponseState.NONE
                        else -> ResponseState.GENERATING  // Non-FINAL: show generating indicator
                    }
                    base.copy(
                        messages = updatedMessages,
                        responseState = newResponseState,
                        thinkingStartTime = 0L,  // Stop the timer
                        // Preserve thinkingSteps in ALL modes so UI can show "Thought For Xs" header
                        // The thinkingSteps will be cleared when response is fully complete
                        thinkingSteps = base.thinkingSteps,
                        // Only calculate thinking duration if not already set (e.g., from StepCompleted)
                        // If currentThinkingStartTime is 0, thinking already ended and duration was set in StepCompleted
                        thinkingDurationSeconds = if (currentThinkingStartTime > 0) {
                            ((now - currentThinkingStartTime) / 1000).toInt()
                        } else if (base.thinkingDurationSeconds > 0) {
                            base.thinkingDurationSeconds  // Preserve duration from StepCompleted
                        } else {
                            0
                        },
                    )
                }
            }
            is MessageGenerationState.Finished -> {
                val assistantMsg = _baseState.value.messages.find { it.id == assistantMessageId }
                val currentThinkingStartTime = _baseState.value.thinkingStartTime
                val currentThinkingDurationSeconds = _baseState.value.thinkingDurationSeconds
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
                            thinkingDurationSeconds = 0,
                            thinkingModelDisplayName = "",
                        )
                    } else {
                        // Check if in Crew mode - don't set message.thinkingData in Crew mode
                        // (per-step thinking is stored in completedSteps)
                        val isCrewMode = mode == Mode.CREW

                        // Use thinkingData that was already set when GeneratingText started
                        // (which captures only thinking time, not generation time)
                        val existingThinkingData = assistantMsg.thinkingData

                        // FIX: In Crew mode, don't set message.thinkingData - completedSteps has the thinking
                        // Only set for non-Crew mode (Fast/Thinking modes)
                        val thinkingData = if (isCrewMode) {
                            // In Crew mode, keep existing thinkingData (or null)
                            existingThinkingData
                        } else {
                            // Non-Crew mode: create thinkingData if needed
                            existingThinkingData ?: if (currentThinkingStartTime > 0 && currentThinkingSteps.isNotEmpty()) {
                                // Use the computed thinkingDurationSeconds from base state
                                val duration = base.thinkingDurationSeconds
                                ThinkingData(
                                    thinkingDurationSeconds = duration,
                                    steps = currentThinkingSteps,
                                    modelDisplayName = currentModelDisplayName
                                )
                            } else {
                                null
                            }
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
                            thinkingDurationSeconds = 0,
                            thinkingModelDisplayName = "",
                        )

                        // Only show popup if we haven't shown it recently and this was a Fast mode response
                        if (!base.showUseTheCrewPopup && mode == Mode.FAST) {
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
                        thinkingDurationSeconds = 0,
                        thinkingModelDisplayName = "",
                    )
                }
            }
            is MessageGenerationState.StepCompleted -> {
                // Handle Crew mode step completion
                // After each step completes, we need to:
                // 1. Store step completion data on the message
                // 2. Attach thinking data to show "Thought For" indicator on message
                // 3. Reset thinking time for the next step
                // 4. For FINAL step: add stepOutput to message content (Bug #1 fix)

                // Get model display name from ModelRegistry
                val modelDisplayName = getModelDisplayName(generationState.modelType)
                val stepCompletionData = StepCompletionData.fromMessageGenerationState(generationState, modelDisplayName)

                // Create ThinkingData from this step's thinking so "Thought For" shows on the message
                // In Crew mode, this is stored in completedSteps, not at message level
                val thinkingData = ThinkingData(
                    thinkingDurationSeconds = generationState.thinkingDurationSeconds,
                    steps = generationState.thinkingSteps,
                    modelDisplayName = generationState.modelDisplayName
                )

                _baseState.update { base ->
                    val assistantMsg = base.messages.find { it.id == assistantMessageId }
                    val existingSteps = assistantMsg?.completedSteps ?: emptyList()
                    val updatedSteps = existingSteps + stepCompletionData
                    val isCrewMode = mode == Mode.CREW

                    // FIX: In Crew mode, don't set message.thinkingData at top level
                    // The per-step thinking is stored in completedSteps and rendered correctly
                    // Only set message.thinkingData for non-Crew mode (Fast/Thinking modes)
                    val messageThinkingData = if (isCrewMode) {
                        // In Crew mode, keep existing thinkingData (or null) - don't overwrite with per-step data
                        assistantMsg?.thinkingData
                    } else {
                        thinkingData
                    }

                    val updatedMessages = base.messages.map { msg ->
                        if (msg.id == assistantMessageId) {
                            // Bug #1 fix: For FINAL step, add stepOutput to content
                            val newContent = if (generationState.stepType == PipelineStep.FINAL) {
                                msg.content + generationState.stepOutput
                            } else {
                                msg.content
                            }
                            msg.copy(
                                content = newContent,
                                completedSteps = updatedSteps,
                                thinkingData = messageThinkingData
                            )
                        } else {
                            msg
                        }
                    }

                    base.copy(
                        messages = updatedMessages,
                        // FIX: For FINAL step, set responseState to NONE since all processing is complete
                        // Keep in PROCESSING state for non-FINAL non-thinking steps (Draft One, Draft Two)
                        // Keep in THINKING state for steps with thinking
                        // This ensures the indicator shows while waiting for the next step
                        // FIX: For non-FINAL steps, always show PROCESSING (waiting for next step)
                        // THINKING indicator should only show when next step is actively thinking (ThinkingLive)
                        // This ensures the indicator shows while waiting for the next step
                        responseState = when {
                            generationState.stepType == PipelineStep.FINAL -> ResponseState.NONE
                            else -> ResponseState.PROCESSING
                        },
                        // Don't reset thinkingStartTime here - it will be set when the next ThinkingLive arrives
                        // Setting to 0 indicates no active thinking timer (step just completed)
                        thinkingStartTime = 0L,
                        // Clear thinkingSteps when step completes - the thinking data is now in completedSteps
                        // FIX: Don't clear thinkingSteps in Crew mode - keep them so UI can show "Thought For Xs"
                        // when GeneratingText arrives after StepCompleted
                        // These will be cleared when the next ThinkingLive arrives or when FINISHED
                        thinkingSteps = if (mode == Mode.CREW) base.thinkingSteps else emptyList(),
                        // Store thinking duration so we can show "Thought For" indicator when transitioning to GENERATING
                        thinkingDurationSeconds = generationState.thinkingDurationSeconds,
                        thinkingModelDisplayName = generationState.modelDisplayName,
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
                        thinkingDurationSeconds = 0,
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
                        thinkingDurationSeconds = 0,
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
                        handleGenerationState(generationState, pendingMessageId, Mode.CREW)
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