package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.WebSearchSource
import com.browntowndev.pocketcrew.domain.model.artifact.mergeArtifactGenerationRequests
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository

internal class ChatGenerationAccumulatorManager(
    private val mode: Mode,
    private val chatId: ChatId,
    private val userMessageId: MessageId,
    private val defaultAssistantMessageId: MessageId,
    private val chatRepository: ChatRepository,
) {
    private val assistantMessageIds = mutableMapOf(ModelType.DRAFT_ONE to defaultAssistantMessageId)
    private val _messages = mutableMapOf<MessageId, MessageAccumulator>()

    internal val messages: Map<MessageId, MessageAccumulator>
        get() = _messages

    @Suppress("CyclomaticComplexMethod")
    suspend fun reduce(state: MessageGenerationState): AccumulatedMessages {
        when (state) {
            is MessageGenerationState.EngineLoading -> {
                getOrCreateAccumulator(state.modelType).currentState = MessageState.ENGINE_LOADING
            }
            is MessageGenerationState.Processing -> {
                getOrCreateAccumulator(state.modelType).currentState = MessageState.PROCESSING
            }

            is MessageGenerationState.ThinkingLive -> {
                getOrCreateAccumulator(state.modelType).apply {
                    appendThinking(state.thinkingChunk)
                    currentState = MessageState.THINKING
                }
            }

            is MessageGenerationState.GeneratingText -> {
                getOrCreateAccumulator(state.modelType).apply {
                    appendContent(state.textDelta)
                    currentState = MessageState.GENERATING
                }
            }

            is MessageGenerationState.WebSearchSourcesAttached -> {
                getOrCreateAccumulator(state.modelType).apply {
                    webSearchSources.addAll(state.sources)
                }
            }

            is MessageGenerationState.ArtifactsAttached -> {
                getOrCreateAccumulator(state.modelType).apply {
                    val merged = mergeArtifactGenerationRequests(artifacts.toList(), state.artifacts)
                    artifacts.clear()
                    artifacts.addAll(merged)
                }
            }
            is MessageGenerationState.MediaAttached -> {
                getOrCreateAccumulator(state.modelType).apply {
                    state.mediaUris.forEachIndexed { index, newUri ->
                        if (generatedMediaUris.none { it == newUri }) {
                            generatedMediaUris.add(newUri)
                            // Assuming order is preserved between uris, ids, types, prompts
                            if (index < state.mediaIds.size) generatedMediaAssetIds.add(state.mediaIds[index])
                            if (index < state.mediaTypes.size) generatedMediaTypes.add(state.mediaTypes[index])
                            if (index < state.prompts.size) generatedMediaPrompts.add(state.prompts[index])
                        }
                    }
                }
            }

            is MessageGenerationState.StepCompleted,
            is MessageGenerationState.Finished -> {
                val modelType = when (state) {
                    is MessageGenerationState.StepCompleted -> state.modelType
                    is MessageGenerationState.Finished -> state.modelType
                }
                getOrCreateAccumulator(modelType).apply {
                    isComplete = true
                    currentState = MessageState.COMPLETE
                }
            }

            is MessageGenerationState.Blocked -> {
                getOrCreateAccumulator(state.modelType).apply {
                    content.clear()
                    content.append("[Blocked: ${state.reason}]")
                    isComplete = true
                    currentState = MessageState.COMPLETE
                }
            }

            is MessageGenerationState.Failed -> {
                getOrCreateAccumulator(state.modelType).apply {
                    content.clear()
                    content.append(
                        if (state.error is InferenceBusyException) {
                            state.error.message
                                ?: "Another message is in progress. Please wait until it completes."
                        } else {
                            "Error: ${state.error.message ?: "Unknown error"}"
                        }
                    )
                    isComplete = true
                    currentState = MessageState.COMPLETE
                }
            }
        }

        return toMessagesState()
    }

    /**
     * Reconciles the internal state with a snapshot from the ActiveChatTurnSnapshotPort.
     * This is crucial for capturing out-of-band events like artifact attachments that
     * happen directly via the snapshot port rather than as inference events.
     */
    suspend fun reconcileWithSnapshot(snapshot: AccumulatedMessages) {
        snapshot.messages.forEach { (messageId, messageSnapshot) ->
            // Find or create the accumulator for this message
            val accumulator = _messages.getOrPut(messageId) {
                MessageAccumulator(
                    messageId = messageId,
                    modelType = messageSnapshot.modelType,
                    pipelineStep = messageSnapshot.pipelineStep
                ).apply {
                    content.append(messageSnapshot.content)
                    thinkingRaw.append(messageSnapshot.thinkingRaw)
                    thinkingStartTime = messageSnapshot.thinkingStartTime
                    thinkingEndTime = messageSnapshot.thinkingEndTime
                    isComplete = messageSnapshot.isComplete
                    currentState = messageSnapshot.messageState
                }
            }

            if (messageSnapshot.transcriptSegments.isNotEmpty()) {
                accumulator.restoreTranscriptSegments(messageSnapshot.transcriptSegments)
            }

            // Sync artifacts (merge so port-attached placeholders upgrade when inference emits the full doc)
            val mergedArtifacts =
                mergeArtifactGenerationRequests(accumulator.artifacts.toList(), messageSnapshot.artifacts)
            accumulator.artifacts.clear()
            accumulator.artifacts.addAll(mergedArtifacts)

            // Sync web search sources
            messageSnapshot.webSearchSources.forEach { snapshotSource ->
                if (accumulator.webSearchSources.none { it.url == snapshotSource.url }) {
                    accumulator.webSearchSources.add(snapshotSource)
                }
            }

            // Sync media
            messageSnapshot.generatedMediaUris.forEachIndexed { index, snapshotUri ->
                if (accumulator.generatedMediaUris.none { it == snapshotUri }) {
                    accumulator.generatedMediaUris.add(snapshotUri)
                    if (index < messageSnapshot.generatedMediaAssetIds.size) {
                        accumulator.generatedMediaAssetIds.add(messageSnapshot.generatedMediaAssetIds[index])
                    }
                    if (index < messageSnapshot.generatedMediaTypes.size) {
                        accumulator.generatedMediaTypes.add(messageSnapshot.generatedMediaTypes[index])
                    }
                    if (index < messageSnapshot.generatedMediaPrompts.size) {
                        accumulator.generatedMediaPrompts.add(messageSnapshot.generatedMediaPrompts[index])
                    }
                }
            }
        }
    }

    fun toMessagesState(): AccumulatedMessages =
        AccumulatedMessages(
            messages = _messages.mapValues { (_, accumulator) -> accumulator.toSnapshot() }
        )

    suspend fun markIncompleteAsCancelled() {
        if (_messages.isEmpty()) {
            val defaultModelType = when (mode) {
                Mode.FAST -> ModelType.FAST
                Mode.THINKING -> ModelType.THINKING
                Mode.CREW -> ModelType.DRAFT_ONE
            }
            getOrCreateAccumulator(defaultModelType)
        }

        _messages.values.filter { !it.isComplete }.forEach { accumulator ->
            accumulator.isComplete = true
            accumulator.currentState = MessageState.COMPLETE
        }
    }

    /**
     * Marks web search sources as extracted for the given URLs across all accumulators.
     * Called when the extract tool completes so the UI reflects the read status immediately.
     */
    fun markWebSearchSourcesExtracted(urls: List<String>) {
        _messages.values.forEach { accumulator ->
            val updatedSources = accumulator.webSearchSources.map { source ->
                if (source.url in urls) source.copy(extracted = true) else source
            }
            accumulator.webSearchSources.clear()
            accumulator.webSearchSources.addAll(updatedSources)
        }
    }

    private suspend fun getOrCreateAccumulator(modelType: ModelType): MessageAccumulator {
        val messageId = if (mode != Mode.CREW) {
            defaultAssistantMessageId
        } else {
            assistantMessageIds[modelType]
                ?: chatRepository.createAssistantMessage(
                    chatId = chatId,
                    userMessageId = userMessageId,
                    modelType = modelType,
                    pipelineStep = getPipelineStepForModelType(modelType),
                ).also { newId ->
                    assistantMessageIds[modelType] = newId
                }
        }

        return _messages.getOrPut(messageId) {
            MessageAccumulator(
                messageId = messageId,
                modelType = modelType,
                pipelineStep = getPipelineStepForModelType(modelType),
            )
        }
    }
}

internal class MessageAccumulator(
    val messageId: MessageId,
    val modelType: ModelType,
    val content: StringBuilder = StringBuilder(),
    val thinkingRaw: StringBuilder = StringBuilder(),
    var thinkingStartTime: Long? = null,
    var thinkingEndTime: Long? = null,
    var isComplete: Boolean = false,
    var currentState: MessageState = MessageState.GENERATING,
    var pipelineStep: PipelineStep? = null,
    val webSearchSources: MutableList<WebSearchSource> = mutableListOf(),
    val artifacts: MutableList<com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest> = mutableListOf(),
    val generatedMediaUris: MutableList<String> = mutableListOf(),
    val generatedMediaAssetIds: MutableList<String> = mutableListOf(),
    val generatedMediaTypes: MutableList<String> = mutableListOf(),
    val generatedMediaPrompts: MutableList<String> = mutableListOf(),
    private val transcriptSegmentsMutable: MutableList<MutableTranscriptSegment> = mutableListOf(),
) {
    val thinkingDurationSeconds: Long
        get() = if (thinkingStartTime != null && thinkingEndTime != null) {
            (thinkingEndTime!! - thinkingStartTime!!) / 1000
        } else {
            0
        }

    fun restoreTranscriptSegments(segments: List<MessageTranscriptSegment>) {
        transcriptSegmentsMutable.clear()
        segments.forEach { seg ->
            when (seg) {
                is MessageTranscriptSegment.Thinking -> transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.THINKING,
                        text = StringBuilder(seg.text),
                        thinkingStartMs = seg.thinkingStartTime,
                        thinkingEndMs = seg.thinkingEndTime,
                        restoredDurationSeconds = seg.thinkingDurationSeconds?.takeIf { it > 0L },
                    ),
                )
                is MessageTranscriptSegment.AssistantText -> transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.ASSISTANT_TEXT,
                        text = StringBuilder(seg.text),
                    ),
                )
            }
        }
    }

    fun appendThinking(chunk: String) {
        if (thinkingEndTime != null) {
            thinkingEndTime = null
            thinkingStartTime = System.currentTimeMillis()
        } else if (thinkingStartTime == null) {
            thinkingStartTime = System.currentTimeMillis()
        }
        thinkingRaw.append(chunk)

        val now = System.currentTimeMillis()
        when {
            transcriptSegmentsMutable.isEmpty() -> {
                transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.THINKING,
                        text = StringBuilder(chunk),
                        thinkingStartMs = now,
                    ),
                )
            }
            transcriptSegmentsMutable.last().kind == TranscriptSegmentKind.ASSISTANT_TEXT -> {
                transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.THINKING,
                        text = StringBuilder(chunk),
                        thinkingStartMs = now,
                    ),
                )
            }
            else -> transcriptSegmentsMutable.last().text.append(chunk)
        }
    }

    fun appendContent(chunk: String) {
        content.append(chunk)
        if (thinkingStartTime != null && thinkingEndTime == null) {
            thinkingEndTime = System.currentTimeMillis()
        }

        when {
            transcriptSegmentsMutable.isEmpty() -> {
                transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.ASSISTANT_TEXT,
                        text = StringBuilder(chunk),
                    ),
                )
            }
            transcriptSegmentsMutable.last().kind == TranscriptSegmentKind.THINKING -> {
                finalizeOpenThinkingSegment()
                transcriptSegmentsMutable.add(
                    MutableTranscriptSegment(
                        kind = TranscriptSegmentKind.ASSISTANT_TEXT,
                        text = StringBuilder(chunk),
                    ),
                )
            }
            else -> transcriptSegmentsMutable.last().text.append(chunk)
        }
    }

    private fun finalizeOpenThinkingSegment(now: Long = System.currentTimeMillis()) {
        val last = transcriptSegmentsMutable.lastOrNull() ?: return
        if (last.kind == TranscriptSegmentKind.THINKING && last.thinkingEndMs == null && last.thinkingStartMs != null) {
            last.thinkingEndMs = now
        }
    }

    private fun finalizeOpenThinkingSegmentIfAny() {
        val last = transcriptSegmentsMutable.lastOrNull() ?: return
        if (last.kind == TranscriptSegmentKind.THINKING && last.thinkingEndMs == null && last.thinkingStartMs != null) {
            last.thinkingEndMs = System.currentTimeMillis()
        }
    }

    fun toTranscriptSegments(finalizeOpenThinking: Boolean = true): List<MessageTranscriptSegment> {
        if (finalizeOpenThinking) {
            finalizeOpenThinkingSegmentIfAny()
        }
        return transcriptSegmentsMutable.mapIndexed { index, seg ->
            when (seg.kind) {
                TranscriptSegmentKind.THINKING -> {
                    val durFromWallClock =
                        if (seg.thinkingStartMs != null && seg.thinkingEndMs != null) {
                            val ms = seg.thinkingEndMs!! - seg.thinkingStartMs!!
                            when {
                                ms <= 0L -> null
                                ms < 1000L -> 1L
                                else -> ms / 1000
                            }
                        } else {
                            null
                        }
                    val dur = durFromWallClock ?: seg.restoredDurationSeconds
                    MessageTranscriptSegment.Thinking(
                        ordinal = index,
                        text = seg.text.toString(),
                        thinkingDurationSeconds = dur,
                        thinkingStartTime = seg.thinkingStartMs,
                        thinkingEndTime = seg.thinkingEndMs,
                    )
                }
                TranscriptSegmentKind.ASSISTANT_TEXT -> MessageTranscriptSegment.AssistantText(
                    ordinal = index,
                    text = seg.text.toString(),
                )
            }
        }
    }

    fun toSnapshot(): MessageSnapshot {
        val shouldFinalizeOpenThinking = currentState != MessageState.THINKING
        if (shouldFinalizeOpenThinking) {
            finalizeOpenThinkingSegmentIfAny()
        }
        return MessageSnapshot(
            messageId = messageId,
            modelType = modelType,
            content = content.toString(),
            thinkingRaw = thinkingRaw.toString(),
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime ?: 0L,
            thinkingEndTime = thinkingEndTime ?: 0L,
            isComplete = isComplete,
            messageState = currentState,
            pipelineStep = pipelineStep,
            webSearchSources = webSearchSources.toList(),
            artifacts = artifacts.toList(),
            generatedMediaUris = generatedMediaUris.toList(),
            generatedMediaAssetIds = generatedMediaAssetIds.toList(),
            generatedMediaTypes = generatedMediaTypes.toList(),
            generatedMediaPrompts = generatedMediaPrompts.toList(),
            transcriptSegments = toTranscriptSegments(finalizeOpenThinking = shouldFinalizeOpenThinking),
        )
    }
}

internal enum class TranscriptSegmentKind {
    THINKING,
    ASSISTANT_TEXT,
}

internal data class MutableTranscriptSegment(
    val kind: TranscriptSegmentKind,
    val text: StringBuilder,
    var thinkingStartMs: Long? = null,
    var thinkingEndMs: Long? = null,
    /** From DB restore when wall-clock bounds are missing but denormalized seconds were stored. */
    var restoredDurationSeconds: Long? = null,
)

internal fun getPipelineStepForModelType(modelType: ModelType): PipelineStep =
    when (modelType) {
        ModelType.DRAFT_ONE -> PipelineStep.DRAFT_ONE
        ModelType.DRAFT_TWO -> PipelineStep.DRAFT_TWO
        ModelType.MAIN -> PipelineStep.SYNTHESIS
        ModelType.FINAL_SYNTHESIS -> PipelineStep.FINAL
        else -> PipelineStep.FINAL
    }
