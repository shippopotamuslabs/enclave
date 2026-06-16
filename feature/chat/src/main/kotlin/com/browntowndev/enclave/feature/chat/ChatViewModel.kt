package com.browntowndev.enclave.feature.chat

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUiMapper
import com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUiMapper
import com.browntowndev.enclave.core.ui.error.ViewModelErrorHandler
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.FileAttachment
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnSnapshotPort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioRepositoryPort
import com.browntowndev.enclave.domain.port.repository.UiSelectionRepository
import com.browntowndev.enclave.domain.usecase.chat.ChatUseCases
import com.browntowndev.enclave.domain.usecase.chat.GetModelDisplayNameUseCase
import com.browntowndev.enclave.domain.usecase.chat.MergeDbMessagesWithOptimisticUseCase
import com.browntowndev.enclave.domain.usecase.inference.InferenceLockManager
import com.browntowndev.enclave.domain.usecase.inference.ShouldModeratePromptUseCase
import com.browntowndev.enclave.domain.usecase.inference.SubmitModerationReportUseCase
import com.browntowndev.enclave.domain.usecase.settings.SettingsUseCases
import com.browntowndev.enclave.feature.chat.BuildConfig
import com.browntowndev.enclave.feature.chat.delegates.ArtifactExportCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatShareCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatTelemetryCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ChatUiStateReducer
import com.browntowndev.enclave.feature.chat.delegates.InferenceOrchestrator
import com.browntowndev.enclave.feature.chat.delegates.LiveVoiceCoordinator
import com.browntowndev.enclave.feature.chat.delegates.SpeechInputCoordinator
import com.browntowndev.enclave.feature.chat.delegates.ToolExecutionBannerCoordinator
import com.browntowndev.enclave.feature.chat.delegates.TtsController
import com.browntowndev.enclave.feature.chat.delegates.maybeAcknowledgeCompletedTurn
import com.browntowndev.enclave.feature.chat.delegates.resolveActiveTurnSnapshotObserveKey
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicyResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Chat screen.
 * Wires domain ports and feature delegates into a single [ChatUiState] flow.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class ChatViewModel
    @Inject
    constructor(
        private val settingsUseCases: SettingsUseCases,
        private val chatUseCases: ChatUseCases,
        private val savedStateHandle: SavedStateHandle,
        inferenceLockManager: InferenceLockManager,
        private val modelDisplayNamesUseCase: GetModelDisplayNameUseCase,
        private val activeChatTurnSnapshotPort: ActiveChatTurnSnapshotPort,
        private val errorHandler: ViewModelErrorHandler,
        private val loggingPort: LoggingPort,
        private val studioRepository: StudioRepositoryPort,
        private val shouldModeratePromptUseCase: ShouldModeratePromptUseCase,
        private val submitModerationReportUseCase: SubmitModerationReportUseCase,
        private val inferenceOrchestrator: InferenceOrchestrator,
        private val chatInputCoordinator: ChatInputCoordinator,
        private val speechInputCoordinator: SpeechInputCoordinator,
        private val ttsController: TtsController,
        private val toolExecutionBannerCoordinator: ToolExecutionBannerCoordinator,
        private val artifactExportCoordinator: ArtifactExportCoordinator,
        private val chatShareCoordinator: ChatShareCoordinator,
        private val chatTelemetryCoordinator: ChatTelemetryCoordinator,
        private val chatUiStateReducer: ChatUiStateReducer,
        private val chatMessageUiMapper: ChatMessageUiMapper,
        private val photoAttachmentPolicyResolver: PhotoAttachmentPolicyResolver,
        private val liveVoiceCoordinator: LiveVoiceCoordinator,
        private val uiSelectionRepository: UiSelectionRepository,
        private val localModelAssetUiMapper: LocalModelAssetUiMapper,
        private val apiModelAssetUiMapper: ApiModelAssetUiMapper,
    ) : ViewModel() {
        private val mergeDbMessagesWithOptimisticUseCase = MergeDbMessagesWithOptimisticUseCase()

        val initialChatId: ChatId?
            get() = savedStateHandle.get<String>("chatId")?.let { ChatId(it) }

        val speechState: StateFlow<SpeechState> = speechInputCoordinator.speechState
        val artifactExportMessages = artifactExportCoordinator.artifactExportMessages
        val shareUiEvents = chatShareCoordinator.shareUiEvents
        private val _moderationMessages = Channel<Int>(Channel.BUFFERED)
        val moderationMessages: Flow<Int> = _moderationMessages.receiveAsFlow()
        private val _chatErrorMessages = Channel<Int>(Channel.BUFFERED)
        val chatErrorMessages: Flow<Int> = _chatErrorMessages.receiveAsFlow()

        private val modelDisplayNamesFlow =
            flow {
                emit(
                    ModelType.entries.associateWith { type ->
                        runCatching { modelDisplayNamesUseCase(type) }
                            .getOrElse { ActiveModelConfigurationDecryptException.USER_MESSAGE }
                    },
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptyMap(),
            )

        private val _isModelOverrideSheetVisible = MutableStateFlow(false)

        private val modelOverrideSheetAssetsFlow =
            chatUseCases.getChatCapablePresetsUseCase()
                .map { presets ->
                    ModelOverrideSheetAssets(
                        localAssets = presets.localAssets.map(localModelAssetUiMapper::map),
                        apiAssets = presets.apiAssets.map(apiModelAssetUiMapper::map),
                    )
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = ModelOverrideSheetAssets(),
                )

        private val photoAttachmentPolicyFlow =
            combine(
                settingsUseCases.getSettings(),
                chatInputCoordinator.selectedMode,
                chatInputCoordinator.selectedModelOverride,
            ) { _, selectedMode, modelOverride ->
                selectedMode to modelOverride
            }.mapLatest { (selectedMode, modelOverride) ->
                photoAttachmentPolicyResolver.resolve(selectedMode, modelOverride)
            }

        private val hasTtsProviderAssignedFlow =
            settingsUseCases.assignments
                .getDefaultModels()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.TTS && assignment.ttsProviderId != null
                    }
                }.distinctUntilChanged()

        private val hasLiveVoiceProviderAssignedFlow =
            settingsUseCases.assignments
                .getDefaultModels()
                .map { assignments ->
                    assignments.any { assignment ->
                        assignment.modelType == ModelType.LIVE_VOICE &&
                            assignment.liveVoiceProviderId != null
                    }
                }.distinctUntilChanged()

        private val navigationChatIdFlow =
            savedStateHandle
                .getStateFlow<String?>(SAVED_STATE_CHAT_ID_KEY, null)
                .map { chatIdValue -> chatIdValue?.let { ChatId(it) } }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = initialChatId,
                )

        private val activeChatIdFlow =
            combine(
                navigationChatIdFlow,
                inferenceOrchestrator.currentChatId,
            ) { navigationChatId, sessionChatId ->
                navigationChatId ?: sessionChatId
            }.distinctUntilChanged()

        private val dbMessagesFlow =
            activeChatIdFlow
                .flatMapLatest { id: ChatId? ->
                    if (id == null) {
                        flowOf(emptyList())
                    } else {
                        chatUseCases.getChat(id)
                    }
                }.distinctUntilChangedBy { messages -> messages.dbMessagesFingerprint() }
                .shareIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    replay = 1,
                )

        private val mergedDbMessagesFlow =
            combine(
                dbMessagesFlow,
                inferenceOrchestrator.optimisticUserMessage,
            ) { dbMessages, optimisticUserMessage ->
                when {
                    optimisticUserMessage == null -> dbMessages
                    dbMessages.any { it.id == optimisticUserMessage.id } -> dbMessages
                    else ->
                        mergeDbMessagesWithOptimisticUseCase(
                            dbMessages = dbMessages,
                            optimisticUserMessage = optimisticUserMessage,
                        )
                }
            }.distinctUntilChangedBy { messages -> messages.dbMessagesFingerprint() }
                .shareIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    replay = 1,
                )

        private val activeTurnCandidateFlow =
            combine(
                activeChatIdFlow,
                mergedDbMessagesFlow,
                inferenceOrchestrator.requestedTurnKey,
            ) { chatId, messages, requestedTurnKey ->
                val incompleteKey =
                    chatId?.let { id ->
                        messages
                            .lastOrNull { message ->
                                message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
                            }?.let { message -> ActiveChatTurnKey(id, message.id) }
                    }
                val requestedKey =
                    requestedTurnKey?.takeIf { key ->
                        key.chatId == chatId
                    }
                ActiveTurnCandidate(
                    chatId = chatId,
                    messages = messages,
                    key = requestedKey ?: incompleteKey,
                )
            }

        private val activeTurnKeyFlow =
            combine(
                activeTurnCandidateFlow,
                inferenceOrchestrator.acknowledgedTurnKeys,
            ) { candidate, acknowledgedKeys ->
                candidate to acknowledgedKeys
            }.scan(null as ActiveChatTurnKey?) { currentKey, (candidate, acknowledgedKeys) ->
                val candidateKey = candidate.key?.takeUnless { key -> key in acknowledgedKeys }
                when {
                    currentKey != null && currentKey in acknowledgedKeys -> null
                    candidateKey != null -> candidateKey
                    currentKey != null &&
                        candidate.chatId == currentKey.chatId &&
                        candidate.messages.any { message ->
                            message.id == currentKey.assistantMessageId &&
                                message.messageState == MessageState.COMPLETE
                        } -> currentKey
                    else -> null
                }
            }.distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    initialValue = null,
                )

        private val activeTurnSnapshotObserveKeyFlow =
            combine(
                mergedDbMessagesFlow,
                activeChatIdFlow,
                inferenceOrchestrator.requestedTurnKey,
                inferenceOrchestrator.acknowledgedTurnKeys,
                activeTurnKeyFlow,
            ) { messages, chatId, requestedTurnKey, acknowledgedKeys, retainedKey ->
                val incompleteKey =
                    chatId?.let { id ->
                        messages
                            .lastOrNull { message ->
                                message.role == Role.ASSISTANT && message.messageState != MessageState.COMPLETE
                            }?.let { message -> ActiveChatTurnKey(id, message.id) }
                    }
                val requestedKey =
                    requestedTurnKey?.takeIf { key ->
                        key.chatId == chatId
                    }
                val resultKey =
                    resolveActiveTurnSnapshotObserveKey(
                        requestedKey = requestedKey,
                        incompleteKey = incompleteKey,
                        acknowledgedKeys = acknowledgedKeys,
                        retainedKey = retainedKey,
                    )
                val incompleteAssistantId =
                    messages
                        .lastOrNull { message ->
                            message.role == Role.ASSISTANT &&
                                message.messageState != MessageState.COMPLETE
                        }?.id
                        ?.value
                        ?.take(4)
                if (BuildConfig.DEBUG) {
                    Log.d(
                        "ObserveKeyCompute",
                        "chatId=${chatId?.value} dbMsgs=${messages.size}/$incompleteAssistantId " +
                            "req=${requestedTurnKey?.assistantMessageId?.value?.take(4)}" +
                            "@${requestedTurnKey?.chatId?.value} " +
                            "ack=${acknowledgedKeys.map { it.assistantMessageId.value.take(4) }} " +
                            "retained=${retainedKey?.assistantMessageId?.value?.take(4)} " +
                            "result=${resultKey?.assistantMessageId?.value?.take(4)}",
                    )
                }
                resultKey
            }.distinctUntilChanged()
                .shareIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    replay = 1,
                )

        private val _activeTurnSnapshotState =
            MutableStateFlow(
                ActiveTurnSnapshotState(
                    key = null,
                    snapshot = null,
                ),
            )
        private val activeTurnSnapshotStateFlow = _activeTurnSnapshotState.asStateFlow()

        private val uiInputsFlow =
            combine(
                settingsUseCases.getSettings(),
                chatInputCoordinator.inputText,
                chatInputCoordinator.selectedMode,
                chatInputCoordinator.selectedModelOverride,
                chatInputCoordinator.selectedImageUris,
                chatInputCoordinator.selectedFileName,
                speechInputCoordinator.speechState,
            ) { args: Array<Any?> ->
                UiInputs(
                    settings = args[0] as SettingsData,
                    inputText = args[1] as String,
                    selectedMode = args[2] as ChatModeUi,
                    selectedModelOverride = args[3] as ChatModelOverride?,
                    selectedImageUris = args[4] as List<String>,
                    selectedFileName = args[5] as String?,
                    speechState = args[6] as SpeechState,
                )
            }

        private val uiInputsWithPolicyFlow =
            combine(
                uiInputsFlow,
                photoAttachmentPolicyFlow,
            ) { inputs, attachmentPolicy ->
                UiInputsWithPolicy(inputs, attachmentPolicy)
            }

        private val ttsPlaybackFlow =
            combine(
                toolExecutionBannerCoordinator.activeToolCallBanner,
                hasTtsProviderAssignedFlow,
                ttsController.isPlayingTts,
                ttsController.isLoadingTts,
                ttsController.isPausedTts,
            ) { activeToolCallBanner, hasTtsProviderAssigned, isPlayingTts, isLoadingTts, isPausedTts ->
                TtsPlaybackState(
                    activeToolCallBanner = activeToolCallBanner,
                    hasTtsProviderAssigned = hasTtsProviderAssigned,
                    isPlayingTts = isPlayingTts,
                    isLoadingTts = isLoadingTts,
                    isPausedTts = isPausedTts,
                )
            }

        private val studioMediaFlow =
            combine(
                studioRepository.observeEphemeralMedia(),
                studioRepository.observeAllMedia(),
            ) { ephemeral, persisted ->
                (ephemeral + persisted).associateBy { it.id }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS), emptyMap())

        private val chatUiCombineInputsFlow =
            combine(
                uiInputsWithPolicyFlow,
                inferenceLockManager.isInferenceBlocked,
                mergedDbMessagesFlow,
                activeTurnSnapshotStateFlow,
                ttsPlaybackFlow,
                studioMediaFlow,
                chatShareCoordinator.shareState,
                chatShareCoordinator.isReadOnly,
                chatShareCoordinator.sharedMessages,
                artifactExportCoordinator.artifactExportSheet,
                modelDisplayNamesFlow,
                activeChatIdFlow,
                hasLiveVoiceProviderAssignedFlow,
                liveVoiceCoordinator.state,
            ) { args ->
                ChatUiCombineInputs(
                    inputsWithPolicy = args[0] as UiInputsWithPolicy,
                    isBlocked = args[1] as Boolean,
                    dbMessages = args[2] as List<Message>,
                    activeTurnSnapshotState = args[3] as ActiveTurnSnapshotState,
                    ttsPlayback = args[4] as TtsPlaybackState,
                    studioMediaMap = args[5] as Map<String, com.browntowndev.enclave.domain.port.repository.StudioMediaAsset>,
                    shareState = args[6] as ShareState,
                    isReadOnly = args[7] as Boolean,
                    sharedMessages = args[8] as List<Message>,
                    artifactExportSheet = args[9] as ArtifactExportSheetState,
                    modelDisplayNames = args[10] as Map<ModelType, String>,
                    chatId = args[11] as ChatId?,
                    hasLiveVoiceProviderAssigned = args[12] as Boolean,
                    liveVoiceState = args[13] as LiveVoiceUiState,
                )
            }

        private val reduceResultFlow =
            chatUiCombineInputsFlow
                .scan(ReduceAccumulator()) { accumulator, inputs ->
                    val reduceResult =
                        chatUiStateReducer.reduce(
                            buildReduceParams(
                                inputs = inputs,
                                hapticState = accumulator.hapticState,
                            ),
                        )
                    ReduceAccumulator(
                        hapticState = reduceResult.hapticState,
                        reduceResult = reduceResult,
                    )
                }.mapNotNull { accumulator -> accumulator.reduceResult }
                .shareIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                    replay = 1,
                )

        val uiState: StateFlow<ChatUiState> =
            combine(
                reduceResultFlow,
                chatTelemetryCoordinator.responseStatsSheet,
                chatTelemetryCoordinator.chatStatsSheet,
                _isModelOverrideSheetVisible,
                modelOverrideSheetAssetsFlow,
            ) { reduceResult, responseStatsSheet, chatStatsSheet, isSheetVisible, sheetAssets ->
                reduceResult.uiState.copy(
                    responseStatsSheet = responseStatsSheet,
                    chatStatsSheet = chatStatsSheet,
                    isModelOverrideSheetVisible = isSheetVisible,
                    modelOverrideSheetLocalAssets = sheetAssets.localAssets,
                    modelOverrideSheetApiAssets = sheetAssets.apiAssets,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIBE_TIMEOUT_MS),
                initialValue = ChatUiState(),
            )

        init {
            liveVoiceCoordinator.onChatIdPersisted = { chatId ->
                inferenceOrchestrator.adoptChatId(chatId)
            }

            viewModelScope.launch {
                val persistedMode = uiSelectionRepository.selectedChatMode.first()
                chatInputCoordinator.onModeChange(ChatModeMapper.run { persistedMode.toUi() })
            }

            viewModelScope.launch {
                activeChatIdFlow.collect { chatId ->
                    chatShareCoordinator.onActiveChatChanged(chatId)
                }
            }

            viewModelScope.launch {
                inferenceOrchestrator.promptTooLargeFailures.collect { failure ->
                    chatInputCoordinator.onInputChange(failure.prompt)
                    _chatErrorMessages.send(R.string.chat_prompt_too_large)
                }
            }

            viewModelScope.launch {
                combine(
                    dbMessagesFlow,
                    inferenceOrchestrator.optimisticUserMessage,
                ) { dbMessages, optimisticUserMessage ->
                    optimisticUserMessage != null &&
                        dbMessages.any { message -> message.id == optimisticUserMessage.id }
                }.distinctUntilChanged()
                    .collect { optimisticConfirmedInDb ->
                        if (optimisticConfirmedInDb) {
                            inferenceOrchestrator.clearOptimisticUser()
                        }
                    }
            }

            viewModelScope.launch {
                combine(
                    reduceResultFlow,
                    mergedDbMessagesFlow,
                    activeTurnSnapshotStateFlow,
                ) { reduceResult, dbMessages, snapshotState ->
                    Triple(reduceResult, dbMessages, snapshotState)
                }.collect { (reduceResult, dbMessages, snapshotState) ->
                    reduceResult.acknowledgedTurnKey?.let { key ->
                        maybeAcknowledgeCompletedTurn(
                            key = key,
                            dbMessages = dbMessages,
                            activeSnapshot = snapshotState.snapshot,
                            acknowledgedTurnKeys = inferenceOrchestrator.acknowledgedTurnKeys.value,
                            onAcknowledge = inferenceOrchestrator::acknowledgeTurn,
                        )
                    }
                }
            }

            viewModelScope.launch {
                activeTurnSnapshotObserveKeyFlow.collectLatest { key ->
                    if (key == null) {
                        if (BuildConfig.DEBUG) {
                            Log.d(
                                "ChatVM",
                                "snapshotFlow key=null snapshot=null",
                            )
                        }
                        _activeTurnSnapshotState.value =
                            ActiveTurnSnapshotState(
                                key = null,
                                snapshot = null,
                            )
                    } else {
                        _activeTurnSnapshotState.value =
                            ActiveTurnSnapshotState(
                                key = key,
                                snapshot = null,
                            )
                        activeChatTurnSnapshotPort
                            .observe(key)
                            .conflate()
                            .distinctUntilChanged()
                            .collect { snapshot ->
                                if (BuildConfig.DEBUG) {
                                    Log.d(
                                        "ChatVM",
                                        "snapshotFlow key=${key.assistantMessageId.value} " +
                                            "snapshot=${snapshot?.messages?.values?.firstOrNull()?.messageState} " +
                                            "segs=${snapshot?.messages?.values?.firstOrNull()?.transcriptSegments?.size}",
                                    )
                                }
                                _activeTurnSnapshotState.value =
                                    ActiveTurnSnapshotState(
                                        key = key,
                                        snapshot = snapshot,
                                    )
                            }
                    }
                }
            }

            speechInputCoordinator.observeResults(viewModelScope) { text ->
                chatInputCoordinator.onInputChange(text)
            }

            toolExecutionBannerCoordinator.start(
                scope = viewModelScope,
                currentChatId = { inferenceOrchestrator.currentChatId.value },
                initialChatId = initialChatId,
                activeTurnKey = { activeTurnKeyFlow.value },
                onExtractingUrl = { url -> markWebSearchSourceExtracted(url) },
            )
        }

        fun onArtifactCardClick(
            messageId: MessageId,
            card: ArtifactCardUiModel,
        ) {
            artifactExportCoordinator.onArtifactCardClick(messageId, card)
        }

        fun dismissArtifactExportSheet() = artifactExportCoordinator.dismissArtifactExportSheet()

        fun onArtifactExportSelectFormat(format: WizardExportFormat) = artifactExportCoordinator.onArtifactExportSelectFormat(format)

        fun onArtifactExportSelectDestination(destination: WizardExportDestination) =
            artifactExportCoordinator.onArtifactExportSelectDestination(destination)

        fun onArtifactExportWizardBack() = artifactExportCoordinator.onArtifactExportWizardBack()

        fun onArtifactExportWizardNext() = artifactExportCoordinator.onArtifactExportWizardNext()

        fun onConfirmArtifactExport() {
            artifactExportCoordinator.onConfirmArtifactExport(
                scope = viewModelScope,
                currentChatId = inferenceOrchestrator.currentChatId.value,
                initialChatId = initialChatId,
            )
        }

        fun onInputChange(inputText: String) = chatInputCoordinator.onInputChange(inputText)

        fun onEditMessage(message: String) = chatInputCoordinator.onInputChange(message)

        fun reportBlockedPrompt(prompt: String) {
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to submit report")) {
                submitModerationReportUseCase(prompt, "blocked")
                _moderationMessages.send(R.string.chat_moderation_feedback_submitted)
            }
        }

        fun reportResponse(messageId: MessageId) {
            val responseText = uiState.value.messages.firstOrNull { it.id == messageId }?.content?.text
            if (responseText.isNullOrBlank()) return
            viewModelScope.launch(errorHandler.coroutineExceptionHandler(TAG, "Failed to submit report")) {
                submitModerationReportUseCase(responseText, "allowed")
                _moderationMessages.send(R.string.chat_moderation_feedback_submitted)
            }
        }

        fun onPlayTts(text: String) {
            ttsController.onPlayTts(
                text = text,
                scope = viewModelScope,
                hasTtsProviderAssigned = uiState.value.hasTtsProviderAssigned,
            )
        }

        fun onStopTts() = ttsController.onStopTts()

        fun onPauseTts() = ttsController.onPauseTts()

        fun onModeChange(mode: ChatModeUi) {
            chatInputCoordinator.onModeChange(mode)
            viewModelScope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to evaluate attachment policy",
                    "Could not update the selected mode.",
                ),
            ) {
                val policy =
                    photoAttachmentPolicyResolver.resolve(
                        mode,
                        chatInputCoordinator.selectedModelOverride.value,
                    )
                if (!policy.isEnabled) {
                    chatInputCoordinator.clearSelectedImages()
                } else if (chatInputCoordinator.selectedImageUris.value.size > policy.maxItems) {
                    chatInputCoordinator.trimSelectedImages(policy.maxItems)
                }
            }
            if (mode != ChatModeUi.OTHER) {
                viewModelScope.launch {
                    uiSelectionRepository.setChatMode(ChatModeMapper.run { mode.toDomain() })
                }
            }
        }

        fun onOtherClick() {
            _isModelOverrideSheetVisible.value = true
        }

        fun onDismissModelOverrideSheet() {
            _isModelOverrideSheetVisible.value = false
        }

        fun onModelOverrideSelected(
            localId: com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId?,
            apiId: com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId?,
            @Suppress("UnusedParameter") ttsId: com.browntowndev.enclave.domain.model.config.TtsProviderId?,
            @Suppress("UnusedParameter") mediaId: com.browntowndev.enclave.domain.model.config.MediaProviderId?,
            @Suppress("UnusedParameter") liveVoiceId: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
        ) {
            val sheetAssets = modelOverrideSheetAssetsFlow.value
            when {
                localId != null -> {
                    val displayName =
                        sheetAssets.localAssets
                            .flatMap { it.configurations }
                            .firstOrNull { it.id == localId }
                            ?.displayName
                            ?: sheetAssets.localAssets
                                .firstOrNull { asset -> asset.configurations.any { it.id == localId } }
                                ?.friendlyName
                            ?: "Local model"
                    chatInputCoordinator.onModelOverrideSelected(
                        ChatModelOverride.Local(
                            configId = localId,
                            displayName = displayName,
                        ),
                    )
                }
                apiId != null -> {
                    val displayName =
                        sheetAssets.apiAssets
                            .flatMap { it.configurations }
                            .firstOrNull { it.id == apiId }
                            ?.let { it.displayName.ifBlank { it.modelId } }
                            ?: sheetAssets.apiAssets
                                .firstOrNull { asset -> asset.configurations.any { it.id == apiId } }
                                ?.displayName
                            ?: "API model"
                    chatInputCoordinator.onModelOverrideSelected(
                        ChatModelOverride.Api(
                            configId = apiId,
                            displayName = displayName,
                        ),
                    )
                }
            }
            _isModelOverrideSheetVisible.value = false
        }

        fun onImagesSelected(uris: List<String>) {
            viewModelScope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to prepare images",
                    "Could not attach the selected images. Please try again.",
                ),
            ) {
                val policy =
                    photoAttachmentPolicyResolver.resolve(
                        chatInputCoordinator.selectedMode.value,
                        chatInputCoordinator.selectedModelOverride.value,
                    )
                if (!policy.isEnabled) {
                    chatInputCoordinator.clearSelectedImages()
                    return@launch
                }
                chatInputCoordinator.onImagesSelected(
                    uris = uris,
                    scope = viewModelScope,
                    maxItems = policy.maxItems,
                    append = true,
                )
            }
        }

        fun removeImage(uri: String) = chatInputCoordinator.removeImage(uri)

        fun clearSelectedImages() = chatInputCoordinator.clearSelectedImages()

        fun onFileSelected(uri: String?) = chatInputCoordinator.onFileSelected(uri, viewModelScope)

        fun clearSelectedFile() = chatInputCoordinator.clearSelectedFile()

        fun stopGeneration() {
            val currentUiState = uiState.value
            if (!currentUiState.isGenerating || !currentUiState.canStop) {
                loggingPort.debug(
                    TAG,
                    "stopGeneration ignored isGenerating=${currentUiState.isGenerating} canStop=${currentUiState.canStop}",
                )
                return
            }
            val activeTurnKey = activeTurnKeyFlow.value ?: currentUiState.activeTurnKeyFromIndicator()
            val finalContent =
                activeTurnKey
                    ?.let { key ->
                        activeTurnSnapshotStateFlow.value.snapshot
                            ?.messages
                            ?.get(key.assistantMessageId)
                    }?.content
            inferenceOrchestrator.stopInference(
                scope = viewModelScope,
                activeTurnKey = activeTurnKey,
                finalContent = finalContent,
            )
            toolExecutionBannerCoordinator.clearBanner()
        }

        fun onNavigationChatIdChanged(chatId: ChatId?) {
            inferenceOrchestrator.syncActiveChatFromNavigation(chatId)
        }

        fun createNewChat() {
            onStopTts()
            liveVoiceCoordinator.cancel()
            speechInputCoordinator.stopListening()
            activeTurnKeyFlow.value?.let { key ->
                viewModelScope.launch {
                    activeChatTurnSnapshotPort.clear(key)
                    inferenceOrchestrator.acknowledgeTurn(key)
                }
            }
            inferenceOrchestrator.createNewChat()
            chatInputCoordinator.clearInput()
            chatInputCoordinator.clearModelOverride()
            _isModelOverrideSheetVisible.value = false
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                val persistedMode = uiSelectionRepository.selectedChatMode.first()
                chatInputCoordinator.onModeChange(ChatModeMapper.run { persistedMode.toUi() })
            }
            toolExecutionBannerCoordinator.clearBanner()
        }

        fun onSendMessage() {
            val input = chatInputCoordinator.inputText.value
            val fileText = chatInputCoordinator.selectedFileText.value
            val fileName = chatInputCoordinator.selectedFileName.value

            viewModelScope.launch(
                errorHandler.coroutineExceptionHandler(
                    TAG,
                    "Failed to send message",
                    "Could not send message. Please try again.",
                ),
            ) {
                val shouldModerateResponse =
                    when (chatInputCoordinator.selectedMode.value) {
                        ChatModeUi.FAST -> shouldModeratePromptUseCase(ModelType.FAST)
                        ChatModeUi.THINKING -> shouldModeratePromptUseCase(ModelType.THINKING)
                        ChatModeUi.COUNCIL ->
                            coroutineScope {
                                awaitAll(
                                    async { shouldModeratePromptUseCase(ModelType.DRAFT_ONE) },
                                    async { shouldModeratePromptUseCase(ModelType.DRAFT_TWO) },
                                    async { shouldModeratePromptUseCase(ModelType.MAIN) },
                                    async { shouldModeratePromptUseCase(ModelType.FINAL_SYNTHESIS) },
                                ).any { it }
                            }
                        ChatModeUi.OTHER ->
                            chatInputCoordinator.selectedModelOverride.value
                                ?.let { shouldModeratePromptUseCase(it) }
                                ?: true
                    }
                val selectedImageUris =
                    if (
                        photoAttachmentPolicyResolver
                            .resolve(
                                chatInputCoordinator.selectedMode.value,
                                chatInputCoordinator.selectedModelOverride.value,
                            ).isEnabled
                    ) {
                        chatInputCoordinator.selectedImageUris.value
                    } else {
                        emptyList()
                    }
                val fileAttachments =
                    if (fileText != null && fileName != null) {
                        listOf(FileAttachment(name = fileName, content = fileText))
                    } else {
                        emptyList()
                    }
                val responseMode =
                    ChatResponseModeMapper.fromSelection(
                        chatInputCoordinator.selectedMode.value,
                        chatInputCoordinator.selectedModelOverride.value,
                    )
                inferenceOrchestrator.sendMessage(
                    scope = viewModelScope,
                    input = input,
                    responseMode = responseMode,
                    selectedImageUris = selectedImageUris,
                    fileAttachments = fileAttachments,
                    initialChatId = initialChatId,
                    shouldModerateResponse = shouldModerateResponse,
                    onInputCleared = { chatInputCoordinator.clearInput() },
                    onResponseBlocked = {
                        loggingPort.debug(TAG, "Response blocked by post-turn moderation")
                        viewModelScope.launch {
                            _moderationMessages.send(R.string.chat_moderation_prompt_rejected)
                        }
                    },
                )
            }
        }

        fun onShareClick(messageId: MessageId) {
            chatShareCoordinator.onShareClick(
                messageId = messageId,
                chatId = inferenceOrchestrator.currentChatId.value ?: initialChatId,
            )
        }

        fun onShareTurn() {
            chatShareCoordinator.onShareTurn(
                scope = viewModelScope,
                chatId = inferenceOrchestrator.currentChatId.value ?: initialChatId,
            )
        }

        fun onShareFullChat() {
            chatShareCoordinator.onShareFullChat(
                scope = viewModelScope,
                chatId = inferenceOrchestrator.currentChatId.value ?: initialChatId,
            )
        }

        fun onDismissShareSheet() = chatShareCoordinator.onDismissShareSheet()

        fun onResponseStatsClick(messageId: MessageId) {
            val snapshot = activeTurnSnapshotStateFlow.value.snapshot
            // Only use in-memory telemetry rows that belong to the clicked message. Previously this fell
            // back to every message's rows in the active snapshot when the clicked message had none, which
            // inflated the displayed input total with unrelated rows and then "dropped" once persistence
            // replaced the in-memory data with the correct per-message rows.
            val inMemoryRows =
                snapshot
                    ?.messages
                    ?.get(messageId)
                    ?.telemetryRows
                    .orEmpty()
            chatTelemetryCoordinator.onResponseStatsClick(
                messageId = messageId,
                inMemoryTelemetryRows = inMemoryRows,
                scope = viewModelScope,
            )
        }

        fun onChatStatsClick() {
            val chatId = inferenceOrchestrator.currentChatId.value ?: initialChatId ?: return
            val snapshot = activeTurnSnapshotStateFlow.value.snapshot
            val inMemoryRows =
                snapshot?.messages?.values?.flatMap { it.telemetryRows }.orEmpty()
            chatTelemetryCoordinator.onChatStatsClick(
                chatId = chatId,
                inMemoryTelemetryRows = inMemoryRows,
                scope = viewModelScope,
            )
        }

        fun dismissResponseStatsSheet() = chatTelemetryCoordinator.dismissResponseStats()

        fun dismissChatStatsSheet() = chatTelemetryCoordinator.dismissChatStats()

        fun onLoadSharedChat(
            shareId: String,
            decryptionKey: String? = null,
        ) {
            chatShareCoordinator.onLoadSharedChat(viewModelScope, shareId, decryptionKey)
        }

        fun onMicClick() {
            speechInputCoordinator.onMicClick(viewModelScope, chatInputCoordinator.inputText.value)
        }

        fun onStartLiveVoice() {
            onStopTts()
            speechInputCoordinator.stopListening()
            liveVoiceCoordinator.start(
                scope = viewModelScope,
                chatId = uiState.value.chatId,
            )
        }

        fun onEndLiveVoice() {
            liveVoiceCoordinator.end(viewModelScope)
        }

        fun onToggleMute() {
            liveVoiceCoordinator.toggleMute()
        }

        internal fun mapToChatMessageForTesting(message: Message): ChatMessage =
            chatMessageUiMapper.map(message, modelDisplayNamesFlow.value)

        private fun markWebSearchSourceExtracted(url: String) {
            val key = activeTurnKeyFlow.value ?: return
            viewModelScope.launch {
                activeChatTurnSnapshotPort.markWebSearchSourcesExtracted(
                    key = key,
                    urls = listOf(url),
                )
            }
        }

        override fun onCleared() {
            super.onCleared()
            inferenceOrchestrator.detachOnViewModelCleared()
            liveVoiceCoordinator.cancel()
            speechInputCoordinator.stopListening()
            onStopTts()
        }

        private companion object {
            private const val TAG = "ChatViewModel"
            private const val SUBSCRIBE_TIMEOUT_MS = 5000L
            private const val SAVED_STATE_CHAT_ID_KEY = "chatId"
        }
    }

private data class ReduceAccumulator(
    val hapticState: ChatUiStateReducer.HapticReducerState = ChatUiStateReducer.HapticReducerState(),
    val reduceResult: ChatUiStateReducer.ReduceResult? = null,
)

private data class ChatUiCombineInputs(
    val inputsWithPolicy: UiInputsWithPolicy,
    val isBlocked: Boolean,
    val dbMessages: List<Message>,
    val activeTurnSnapshotState: ActiveTurnSnapshotState,
    val ttsPlayback: TtsPlaybackState,
    val studioMediaMap: Map<String, com.browntowndev.enclave.domain.port.repository.StudioMediaAsset>,
    val shareState: ShareState,
    val isReadOnly: Boolean,
    val sharedMessages: List<Message>,
    val artifactExportSheet: ArtifactExportSheetState,
    val modelDisplayNames: Map<ModelType, String>,
    val chatId: ChatId?,
    val hasLiveVoiceProviderAssigned: Boolean,
    val liveVoiceState: LiveVoiceUiState,
)

private fun buildReduceParams(
    inputs: ChatUiCombineInputs,
    hapticState: ChatUiStateReducer.HapticReducerState,
): ChatUiStateReducer.ReduceParams {
    val uiInputs = inputs.inputsWithPolicy.inputs
    return ChatUiStateReducer.ReduceParams(
        settings = uiInputs.settings,
        inputText = uiInputs.inputText,
        selectedMode = uiInputs.selectedMode,
        selectedModelOverride = uiInputs.selectedModelOverride,
        selectedImageUris = uiInputs.selectedImageUris,
        selectedFileName = uiInputs.selectedFileName,
        speechState = uiInputs.speechState,
        attachmentPolicy = inputs.inputsWithPolicy.attachmentPolicy,
        isGlobalInferenceBlocked = inputs.isBlocked,
        dbMessages = inputs.dbMessages,
        activeTurnKey = inputs.activeTurnSnapshotState.key,
        activeTurnSnapshot = inputs.activeTurnSnapshotState.snapshot,
        activeToolCallBanner = inputs.ttsPlayback.activeToolCallBanner,
        hasTtsProviderAssigned = inputs.ttsPlayback.hasTtsProviderAssigned,
        isPlayingTts = inputs.ttsPlayback.isPlayingTts,
        isLoadingTts = inputs.ttsPlayback.isLoadingTts,
        isPausedTts = inputs.ttsPlayback.isPausedTts,
        studioMediaMap = inputs.studioMediaMap,
        shareState = inputs.shareState,
        isReadOnly = inputs.isReadOnly,
        sharedMessages = inputs.sharedMessages,
        artifactExportSheet = inputs.artifactExportSheet,
        chatId = inputs.chatId,
        modelDisplayNames = inputs.modelDisplayNames,
        liveVoiceState = inputs.liveVoiceState,
        hasLiveVoiceProviderAssigned = inputs.hasLiveVoiceProviderAssigned,
        hapticState = hapticState,
    )
}

private data class UiInputs(
    val settings: SettingsData,
    val inputText: String,
    val selectedMode: ChatModeUi,
    val selectedModelOverride: ChatModelOverride?,
    val selectedImageUris: List<String>,
    val selectedFileName: String?,
    val speechState: SpeechState,
)

private data class ModelOverrideSheetAssets(
    val localAssets: List<com.browntowndev.enclave.core.ui.component.assignment.LocalModelAssetUi> = emptyList(),
    val apiAssets: List<com.browntowndev.enclave.core.ui.component.assignment.ApiModelAssetUi> = emptyList(),
)

private data class UiInputsWithPolicy(
    val inputs: UiInputs,
    val attachmentPolicy: com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy,
)

private data class TtsPlaybackState(
    val activeToolCallBanner: ToolCallBannerUi?,
    val hasTtsProviderAssigned: Boolean,
    val isPlayingTts: Boolean,
    val isLoadingTts: Boolean,
    val isPausedTts: Boolean,
)

private data class ActiveTurnCandidate(
    val chatId: ChatId?,
    val messages: List<Message>,
    val key: ActiveChatTurnKey?,
)

private data class ActiveTurnSnapshotState(
    val key: ActiveChatTurnKey?,
    val snapshot: AccumulatedMessages?,
)

private fun ChatUiState.activeTurnKeyFromIndicator(): ActiveChatTurnKey? {
    val chatId = chatId ?: return null
    val messageId = activeIndicatorMessageId ?: return null
    return messageId
        .takeIf { id ->
            messages.any { message -> message.id == id && message.role == MessageRole.Assistant }
        }?.let { id -> ActiveChatTurnKey(chatId, id) }
}

internal fun List<Message>.dbMessagesFingerprintForTesting(): Any = dbMessagesFingerprint()

private data class DbMessagesFingerprint(
    val messages: List<DbMessageFingerprint>,
)

private data class DbMessageFingerprint(
    val id: MessageId,
    val updatedAt: Long?,
    val messageState: MessageState,
    val fallbackMessage: Message?,
)

private fun List<Message>.dbMessagesFingerprint(): DbMessagesFingerprint =
    DbMessagesFingerprint(
        messages =
            map { message ->
                DbMessageFingerprint(
                    id = message.id,
                    updatedAt = message.updatedAt,
                    messageState = message.messageState,
                    fallbackMessage = message.takeIf { it.updatedAt == null },
                )
            },
    )
