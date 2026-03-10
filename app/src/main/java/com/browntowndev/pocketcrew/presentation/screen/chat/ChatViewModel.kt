package com.browntowndev.pocketcrew.presentation.screen.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val selectedMode: Mode = Mode.AUTO,
    val isInputExpanded: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingSteps: List<String> = emptyList(),
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val settingsUseCases: SettingsUseCases,
    private val chatUseCases: ChatUseCases,
) : ViewModel() {

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
        val currentState = uiState.value
        if (currentState.inputText.isNotBlank()) {
            val userMessageContent = currentState.inputText
            
            // Generate unique IDs using timestamp + counter to prevent duplicate keys
            val timestamp = System.currentTimeMillis()
            val userMessageId = "${timestamp}_user"
            val assistantMessageId = "${timestamp}_assistant"

            val userMessage = ChatMessage(
                id = userMessageId,
                role = MessageRole.User,
                content = userMessageContent,
                formattedTimestamp = "Now",
            )
            
            // Placeholder assistant message that will be updated with streaming content
            val placeholderAssistantMessage = ChatMessage(
                id = assistantMessageId,
                role = MessageRole.Assistant,
                content = "",
                formattedTimestamp = "Now",
            )
            
            _baseState.update {
                it.copy(
                    messages = it.messages + userMessage + placeholderAssistantMessage,
                    inputText = "",
                    isThinking = true,
                    thinkingSteps = emptyList(),
                )
            }

            // Create domain message for persistence
            val domainMessage = Message(
                id = -1L, // Signal to create new chat if needed
                chatId = -1L,
                content = userMessageContent,
                role = com.browntowndev.pocketcrew.domain.model.chat.Role.USER
            )

            viewModelScope.launch {
                // Step 1: Save user message (creates new chat if needed)
                chatUseCases.processPrompt(domainMessage)

                // Step 2: Generate streaming response
                chatUseCases.generateChatResponse(userMessageContent, assistantMessageId)
                    .collect { generationState ->
                        when (generationState) {
                            is MessageGenerationState.ThinkingLive -> {
                                _baseState.update {
                                    it.copy(thinkingSteps = generationState.steps)
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
                                _baseState.update { base ->
                                    val assistantMsg = base.messages.find { it.id == assistantMessageId }
                                    if (assistantMsg?.content.isNullOrEmpty()) {
                                        // No content generated - remove orphan placeholder
                                        base.copy(
                                            messages = base.messages.filter { it.id != assistantMessageId },
                                            isThinking = false,
                                            thinkingSteps = emptyList(),
                                        )
                                    } else {
                                        base.copy(
                                            isThinking = false,
                                            thinkingSteps = emptyList(),
                                        )
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
