package com.browntowndev.pocketcrew.feature.chat.delegates

import androidx.lifecycle.SavedStateHandle
import android.util.Log
import com.browntowndev.pocketcrew.core.ui.error.ViewModelErrorHandler
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.FileAttachment
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.pocketcrew.domain.usecase.chat.ChatUseCases
import com.browntowndev.pocketcrew.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.pocketcrew.domain.usecase.settings.SettingsUseCases
import com.browntowndev.pocketcrew.feature.chat.ChatModeMapper.toDomain
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

class InferenceOrchestrator
    @Inject
    constructor(
        private val chatUseCases: ChatUseCases,
        private val settingsUseCases: SettingsUseCases,
        private val cancelInferenceUseCase: CancelInferenceUseCase,
        private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        private val errorHandler: ViewModelErrorHandler,
        private val savedStateHandle: SavedStateHandle,
    ) {
        private val _currentChatId = MutableStateFlow<ChatId?>(null)
        val currentChatId: StateFlow<ChatId?> = _currentChatId.asStateFlow()

        private val _requestedTurnKey = MutableStateFlow<ActiveChatTurnKey?>(null)
        val requestedTurnKey: StateFlow<ActiveChatTurnKey?> = _requestedTurnKey.asStateFlow()

        private val _acknowledgedTurnKeys = MutableStateFlow<Set<ActiveChatTurnKey>>(emptySet())
        val acknowledgedTurnKeys: StateFlow<Set<ActiveChatTurnKey>> = _acknowledgedTurnKeys.asStateFlow()

        private var inferenceJob: Job? = null

        fun sendMessage(
            scope: CoroutineScope,
            input: String,
            selectedMode: ChatModeUi,
            selectedImageUris: List<String>,
            fileAttachments: List<FileAttachment>,
            initialChatId: ChatId?,
            onInputCleared: () -> Unit,
        ) {
            inferenceJob?.cancel()

            scope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to send message",
                    "Could not send message. Please try again.",
                ),
            ) {
                val settings = settingsUseCases.getSettings().first()

                if (input.isNotBlank() || selectedImageUris.isNotEmpty() || fileAttachments.isNotEmpty()) {
                    val chatIdForMessage: ChatId? = _currentChatId.value ?: initialChatId

                    val domainMessage =
                        Message(
                            id = MessageId(UUID.randomUUID().toString()),
                            chatId = chatIdForMessage,
                            content =
                                Content(
                                    text = input,
                                    imageUris = selectedImageUris,
                                    fileAttachments = fileAttachments,
                                ),
                            role = Role.USER,
                            messageState = MessageState.COMPLETE,
                            createdAt = System.currentTimeMillis(),
                        )

                    val promptResult = chatUseCases.processPrompt(domainMessage)

                    _currentChatId.value = promptResult.chatId
                    savedStateHandle["chatId"] = promptResult.chatId.value
                    val turnKey =
                        ActiveChatTurnKey(
                            chatId = promptResult.chatId,
                            assistantMessageId = promptResult.assistantMessageId,
                        )
                    _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("sendMessage-unack") { keys -> keys - turnKey }
                    _requestedTurnKey.value = turnKey

                    onInputCleared()

                    inferenceJob =
                        chatUseCases
                            .generateChatResponse(
                                prompt = input,
                                userMessageId = promptResult.userMessageId,
                                assistantMessageId = promptResult.assistantMessageId,
                                chatId = promptResult.chatId,
                                mode = selectedMode.toDomain(),
                                backgroundInferenceEnabled = settings.backgroundInferenceEnabled,
                            ).onEach {
                                // Snapshot publication is owned by the generation pipeline
                            }.catch { cause ->
                                val userMessage =
                                    if (cause is ActiveModelConfigurationDecryptException) {
                                        cause.message ?: ActiveModelConfigurationDecryptException.USER_MESSAGE
                                    } else {
                                        "Failed to generate response. Please try again."
                                    }
                                errorHandler.handleError(TAG, "Inference failed", cause, userMessage)
                            }.launchIn(scope)
                }
            }
        }

        fun stopInference(scope: CoroutineScope) {
            inferenceJob?.cancel()
            cancelInferenceUseCase()
            _requestedTurnKey.value?.let { key ->
                scope.launch {
                    activeChatTurnSnapshotPort.clear(key)
                    _requestedTurnKey.update { current -> if (current == key) null else current }
                    _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("stopInference") { keys -> keys + key }
                }
            }
        }

        fun acknowledgeTurn(key: ActiveChatTurnKey) {
            _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("acknowledgeTurn") { keys -> keys + key }
            if (_requestedTurnKey.value == key) {
                _requestedTurnKey.update { null }
            }
        }

        fun createNewChat() {
            inferenceJob?.cancel()
            _requestedTurnKey.value?.let { key ->
                _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("createNewChat") { keys -> keys + key }
            }
            _currentChatId.value = null
            _requestedTurnKey.value = null
            savedStateHandle["chatId"] = null
        }

        fun cancelInference() {
            inferenceJob?.cancel()
        }

        private inline fun MutableStateFlow<Set<ActiveChatTurnKey>>.updateAcknowledgedTurnKeys(
            via: String,
            crossinline transform: (Set<ActiveChatTurnKey>) -> Set<ActiveChatTurnKey>,
        ) {
            update { keys ->
                val newKeys = transform(keys)
                Log.d(
                    "InfOrch",
                    "ackUpdate from=${keys.map { it.assistantMessageId.value.take(4) }} " +
                        "to=${newKeys.map { it.assistantMessageId.value.take(4) }} via=$via",
                )
                newKeys
            }
        }

        private companion object {
            private const val TAG = "InferenceOrchestrator"
        }
    }
