package com.browntowndev.enclave.feature.inference.openai

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort

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
