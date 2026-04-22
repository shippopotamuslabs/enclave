package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.AccumulatedMessages
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageSnapshot
import com.browntowndev.pocketcrew.domain.model.chat.Mode
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

            is MessageGenerationState.TavilySourcesAttached -> {
                getOrCreateAccumulator(state.modelType).apply {
                    tavilySources.addAll(state.sources)
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

    fun toMessagesState(): AccumulatedMessages =
        AccumulatedMessages(
            messages = _messages.mapValues { (_, accumulator) -> accumulator.toSnapshot() }
        )

    fun markIncompleteAsCancelled() {
        _messages.values.filter { !it.isComplete }.forEach { accumulator ->
            accumulator.isComplete = true
            accumulator.currentState = MessageState.COMPLETE
        }
    }

    /**
     * Marks tavily sources as extracted for the given URLs across all accumulators.
     * Called when the extract tool completes so the UI reflects the read status immediately.
     */
    fun markSourcesExtracted(urls: List<String>) {
        _messages.values.forEach { accumulator ->
            val updatedSources = accumulator.tavilySources.map { source ->
                if (source.url in urls) source.copy(extracted = true) else source
            }
            accumulator.tavilySources.clear()
            accumulator.tavilySources.addAll(updatedSources)
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
    val tavilySources: MutableList<com.browntowndev.pocketcrew.domain.model.chat.TavilySource> = mutableListOf(),
) {
    val thinkingDurationSeconds: Long
        get() = if (thinkingStartTime != null && thinkingEndTime != null) {
            (thinkingEndTime!! - thinkingStartTime!!) / 1000
        } else {
            0
        }

    fun appendThinking(chunk: String) {
        thinkingRaw.append(chunk)
        if (thinkingStartTime == null) {
            thinkingStartTime = System.currentTimeMillis()
        }
    }

    fun appendContent(chunk: String) {
        content.append(chunk)
        if (thinkingStartTime != null && thinkingEndTime == null) {
            thinkingEndTime = System.currentTimeMillis()
        }
    }

    fun toSnapshot(): MessageSnapshot = MessageSnapshot(
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
        tavilySources = tavilySources.toList(),
    )
}

internal fun getPipelineStepForModelType(modelType: ModelType): PipelineStep =
    when (modelType) {
        ModelType.DRAFT_ONE -> PipelineStep.DRAFT_ONE
        ModelType.DRAFT_TWO -> PipelineStep.DRAFT_TWO
        ModelType.MAIN -> PipelineStep.SYNTHESIS
        ModelType.FINAL_SYNTHESIS -> PipelineStep.FINAL
        else -> PipelineStep.FINAL
    }
