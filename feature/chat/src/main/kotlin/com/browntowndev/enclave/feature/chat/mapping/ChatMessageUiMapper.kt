package com.browntowndev.enclave.feature.chat.mapping

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.feature.chat.ChatMessage
import com.browntowndev.enclave.feature.chat.ContentUi
import com.browntowndev.enclave.feature.chat.IndicatorState
import com.browntowndev.enclave.feature.chat.MessageRole
import com.browntowndev.enclave.feature.chat.ThinkingDataUi
import com.browntowndev.enclave.feature.chat.TranscriptSegmentUi
import com.browntowndev.enclave.feature.chat.toArtifactCardUiModel
import com.browntowndev.enclave.feature.chat.toUiList
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ChatMessageUiMapper
    @Inject
    constructor() {
        /** UI-only sentinel until [Message.chatId] is assigned after first persist. */
        private val optimisticPendingChatId = ChatId("optimistic-pending")

        private data class CachedChatMessage(
            val sourceMessage: Message,
            val modelDisplayNames: Map<ModelType, String>,
            val chatMessage: ChatMessage,
        )

        private val cache = LinkedHashMap<MessageId, CachedChatMessage>()
        private var cachedChatId: ChatId? = null

        fun onActiveChatChanged(chatId: ChatId?) {
            if (chatId == cachedChatId) return
            cachedChatId = chatId
            cache.clear()
        }

        fun map(
            message: Message,
            modelDisplayNames: Map<ModelType, String>,
        ): ChatMessage {
            cache[message.id]?.let { entry ->
                if (entry.sourceMessage == message && entry.modelDisplayNames === modelDisplayNames) {
                    return entry.chatMessage
                }
            }

            val role =
                when (message.role) {
                    Role.USER -> MessageRole.User
                    Role.ASSISTANT -> MessageRole.Assistant
                    Role.SYSTEM -> MessageRole.Assistant
                }
            val modelDisplayName =
                message.modelType?.let { modelType -> modelDisplayNames[modelType] } ?: "Agent"

            val chatMessage =
                ChatMessage(
                    id = message.id,
                    chatId = message.chatId ?: cachedChatId ?: optimisticPendingChatId,
                    role = role,
                    content =
                        ContentUi(
                            text = message.content.text,
                            pipelineStep = message.content.pipelineStep,
                            imageUris = message.content.imageUris,
                            webSearchSources = message.webSearchSources.toUiList(),
                            artifacts =
                                if (message.messageState == MessageState.COMPLETE) {
                                    message.content.artifacts.map { it.toArtifactCardUiModel() }
                                } else {
                                    emptyList()
                                },
                            fileAttachments = message.content.fileAttachments,
                            generatedMediaUris = message.content.generatedMediaUris,
                            generatedMediaAssetIds = message.content.generatedMediaAssetIds,
                            generatedMediaTypes = message.content.generatedMediaTypes,
                            generatedMediaPrompts = message.content.generatedMediaPrompts,
                        ),
                    formattedTimestamp = formatTimestamp(message.createdAt),
                    indicatorState = computeIndicatorState(message),
                    modelDisplayName = modelDisplayName,
                    webSearchSources = message.webSearchSources.toUiList(),
                    transcriptSegments = mapTranscriptSegmentsToUi(message),
                )
            cache[message.id] =
                CachedChatMessage(
                    sourceMessage = message,
                    modelDisplayNames = modelDisplayNames,
                    chatMessage = chatMessage,
                )
            return chatMessage
        }

        private fun mapTranscriptSegmentsToUi(message: Message): List<TranscriptSegmentUi> {
            val segments = message.transcriptSegments
            if (segments.isEmpty()) return emptyList()
            val isMessageComplete = message.messageState == MessageState.COMPLETE
            val lastThinkingIndex = segments.indexOfLast { it is MessageTranscriptSegment.Thinking }
            val lastAnswerIndex = segments.indexOfLast { it is MessageTranscriptSegment.AssistantText }
            val durationOverrides = distributeThinkingDurationFromAggregate(message, segments)
            return segments.mapIndexed { index, seg ->
                when (seg) {
                    is MessageTranscriptSegment.Thinking -> {
                        val durationSeconds =
                            durationOverrides[index]
                                ?: transcriptThinkingDurationSecondsForUi(seg)
                        TranscriptSegmentUi.ThinkingBlock(
                            ordinal = seg.ordinal,
                            text = seg.text,
                            durationSeconds = durationSeconds,
                            thinkingStartTimeMs = seg.thinkingStartTime,
                            isStreaming =
                                !isMessageComplete &&
                                    index == lastThinkingIndex &&
                                    seg.thinkingEndTime == null,
                        )
                    }
                    is MessageTranscriptSegment.AssistantText ->
                        TranscriptSegmentUi.AnswerBlock(
                            ordinal = seg.ordinal,
                            text = seg.text,
                            isStreaming =
                                !isMessageComplete &&
                                    index == lastAnswerIndex &&
                                    message.messageState != MessageState.COMPLETE,
                        )
                }
            }
        }

        private fun effectiveSegmentDurationSeconds(seg: MessageTranscriptSegment.Thinking): Long? {
            val start = seg.thinkingStartTime
            val end = seg.thinkingEndTime
            if (start == null || end == null || end < start) return null
            val ms = end - start
            return when {
                ms <= 0L -> null
                ms < 1000L -> 1L
                else -> ms / 1000
            }
        }

        private fun transcriptThinkingDurationSecondsForUi(seg: MessageTranscriptSegment.Thinking): Long? {
            val stored = seg.thinkingDurationSeconds
            val fromTimestamps = effectiveSegmentDurationSeconds(seg)
            return when {
                stored != null && stored > 0L -> stored
                fromTimestamps != null -> fromTimestamps
                else -> stored
            }
        }

        private fun distributeThinkingDurationFromAggregate(
            message: Message,
            segments: List<MessageTranscriptSegment>,
        ): Map<Int, Long> {
            val thinkingIndices =
                segments.mapIndexedNotNull { i, s -> i.takeIf { s is MessageTranscriptSegment.Thinking } }
            if (thinkingIndices.isEmpty()) return emptyMap()
            val allWeak =
                thinkingIndices.all { i ->
                    val d = transcriptThinkingDurationSecondsForUi(segments[i] as MessageTranscriptSegment.Thinking)
                    d == null || d == 0L
                }
            if (!allWeak) return emptyMap()
            val agg = aggregateThinkingDurationSeconds(message) ?: return emptyMap()
            if (agg <= 0L) return emptyMap()
            val n = thinkingIndices.size
            val base = agg / n
            val rem = (agg % n).toInt()
            return thinkingIndices
                .mapIndexed { idx, segIndex ->
                    val extra = if (idx < rem) 1L else 0L
                    segIndex to base + extra
                }.toMap()
        }

        private fun aggregateThinkingDurationSeconds(message: Message): Long? {
            val stored = message.thinkingDurationSeconds
            val fromTimestamps = message.computedDurationSeconds
            return when {
                stored != null && stored > 0L -> stored
                fromTimestamps != null -> fromTimestamps
                else -> stored
            }
        }

        private fun computeIndicatorState(message: Message): IndicatorState? {
            if (message.role == Role.USER) return IndicatorState.None

            return when (message.messageState) {
                MessageState.ENGINE_LOADING -> IndicatorState.EngineLoading
                MessageState.PROCESSING ->
                    if (message.hasOpenThinkingTranscriptSegment()) {
                        thinkingIndicatorState(message)
                    } else {
                        IndicatorState.Processing
                    }
                MessageState.THINKING -> thinkingIndicatorState(message)
                MessageState.GENERATING -> {
                    if (message.content.text.isBlank() &&
                        message.thinkingRaw.isNullOrBlank() &&
                        !message.hasOpenThinkingTranscriptSegment()
                    ) {
                        IndicatorState.Processing
                    } else {
                        val raw = message.thinkingRaw
                        val duration = aggregateThinkingDurationSeconds(message)
                        val startTime = message.thinkingStartTime ?: 0L
                        IndicatorState.Generating(
                            thinkingData =
                                if (!raw.isNullOrBlank() && duration != null) {
                                    ThinkingDataUi(
                                        thinkingDurationSeconds = duration,
                                        thinkingRaw = raw,
                                        thinkingStartTime = startTime,
                                    )
                                } else {
                                    null
                                },
                        )
                    }
                }
                MessageState.COMPLETE -> {
                    val raw = message.thinkingRaw
                    val duration = aggregateThinkingDurationSeconds(message)
                    val startTime = message.thinkingStartTime ?: 0L
                    IndicatorState.Complete(
                        thinkingData =
                            if (!raw.isNullOrBlank() && duration != null) {
                                ThinkingDataUi(
                                    thinkingDurationSeconds = duration,
                                    thinkingRaw = raw,
                                    thinkingStartTime = startTime,
                                )
                            } else {
                                null
                            },
                    )
                }
            }
        }

        private fun formatTimestamp(timestamp: Long?): String {
            if (timestamp == null || timestamp == 0L) return ""
            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        private fun Message.hasOpenThinkingTranscriptSegment(): Boolean {
            val last = transcriptSegments.lastOrNull() as? MessageTranscriptSegment.Thinking ?: return false
            return last.thinkingEndTime == null
        }

        private fun thinkingIndicatorState(message: Message): IndicatorState.Thinking {
            val duration = aggregateThinkingDurationSeconds(message)
            return IndicatorState.Thinking(
                thinkingRaw = message.thinkingRaw ?: "",
                thinkingDurationSeconds = duration ?: 0L,
                thinkingStartTime = message.thinkingStartTime ?: 0L,
            )
        }
    }
