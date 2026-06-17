package com.browntowndev.enclave.feature.chat.delegates

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatInferenceErrorCategory
import com.browntowndev.enclave.domain.model.chat.ChatInferenceUserErrorMapper
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatResponseMode
import com.browntowndev.enclave.domain.model.chat.Content
import com.browntowndev.enclave.domain.model.chat.FileAttachment
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.PipelineExecutorPort
import com.browntowndev.enclave.domain.port.repository.ChatRepository
import com.browntowndev.enclave.domain.usecase.chat.ChatTurnTiming
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.SubmitPromptRequest
import com.browntowndev.enclave.domain.qualifier.ApplicationScope
import com.browntowndev.enclave.domain.usecase.inference.CancelInferenceUseCase
import com.browntowndev.enclave.domain.usecase.inference.ModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.BuildConfig
import com.browntowndev.enclave.feature.chat.ChatModeUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

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
        private val pipelineExecutor: PipelineExecutorPort,
        private val moderatePromptUseCase: ModeratePromptUseCase,
        private val loggingPort: LoggingPort,
        @param:ApplicationScope private val applicationScope: CoroutineScope,
    ) {
        private val _currentChatId = MutableStateFlow<ChatId?>(null)
        val currentChatId: StateFlow<ChatId?> = _currentChatId.asStateFlow()

        private val _requestedTurnKey = MutableStateFlow<ActiveChatTurnKey?>(null)
        val requestedTurnKey: StateFlow<ActiveChatTurnKey?> = _requestedTurnKey.asStateFlow()

        private val _acknowledgedTurnKeys = MutableStateFlow<Set<ActiveChatTurnKey>>(emptySet())
        val acknowledgedTurnKeys: StateFlow<Set<ActiveChatTurnKey>> = _acknowledgedTurnKeys.asStateFlow()

        private var inferenceJob: Job? = null
        private var sendJob: Job? = null

        private val _optimisticUserMessage = MutableStateFlow<Message?>(null)
        val optimisticUserMessage: StateFlow<Message?> = _optimisticUserMessage.asStateFlow()

        private val _promptTooLargeFailures = Channel<PromptTooLargeFailure>(Channel.BUFFERED)
        val promptTooLargeFailures: Flow<PromptTooLargeFailure> = _promptTooLargeFailures.receiveAsFlow()

        fun clearOptimisticUser() {
            _optimisticUserMessage.value = null
        }

        fun sendMessage(
            scope: CoroutineScope,
            input: String,
            responseMode: ChatResponseMode,
            selectedImageUris: List<String>,
            fileAttachments: List<FileAttachment>,
            initialChatId: ChatId?,
            shouldModerateResponse: Boolean,
            onInputCleared: () -> Unit,
            onResponseBlocked: () -> Unit,
        ) {
            cancelActiveInferenceJob()
            cancelInferenceUseCase()

            sendJob?.cancel()
            sendJob =
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
                            chatUseCases.submitPrompt(
                                SubmitPromptRequest(
                                    message = domainMessage,
                                    assistantMessageId = assistantMessageId,
                                ),
                            )
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
                        applicationScope.launch(
                            errorHandler.coroutineExceptionHandler(
                                TAG,
                                "Inference failed",
                                "Failed to generate response. Please try again.",
                            ),
                        ) {
                            var compactionFailureReported = false
                            var finalResponseText: String? = null
                            chatUseCases
                                .generateChatResponse(
                                    prompt = input,
                                    userMessageId = userResult.userMessageId,
                                    assistantMessageId = assistantMessageId,
                                    chatId = userResult.chatId,
                                    responseMode = responseMode,
                                    backgroundInferenceEnabled = settings.backgroundInferenceEnabled,
                                ).catch { cause ->
                                    val userMessage =
                                        if (cause is ActiveModelConfigurationDecryptException) {
                                            cause.message
                                                ?: ActiveModelConfigurationDecryptException.USER_MESSAGE
                                        } else {
                                            "Failed to generate response. Please try again."
                                        }
                                    errorHandler.handleError(TAG, "Inference failed", cause, userMessage)
                                }.collect { accumulatedMessages ->
                                    if (!compactionFailureReported && accumulatedMessages.hasCompactionFailure()) {
                                        compactionFailureReported = true
                                        _promptTooLargeFailures.send(PromptTooLargeFailure(prompt = input))
                                    }
                                    finalResponseText =
                                        accumulatedMessages.messages.values
                                            .firstOrNull { it.isComplete }?.content
                                }

                            // When background inference is enabled, the flow returns immediately
                            // after starting the service. The actual response is published via
                            // the snapshot port — observe it for the completed message so
                            // post-turn moderation can run.
                            if (finalResponseText == null && settings.backgroundInferenceEnabled) {
                                finalResponseText =
                                    activeChatTurnSnapshotPort.observe(turnKey)
                                        .firstOrNull { snapshot ->
                                            snapshot?.messages?.values
                                                ?.any { it.isComplete } == true
                                        }?.messages?.values
                                        ?.firstOrNull { it.isComplete }?.content
                            }

                            // Post-turn response moderation (playStore flavor only)
                            val responseLength = finalResponseText?.length
                            loggingPort.debug(
                                TAG,
                                "Post-turn moderation gate: shouldModerateResponse=$shouldModerateResponse, " +
                                    "finalResponseText length=$responseLength",
                            )
                            if (shouldModerateResponse && finalResponseText != null) {
                                val moderationResult = moderatePromptUseCase(finalResponseText!!)
                                if (moderationResult.blocked) {
                                    chatRepository.updateMessageContent(assistantMessageId, BLOCKED_RESPONSE_PLACEHOLDER)
                                    activeChatTurnSnapshotPort.clear(turnKey)
                                    onResponseBlocked()
                                }
                            }
                        }
                }
            }
        }

        fun stopInference(
            scope: CoroutineScope,
            activeTurnKey: ActiveChatTurnKey? = null,
            finalContent: String? = null,
        ) {
            cancelActiveInferenceJob()
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
        fun adoptChatId(chatId: ChatId) {
            _currentChatId.value = chatId
            savedStateHandle["chatId"] = chatId.value
        }

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
            cancelActiveInferenceJob()
            cancelInferenceUseCase()
            _requestedTurnKey.value?.let { key ->
                _acknowledgedTurnKeys.updateAcknowledgedTurnKeys("createNewChat") { keys -> keys + key }
            }
            _currentChatId.value = null
            _requestedTurnKey.value = null
            savedStateHandle["chatId"] = null
        }

        fun cancelInference(scope: CoroutineScope) {
            cancelActiveInferenceJob()
            cancelInferenceUseCase()
            _currentChatId.value?.let { chatId ->
                scope.launch {
                    pipelineExecutor.stopPipeline(chatId.value)
                }
            }
        }

        /**
         * Detach UI/session wiring when [ChatViewModel] is cleared (e.g. navigation away).
         * In-flight generation continues on [applicationScope]; [ActiveChatTurnSnapshotPort]
         * holds live state for reattachment when the user returns.
         */
        fun detachOnViewModelCleared() {
            sendJob?.cancel()
            sendJob = null
        }

        private fun cancelActiveInferenceJob() {
            sendJob?.cancel()
            sendJob = null
            inferenceJob?.cancel()
            inferenceJob = null
        }

        private inline fun MutableStateFlow<Set<ActiveChatTurnKey>>.updateAcknowledgedTurnKeys(
            via: String,
            crossinline transform: (Set<ActiveChatTurnKey>) -> Set<ActiveChatTurnKey>,
        ) {
            update { keys ->
                val newKeys = transform(keys)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "InfOrch",
                        "ackUpdate from=${keys.map { it.assistantMessageId.value.take(4) }} " +
                            "to=${newKeys.map { it.assistantMessageId.value.take(4) }} via=$via",
                    )
                }
                newKeys
            }
        }

        private companion object {
            private const val TAG = "InferenceOrchestrator"
            private const val BLOCKED_RESPONSE_PLACEHOLDER =
                "This response was removed due to content policy."
        }
    }

data class PromptTooLargeFailure(
    val prompt: String,
)

private fun AccumulatedMessages.hasCompactionFailure(): Boolean =
    messages.values.any { snapshot ->
        snapshot.isComplete &&
            (
                snapshot.failureErrorCategory == ChatInferenceErrorCategory.PROMPT_TOO_LARGE ||
                    ChatInferenceUserErrorMapper.isPromptTooLarge(snapshot.content)
            )
    }
