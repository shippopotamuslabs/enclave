package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

/**
 * Transient events representing the lifecycle of a tool execution.
 * These are used to drive UI indicators and are not persisted.
 */
sealed class ToolExecutionEvent {
    abstract val eventId: String
    abstract val chatId: ChatId?
    abstract val userMessageId: MessageId?

    data class Started(
        override val eventId: String,
        val toolName: String,
        val argumentsJson: String,
        override val chatId: ChatId?,
        override val userMessageId: MessageId?,
        val modelType: ModelType,
    ) : ToolExecutionEvent()

    data class Finished(
        override val eventId: String,
        override val chatId: ChatId?,
        override val userMessageId: MessageId?,
        val resultJson: String? = null,
        val error: String? = null,
    ) : ToolExecutionEvent()

    data class Extracting(
        override val eventId: String,
        val url: String,
        override val chatId: ChatId?,
        override val userMessageId: MessageId?,
    ) : ToolExecutionEvent()
}
