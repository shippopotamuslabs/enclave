package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

data class AccumulatedMessages(
    val messages: Map<MessageId, MessageSnapshot>,
)

data class MessageSnapshot(
    val messageId: MessageId,
    val modelType: ModelType,
    val content: String,
    val thinkingRaw: String,
    val thinkingDurationSeconds: Long = 0,
    val thinkingStartTime: Long = 0,
    val thinkingEndTime: Long = 0,
    val isComplete: Boolean = false,
    val messageState: MessageState = if (isComplete) MessageState.COMPLETE else MessageState.GENERATING,
    val pipelineStep: PipelineStep? = null,
    val tavilySources: List<TavilySource> = emptyList(),
)
