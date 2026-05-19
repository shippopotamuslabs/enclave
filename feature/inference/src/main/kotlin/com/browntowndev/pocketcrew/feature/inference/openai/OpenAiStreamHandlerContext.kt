package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort

/** Shared dependencies for OpenAI Responses API stream event handlers. */
internal class OpenAiStreamHandlerContext(
    val provider: String,
    val modelId: String,
    val modelType: ModelType,
    val loggingPort: LoggingPort,
    val tag: String,
    val chatId: ChatId?,
    val userMessageId: MessageId?,
    val assistantMessageId: MessageId?,
    val emitEvent: suspend (InferenceEvent) -> Unit,
)
