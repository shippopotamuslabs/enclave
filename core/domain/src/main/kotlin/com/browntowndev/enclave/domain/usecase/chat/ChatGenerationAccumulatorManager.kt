package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.mergeArtifactGenerationRequests
import com.browntowndev.enclave.domain.model.chat.AccumulatedMessages
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageSnapshot
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.chat.MessageTranscriptSegment
import com.browntowndev.enclave.domain.model.chat.Mode
import com.browntowndev.enclave.domain.model.chat.WebSearchSource
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.inference.InferenceBusyException
import com.browntowndev.enclave.domain.port.repository.ChatRepository

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
                getOrCreateAccumulator(state.modelType).apply {
                    when (currentState) {
                        MessageState.THINKING -> Unit
                        MessageState.GENERATING -> {
                            if (content.isEmpty() && thinkingRaw.isEmpty()) {
                                currentState = MessageState.PROCESSING
                            }
                        }
                        else -> currentState = MessageState.PROCESSING
                    }
                }
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

            is MessageGenerationState.TelemetryCaptured -> {
                getOrCreateAccumulator(state.modelType).addTelemetry(state.telemetry)
            }

            is MessageGenerationState.StepCompleted,
            is MessageGenerationState.Finished,
            -> {
                val modelType =
                    when (state) {
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
                    replaceContent("[Blocked: ${state.reason}]")
                    isComplete = true
                    currentState = MessageState.COMPLETE
                }
            }

            is MessageGenerationState.Failed -> {
                getOrCreateAccumulator(state.modelType).apply {
                    replaceContent(
                        if (state.error is InferenceBusyException) {
                            state.error.message
                                ?: "Another message is in progress. Please wait until it completes."
                        } else {
                            "Sorry, something went wrong. Please try again."
                        },
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
            val accumulator =
                _messages.getOrPut(messageId) {
                    MessageAccumulator(
                        messageId = messageId,
                        modelType = messageSnapshot.modelType,
                        pipelineStep = messageSnapshot.pipelineStep,
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
            messages = _messages.mapValues { (_, accumulator) -> accumulator.toSnapshot() },
        )

    suspend fun markIncompleteAsCancelled() {
        if (_messages.isEmpty()) {
            val defaultModelType =
                when (mode) {
                    Mode.FAST -> ModelType.FAST
                    Mode.THINKING -> ModelType.THINKING
                    Mode.COUNCIL -> ModelType.DRAFT_ONE
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
            val updatedSources =
                accumulator.webSearchSources.map { source ->
                    if (source.url in urls) source.copy(extracted = true) else source
                }
            accumulator.webSearchSources.clear()
            accumulator.webSearchSources.addAll(updatedSources)
        }
    }

    private suspend fun getOrCreateAccumulator(modelType: ModelType): MessageAccumulator {
        val messageId =
            if (mode != Mode.COUNCIL) {
                defaultAssistantMessageId
            } else {
                assistantMessageIds[modelType]
                    ?: chatRepository
                        .createAssistantMessage(
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
    thinkingStartTime: Long? = null,
    thinkingEndTime: Long? = null,
    isComplete: Boolean = false,
    currentState: MessageState = MessageState.PROCESSING,
    pipelineStep: PipelineStep? = null,
    webSearchSources: MutableList<WebSearchSource> = mutableListOf(),
    artifacts: MutableList<ArtifactGenerationRequest> = mutableListOf(),
    generatedMediaUris: MutableList<String> = mutableListOf(),
    generatedMediaAssetIds: MutableList<String> = mutableListOf(),
    generatedMediaTypes: MutableList<String> = mutableListOf(),
    generatedMediaPrompts: MutableList<String> = mutableListOf(),
    val telemetryRows: MutableList<TurnTelemetry> = mutableListOf(),
    private val transcriptSegmentsMutable: MutableList<MutableTranscriptSegment> = mutableListOf(),
) {
    private val webSearchSourcesList = SnapshotMutableList(webSearchSources)
    private val artifactsList = SnapshotMutableList(artifacts)
    private val generatedMediaUrisList = SnapshotMutableList(generatedMediaUris)
    private val generatedMediaAssetIdsList = SnapshotMutableList(generatedMediaAssetIds)
    private val generatedMediaTypesList = SnapshotMutableList(generatedMediaTypes)
    private val generatedMediaPromptsList = SnapshotMutableList(generatedMediaPrompts)
    private val webSearchSourcesSnapshot = CachedListSnapshot(webSearchSourcesList)
    private val artifactsSnapshot = CachedListSnapshot(artifactsList)
    private val generatedMediaUrisSnapshot = CachedListSnapshot(generatedMediaUrisList)
    private val generatedMediaAssetIdsSnapshot = CachedListSnapshot(generatedMediaAssetIdsList)
    private val generatedMediaTypesSnapshot = CachedListSnapshot(generatedMediaTypesList)
    private val generatedMediaPromptsSnapshot = CachedListSnapshot(generatedMediaPromptsList)
    private var transcriptSegmentsVersion = 0
    private var cachedTranscriptSegmentsVersion = -1
    private var cachedTranscriptSegments: List<MessageTranscriptSegment> = emptyList()
    private var scalarVersion = 0
    private var telemetryRowsVersion = 0
    private var cachedSnapshotVersion = -1L
    private var cachedSnapshot: MessageSnapshot? = null

    val webSearchSources: MutableList<WebSearchSource> = webSearchSourcesList
    val artifacts: MutableList<ArtifactGenerationRequest> = artifactsList
    val generatedMediaUris: MutableList<String> = generatedMediaUrisList
    val generatedMediaAssetIds: MutableList<String> = generatedMediaAssetIdsList
    val generatedMediaTypes: MutableList<String> = generatedMediaTypesList
    val generatedMediaPrompts: MutableList<String> = generatedMediaPromptsList

    var thinkingStartTime: Long? = thinkingStartTime
        set(value) {
            if (field != value) {
                field = value
                markScalarDirty()
            }
        }

    var thinkingEndTime: Long? = thinkingEndTime
        set(value) {
            if (field != value) {
                field = value
                markScalarDirty()
            }
        }

    var isComplete: Boolean = isComplete
        set(value) {
            if (field != value) {
                field = value
                markScalarDirty()
            }
        }

    var currentState: MessageState = currentState
        set(value) {
            if (field != value) {
                field = value
                markScalarDirty()
            }
        }

    var pipelineStep: PipelineStep? = pipelineStep
        set(value) {
            if (field != value) {
                field = value
                markScalarDirty()
            }
        }

    val thinkingDurationSeconds: Long
        get() {
            val start = thinkingStartTime
            val end = thinkingEndTime
            return if (start != null && end != null) {
                (end - start) / 1000
            } else {
                0
            }
        }

    fun restoreTranscriptSegments(segments: List<MessageTranscriptSegment>) {
        transcriptSegmentsMutable.clear()
        segments.forEach { seg ->
            when (seg) {
                is MessageTranscriptSegment.Thinking ->
                    transcriptSegmentsMutable.add(
                        MutableTranscriptSegment(
                            kind = TranscriptSegmentKind.THINKING,
                            text = StringBuilder(seg.text),
                            thinkingStartMs = seg.thinkingStartTime,
                            thinkingEndMs = seg.thinkingEndTime,
                            restoredDurationSeconds = seg.thinkingDurationSeconds?.takeIf { it > 0L },
                        ),
                    )
                is MessageTranscriptSegment.AssistantText ->
                    transcriptSegmentsMutable.add(
                        MutableTranscriptSegment(
                            kind = TranscriptSegmentKind.ASSISTANT_TEXT,
                            text = StringBuilder(seg.text),
                        ),
                    )
            }
        }
        markTranscriptSegmentsDirty()
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
        markTranscriptSegmentsDirty()
        markScalarDirty()
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
        markTranscriptSegmentsDirty()
        markScalarDirty()
    }

    fun replaceContent(value: String) {
        content.clear()
        content.append(value)
        markScalarDirty()
    }

    fun addTelemetry(row: TurnTelemetry) {
        telemetryRows.add(row)
        telemetryRowsVersion += 1
    }

    private fun finalizeOpenThinkingSegment(now: Long = System.currentTimeMillis()) {
        val last = transcriptSegmentsMutable.lastOrNull() ?: return
        if (last.kind == TranscriptSegmentKind.THINKING && last.thinkingEndMs == null && last.thinkingStartMs != null) {
            last.thinkingEndMs = now
            markTranscriptSegmentsDirty()
        }
    }

    private fun finalizeOpenThinkingSegmentIfAny() {
        val last = transcriptSegmentsMutable.lastOrNull() ?: return
        if (last.kind == TranscriptSegmentKind.THINKING && last.thinkingEndMs == null && last.thinkingStartMs != null) {
            last.thinkingEndMs = System.currentTimeMillis()
            markTranscriptSegmentsDirty()
        }
    }

    fun toTranscriptSegments(finalizeOpenThinking: Boolean = true): List<MessageTranscriptSegment> {
        if (finalizeOpenThinking) {
            finalizeOpenThinkingSegmentIfAny()
        }
        if (cachedTranscriptSegmentsVersion == transcriptSegmentsVersion) {
            return cachedTranscriptSegments
        }
        cachedTranscriptSegments = buildTranscriptSegments()
        cachedTranscriptSegmentsVersion = transcriptSegmentsVersion
        return cachedTranscriptSegments
    }

    private fun buildTranscriptSegments(): List<MessageTranscriptSegment> =
        transcriptSegmentsMutable.mapIndexed { index, seg ->
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
                TranscriptSegmentKind.ASSISTANT_TEXT ->
                    MessageTranscriptSegment.AssistantText(
                        ordinal = index,
                        text = seg.text.toString(),
                    )
            }
        }

    fun toSnapshot(): MessageSnapshot {
        val shouldFinalizeOpenThinking = currentState != MessageState.THINKING
        if (shouldFinalizeOpenThinking) {
            finalizeOpenThinkingSegmentIfAny()
        }
        val snapshotVersion = snapshotVersion()
        val existingSnapshot = cachedSnapshot
        if (existingSnapshot != null && cachedSnapshotVersion == snapshotVersion) {
            return existingSnapshot
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
            webSearchSources = webSearchSourcesSnapshot.value(),
            artifacts = artifactsSnapshot.value(),
            generatedMediaUris = generatedMediaUrisSnapshot.value(),
            generatedMediaAssetIds = generatedMediaAssetIdsSnapshot.value(),
            generatedMediaTypes = generatedMediaTypesSnapshot.value(),
            generatedMediaPrompts = generatedMediaPromptsSnapshot.value(),
            transcriptSegments = toTranscriptSegments(finalizeOpenThinking = shouldFinalizeOpenThinking),
            telemetryRows = telemetryRows.toList(),
        ).also { snapshot ->
            cachedSnapshot = snapshot
            cachedSnapshotVersion = snapshotVersion
        }
    }

    private fun markTranscriptSegmentsDirty() {
        transcriptSegmentsVersion += 1
    }

    private fun markScalarDirty() {
        scalarVersion += 1
    }

    private fun snapshotVersion(): Long =
        scalarVersion.toLong() +
            webSearchSourcesList.version +
            artifactsList.version +
            generatedMediaUrisList.version +
            generatedMediaAssetIdsList.version +
            generatedMediaTypesList.version +
            generatedMediaPromptsList.version +
            transcriptSegmentsVersion +
            telemetryRowsVersion
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

private class SnapshotMutableList<T>(
    initialValues: Collection<T>,
) : AbstractMutableList<T>() {
    private val values = initialValues.toMutableList()

    var version: Int = 0
        private set

    override val size: Int
        get() = values.size

    override fun get(index: Int): T = values[index]

    override fun add(
        index: Int,
        element: T,
    ) {
        values.add(index, element)
        version += 1
    }

    override fun removeAt(index: Int): T {
        val removed = values.removeAt(index)
        version += 1
        return removed
    }

    override fun set(
        index: Int,
        element: T,
    ): T {
        val previous = values.set(index, element)
        version += 1
        return previous
    }

    override fun clear() {
        if (values.isNotEmpty()) {
            values.clear()
            version += 1
        }
    }
}

private class CachedListSnapshot<T>(
    private val source: SnapshotMutableList<T>,
) {
    private var cachedVersion = -1
    private var cachedValue: List<T> = emptyList()

    fun value(): List<T> {
        if (cachedVersion != source.version) {
            cachedValue = source.toList()
            cachedVersion = source.version
        }
        return cachedValue
    }
}

internal fun getPipelineStepForModelType(modelType: ModelType): PipelineStep =
    when (modelType) {
        ModelType.DRAFT_ONE -> PipelineStep.DRAFT_ONE
        ModelType.DRAFT_TWO -> PipelineStep.DRAFT_TWO
        ModelType.MAIN -> PipelineStep.SYNTHESIS
        ModelType.FINAL_SYNTHESIS -> PipelineStep.FINAL
        else -> PipelineStep.FINAL
    }
