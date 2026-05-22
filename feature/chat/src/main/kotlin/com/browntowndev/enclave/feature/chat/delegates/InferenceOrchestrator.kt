package com.browntowndev.enclave.feature.chat.delegates

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.FileAttachment
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.ChatModeMapper.toDomain
import com.browntowndev.enclave.feature.chat.ChatModeUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds
import javax.inject.Inject

class InferenceOrchestrator
    @Inject
    constructor(
        private val chatUseCases: ChatUseCases,
        private val settingsUseCases: SettingsUseCases,
        private val cancelInferenceUseCase: CancelInferenceUseCase,
        private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        private val chatRepository: ChatRepository,
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

        private val _optimisticUserMessage = MutableStateFlow<Message?>(null)
        val optimisticUserMessage: StateFlow<Message?> = _optimisticUserMessage.asStateFlow()

        fun clearOptimisticUser() {
            _optimisticUserMessage.value = null
        }

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
                    val assistantMessageId = MessageId(UUID.randomUUID().toString())

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

                    _optimisticUserMessage.value = domainMessage
                    onInputCleared()

                    val userResult =
                        try {
                            chatUseCases.processPrompt(domainMessage)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _optimisticUserMessage.value = null
                            errorHandler.handleError(
                                tag = TAG,
                                message = "Failed to send message",
                                throwable = e,
                                userMessage = "Could not send message. Please try again.",
                            )
                            return@launch
                        }

                    _currentChatId.value = userResult.chatId
                    savedStateHandle["chatId"] = userResult.chatId.value
                    val persistedUserMessage =
                        domainMessage.copy(
                            id = userResult.userMessageId,
                            chatId = userResult.chatId,
                            createdAt = userResult.userMessageCreatedAtMillis,
                        )
                    _optimisticUserMessage.value = persistedUserMessage

                    delay(ChatTurnTiming.ASSISTANT_PLACEHOLDER_DELAY_MS.milliseconds)

                    chatUseCases.createAssistantPlaceholder(
                        chatId = userResult.chatId,
                        userMessageCreatedAtMillis = userResult.userMessageCreatedAtMillis,
                        assistantMessageId = assistantMessageId,
                    )

                    val turnKey =
                        ActiveChatTurnKey(
                            chatId = userResult.chatId,
                            assistantMessageId = assistantMessageId,
                        )
                    _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("sendMessage-unack") { keys ->
                        keys - turnKey
                    }
                    _requestedTurnKey.value = turnKey

                    inferenceJob =
                        chatUseCases
                            .generateChatResponse(
                                prompt = input,
                                userMessageId = userResult.userMessageId,
                                assistantMessageId = assistantMessageId,
                                chatId = userResult.chatId,
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

        fun stopInference(
            scope: CoroutineScope,
            activeTurnKey: ActiveChatTurnKey? = null,
            finalContent: String? = null,
        ) {
            inferenceJob?.cancel()
            cancelInferenceUseCase()
            val key = _requestedTurnKey.value ?: activeTurnKey
            key?.let {
                scope.launch(
                    errorHandler.coroutineExceptionHandler(
                        TAG,
                        "Failed to stop inference",
                        "Could not stop the response. Please try again.",
                    ),
                ) {
                    if (!finalContent.isNullOrBlank()) {
                        chatRepository.updateMessageContent(it.assistantMessageId, finalContent)
                    }
                    chatRepository.updateMessageState(it.assistantMessageId, MessageState.COMPLETE)
                    activeChatTurnSnapshotPort.clear(it)
                    _requestedTurnKey.update { current -> if (current == it) null else current }
                    _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("stopInference") { keys -> keys + it }
                }
            }
        }

        fun acknowledgeTurn(key: ActiveChatTurnKey) {
            _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("acknowledgeTurn") { keys -> keys + key }
            if (_requestedTurnKey.value == key) {
                _requestedTurnKey.update { null }
            }
        }

        /**
         * Align orchestrator session with navigation when user opens an existing chat.
         * Does not write [SavedStateHandle] — Navigation owns `chatId` there; [createNewChat] clears it.
         * Intentional no-op for null so [ChatRoute] cannot wipe nav args before they are applied.
         */
        fun syncActiveChatFromNavigation(navChatId: ChatId?) {
            if (navChatId == null) return
            if (_currentChatId.value == navChatId) return
            _currentChatId.value = navChatId
            pruneAcknowledgedTurnKeysToActiveChat(navChatId)
            val requestedKey = _requestedTurnKey.value
            if (requestedKey != null && requestedKey.chatId != navChatId) {
                _requestedTurnKey.value = null
            }
        }

        private fun pruneAcknowledgedTurnKeysToActiveChat(activeChatId: ChatId?) {
            _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("syncActiveChatFromNavigation") { keys ->
                when (activeChatId) {
                    null -> emptySet()
                    else -> keys.filter { key -> key.chatId == activeChatId }.toSet()
                }
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
