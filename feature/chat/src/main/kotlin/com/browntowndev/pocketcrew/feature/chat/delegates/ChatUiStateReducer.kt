package com.browntowndev.pocketcrew.feature.chat.delegates

import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.port.inference.ActiveChatTurnKey
import com.browntowndev.pocketcrew.domain.port.media.SpeechState
import com.browntowndev.pocketcrew.domain.port.repository.SettingsData
import com.browntowndev.pocketcrew.domain.port.repository.StudioMediaAsset
import com.browntowndev.pocketcrew.feature.chat.ArtifactExportSheetState
import com.browntowndev.pocketcrew.feature.chat.ChatMessage
import com.browntowndev.pocketcrew.feature.chat.ChatMessageProjector
import com.browntowndev.pocketcrew.feature.chat.ChatModeUi
import com.browntowndev.pocketcrew.feature.chat.ChatUiState
import com.browntowndev.pocketcrew.feature.chat.IndicatorState
import com.browntowndev.pocketcrew.feature.chat.MessageRole
import com.browntowndev.pocketcrew.feature.chat.ResponseHapticPhase
import com.browntowndev.pocketcrew.feature.chat.ShareState
import com.browntowndev.pocketcrew.feature.chat.ToolCallBannerUi
import com.browntowndev.pocketcrew.feature.chat.TranscriptSegmentUi
import com.browntowndev.pocketcrew.feature.chat.mapping.ChatMessageUiMapper
import com.browntowndev.pocketcrew.feature.chat.mapping.PhotoAttachmentPolicy
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import javax.inject.Inject

class ChatUiStateReducer
    @Inject
    constructor(
        private val chatMessageUiMapper: ChatMessageUiMapper,
    ) {
        private val chatMessageProjector = ChatMessageProjector()
        private var lastResponseHapticSignature: ResponseHapticSignature? = null
        private var responseHapticPulseKey: Long = 0L

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
            val studioMediaMap: Map<String, StudioMediaAsset>,
            val shareState: ShareState,
            val isReadOnly: Boolean,
            val sharedMessages: List<Message>,
            val artifactExportSheet: ArtifactExportSheetState,
            val chatId: ChatId?,
            val modelDisplayNames: Map<ModelType, String>,
        )

        data class ReduceResult(
            val uiState: ChatUiState,
            val acknowledgedTurnKey: ActiveChatTurnKey?,
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
            val responseHapticPhase =
                resolveResponseHapticPhase(
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
                    isGlobalInferenceBlocked = params.isGlobalInferenceBlocked,
                    hapticResponse = params.settings.hapticResponse,
                    responseHapticPhase = responseHapticPhase,
                    responseHapticPulseKey = responseHapticPulseKey,
                    chatId = params.chatId,
                    isGenerating = isGenerating,
                    canStop = canStop,
                    hasActiveIndicator = hasActiveIndicator,
                    activeIndicatorMessageId = activeIndicatorMessageId,
                    activeToolCallBanner = activeToolCallBanner,
                    backgroundInferenceEnabled = params.settings.backgroundInferenceEnabled,
                    hasTtsProviderAssigned = params.hasTtsProviderAssigned,
                    isPlayingTts = params.isPlayingTts,
                    shareState = params.shareState,
                    isReadOnly = params.isReadOnly,
                    artifactExportSheet = params.artifactExportSheet,
                )
            return ReduceResult(uiState = uiState, acknowledgedTurnKey = acknowledgedTurnKey)
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

        private fun resolveResponseHapticPhase(
            activeToolCallBanner: ToolCallBannerUi?,
            visibleTextHapticSignature: ResponseHapticSignature?,
            hasThinkingIndicator: Boolean,
        ): ResponseHapticPhase {
            if (activeToolCallBanner != null) {
                lastResponseHapticSignature = visibleTextHapticSignature
                return ResponseHapticPhase.None
            }
            if (visibleTextHapticSignature == null) {
                lastResponseHapticSignature = null
                return if (hasThinkingIndicator) {
                    ResponseHapticPhase.ThinkingPulse
                } else {
                    ResponseHapticPhase.None
                }
            }

            val previous = lastResponseHapticSignature
            lastResponseHapticSignature = visibleTextHapticSignature
            val visibleTextAdvanced =
                previous != null &&
                    previous.messageId == visibleTextHapticSignature.messageId &&
                    visibleTextHapticSignature.visibleTextLength > previous.visibleTextLength
            if (!visibleTextAdvanced) return ResponseHapticPhase.None

            responseHapticPulseKey += 1
            return ResponseHapticPhase.GeneratingBuzz
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
