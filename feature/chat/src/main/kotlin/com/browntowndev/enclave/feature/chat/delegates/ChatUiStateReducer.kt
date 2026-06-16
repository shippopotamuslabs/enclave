package com.browntowndev.enclave.feature.chat.delegates

import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.enclave.domain.port.media.SpeechState
import com.browntowndev.enclave.domain.port.repository.SettingsData
import com.browntowndev.enclave.domain.port.repository.StudioMediaAsset
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ChatMessageProjector
import com.browntowndev.enclave.feature.chat.ChatModeUi
import com.browntowndev.enclave.feature.chat.ChatUiState
import com.browntowndev.enclave.feature.chat.LiveVoiceUiState
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ResponseHapticPhase
import com.browntowndev.enclave.feature.chat.ShareState
import com.browntowndev.enclave.feature.chat.ToolCallBannerUi
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.enclave.feature.chat.mapping.PhotoAttachmentPolicy
import javax.inject.Inject

class ChatUiStateReducer
    @Inject
    constructor(
        private val chatMessageUiMapper: ChatMessageUiMapper,
    ) {
        private val chatMessageProjector = ChatMessageProjector()

        data class HapticReducerState(
            val lastResponseHapticMessageId: MessageId? = null,
            val lastResponseHapticVisibleTextLength: Int = 0,
            val responseHapticPulseKey: Long = 0L,
        )

        data class ReduceParams(
            val settings: SettingsData,
            val inputText: String,
            val selectedMode: ChatModeUi,
            val selectedImageUris: List<String>,
            val selectedFileName: String?,
            val speechState: SpeechState,
            val attachmentPolicy: PhotoAttachmentPolicy,
            val isGlobalInferenceBlocked: Boolean,
            val dbMessages: List<Message>,
            val activeTurnKey: ActiveChatTurnKey?,
            val activeTurnSnapshot: AccumulatedMessages?,
            val activeToolCallBanner: ToolCallBannerUi?,
            val hasTtsProviderAssigned: Boolean,
            val isPlayingTts: Boolean,
            val isLoadingTts: Boolean = false,
            val isPausedTts: Boolean = false,
            val studioMediaMap: Map<String, StudioMediaAsset>,
            val shareState: ShareState,
            val isReadOnly: Boolean,
            val sharedMessages: List<Message>,
            val artifactExportSheet: ArtifactExportSheetState,
            val chatId: ChatId?,
            val modelDisplayNames: Map<ModelType, String>,
            val liveVoiceState: LiveVoiceUiState = LiveVoiceUiState.Idle,
            val hasLiveVoiceProviderAssigned: Boolean = false,
            val hapticState: HapticReducerState = HapticReducerState(),
            val selectedModelOverride: ChatModelOverride? = null,
        )

        data class ReduceResult(
            val uiState: ChatUiState,
            val acknowledgedTurnKey: ActiveChatTurnKey?,
            val hapticState: HapticReducerState,
        )

        fun reduce(params: ReduceParams): ReduceResult {
            chatMessageUiMapper.onActiveChatChanged(params.chatId)
            chatMessageProjector.onActiveChatChanged(params.chatId)
            val messages = if (params.isReadOnly) params.sharedMessages else params.dbMessages
            val projectedMessages =
                chatMessageProjector.project(
                    dbMessages = messages,
                    activeSnapshot = params.activeTurnSnapshot,
                    activeKey = params.activeTurnKey,
                )
            val acknowledgedTurnKey = params.activeTurnKey.takeIf { projectedMessages.handoffReady }

            val reconciledMessages =
                projectedMessages.messages.map { message ->
                    if (message.content.generatedMediaAssetIds.isEmpty()) {
                        message
                    } else {
                        val reconciledUris =
                            message.content.generatedMediaAssetIds.mapIndexed { index, id ->
                                params.studioMediaMap[id]?.localUri
                                    ?: message.content.generatedMediaUris.getOrNull(index)
                                    ?: ""
                            }
                        message.copy(
                            content = message.content.copy(generatedMediaUris = reconciledUris),
                        )
                    }
                }

            var isGenerating = false
            var canStop = true
            var hasActiveIndicator = false
            var activeIndicatorMessageId: MessageId? = null
            var hasThinkingIndicator = false
            val chatMessages: List<ChatMessage> =
                reconciledMessages.map { message ->
                    val chatMessage = chatMessageUiMapper.map(message, params.modelDisplayNames)
                    val state = chatMessage.indicatorState
                    if (state != null && state !is IndicatorState.None) {
                        hasActiveIndicator = true
                        activeIndicatorMessageId = chatMessage.id
                        val isActiveGenerationState =
                            state is IndicatorState.Generating ||
                                state is IndicatorState.Thinking ||
                                state is IndicatorState.Processing ||
                                state is IndicatorState.EngineLoading
                        if (!isGenerating && isActiveGenerationState) {
                            isGenerating = true
                            if (state is IndicatorState.EngineLoading) {
                                canStop = false
                            }
                        }
                        if (state is IndicatorState.Thinking && chatMessage.visibleAnswerTextLength() == 0) {
                            hasThinkingIndicator = true
                        }
                    }
                    chatMessage
                }

            val activeToolCallBanner = params.activeToolCallBanner
            val activeToolCallMessageId =
                activeToolCallBanner?.let {
                    resolveActiveToolCallMessageId(
                        activeIndicatorMessageId = activeIndicatorMessageId,
                        activeTurnKey = params.activeTurnKey,
                        chatMessages = chatMessages,
                    )
                }
            if (activeToolCallBanner != null) {
                hasActiveIndicator = true
                isGenerating = true
                activeIndicatorMessageId = activeToolCallMessageId
            }

            val visibleTextHapticSignature = chatMessages.latestVisibleTextHapticSignature()
            val hapticResolution =
                resolveResponseHapticPhase(
                    hapticState = params.hapticState,
                    activeToolCallBanner = activeToolCallBanner,
                    visibleTextHapticSignature = visibleTextHapticSignature,
                    hasThinkingIndicator = hasThinkingIndicator,
                )

            val uiState =
                ChatUiState(
                    messages = chatMessages,
                    inputText = params.inputText,
                    speechState = params.speechState,
                    selectedImageUris = params.selectedImageUris,
                    selectedFileName = params.selectedFileName,
                    isPhotoAttachmentEnabled = params.attachmentPolicy.isEnabled,
                    photoAttachmentMaxItems = params.attachmentPolicy.maxItems,
                    photoAttachmentDisabledReason = params.attachmentPolicy.disabledReason,
                    selectedMode = params.selectedMode,
                    selectedModeModelName =
                        resolveModelNameForMode(
                            mode = params.selectedMode,
                            modelDisplayNames = params.modelDisplayNames,
                            selectedModelOverride = params.selectedModelOverride,
                        ),
                    modeModelNames =
                        ChatModeUi.entries
                            .filter { it != ChatModeUi.OTHER }
                            .associateWith { mode ->
                                resolveModelNameForMode(
                                    mode = mode,
                                    modelDisplayNames = params.modelDisplayNames,
                                    selectedModelOverride = null,
                                )
                            },
                    isGlobalInferenceBlocked = params.isGlobalInferenceBlocked,
                    hapticResponse = params.settings.hapticResponse,
                    responseHapticPhase = hapticResolution.phase,
                    responseHapticPulseKey = hapticResolution.updatedState.responseHapticPulseKey,
                    chatId = params.chatId,
                    isGenerating = isGenerating,
                    canStop = canStop,
                    hasActiveIndicator = hasActiveIndicator,
                    activeIndicatorMessageId = activeIndicatorMessageId,
                    activeToolCallBanner = activeToolCallBanner,
                    backgroundInferenceEnabled = params.settings.backgroundInferenceEnabled,
                    hasTtsProviderAssigned = params.hasTtsProviderAssigned,
                    liveVoiceState = params.liveVoiceState,
                    hasLiveVoiceProviderAssigned = params.hasLiveVoiceProviderAssigned,
                    isPlayingTts = params.isPlayingTts,
                    isLoadingTts = params.isLoadingTts,
                    isPausedTts = params.isPausedTts,
                    shareState = params.shareState,
                    isReadOnly = params.isReadOnly,
                    artifactExportSheet = params.artifactExportSheet,
                )
            return ReduceResult(
                uiState = uiState,
                acknowledgedTurnKey = acknowledgedTurnKey,
                hapticState = hapticResolution.updatedState,
            )
        }

        private fun resolveActiveToolCallMessageId(
            activeIndicatorMessageId: MessageId?,
            activeTurnKey: ActiveChatTurnKey?,
            chatMessages: List<ChatMessage>,
        ): MessageId? =
            activeIndicatorMessageId
                ?: activeTurnKey
                    ?.assistantMessageId
                    ?.takeIf { assistantMessageId -> chatMessages.any { it.id == assistantMessageId } }
                ?: chatMessages.lastOrNull { it.role == MessageRole.Assistant }?.id
                ?: chatMessages.lastOrNull { it.role == MessageRole.User }?.id

        private data class HapticResolution(
            val phase: ResponseHapticPhase,
            val updatedState: HapticReducerState,
        )

        private fun resolveResponseHapticPhase(
            hapticState: HapticReducerState,
            activeToolCallBanner: ToolCallBannerUi?,
            visibleTextHapticSignature: ResponseHapticSignature?,
            hasThinkingIndicator: Boolean,
        ): HapticResolution {
            if (activeToolCallBanner != null) {
                return HapticResolution(
                    phase = ResponseHapticPhase.None,
                    updatedState =
                        hapticState.copy(
                            lastResponseHapticMessageId = visibleTextHapticSignature?.messageId,
                            lastResponseHapticVisibleTextLength = visibleTextHapticSignature?.visibleTextLength ?: 0,
                        ),
                )
            }
            if (visibleTextHapticSignature == null) {
                return HapticResolution(
                    phase =
                        if (hasThinkingIndicator) {
                            ResponseHapticPhase.ThinkingPulse
                        } else {
                            ResponseHapticPhase.None
                        },
                    updatedState =
                        hapticState.copy(
                            lastResponseHapticMessageId = null,
                            lastResponseHapticVisibleTextLength = 0,
                        ),
                )
            }

            val previousMessageId = hapticState.lastResponseHapticMessageId
            val previousVisibleTextLength = hapticState.lastResponseHapticVisibleTextLength
            val visibleTextAdvanced =
                previousMessageId != null &&
                    previousMessageId == visibleTextHapticSignature.messageId &&
                    visibleTextHapticSignature.visibleTextLength > previousVisibleTextLength
            val updatedState =
                hapticState.copy(
                    lastResponseHapticMessageId = visibleTextHapticSignature.messageId,
                    lastResponseHapticVisibleTextLength = visibleTextHapticSignature.visibleTextLength,
                )
            if (!visibleTextAdvanced) {
                return HapticResolution(
                    phase = ResponseHapticPhase.None,
                    updatedState = updatedState,
                )
            }

            return HapticResolution(
                phase = ResponseHapticPhase.GeneratingBuzz,
                updatedState =
                    updatedState.copy(
                        responseHapticPulseKey = updatedState.responseHapticPulseKey + 1,
                    ),
            )
        }

        private fun List<ChatMessage>.latestVisibleTextHapticSignature(): ResponseHapticSignature? =
            lastOrNull { message ->
                message.role == MessageRole.Assistant &&
                    message.indicatorState is IndicatorState.Generating &&
                    message.visibleAnswerTextLength() > 0
            }?.let { message ->
                ResponseHapticSignature(
                    messageId = message.id,
                    visibleTextLength = message.visibleAnswerTextLength(),
                )
            }

        private fun resolveModelNameForMode(
            mode: ChatModeUi,
            modelDisplayNames: Map<ModelType, String>,
            selectedModelOverride: ChatModelOverride?,
        ): String? {
            val modelType =
                when (mode) {
                    ChatModeUi.FAST -> ModelType.FAST
                    ChatModeUi.THINKING -> ModelType.THINKING
                    // Council fans out to up to 4 models; there is no single name to show.
                    ChatModeUi.COUNCIL -> return null
                    ChatModeUi.OTHER -> return selectedModelOverride?.displayName
                }
            return modelDisplayNames[modelType]?.takeIf { it.isNotEmpty() }
        }

        private fun ChatMessage.visibleAnswerTextLength(): Int {
            val answerTextLength =
                transcriptSegments
                    .filterIsInstance<TranscriptSegmentUi.AnswerBlock>()
                    .sumOf { segment -> segment.text.length }
            return answerTextLength.takeIf { it > 0 } ?: content.text.length
        }

        private data class ResponseHapticSignature(
            val messageId: MessageId,
            val visibleTextLength: Int,
        )
    }
