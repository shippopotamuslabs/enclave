package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.Message
import com.browntowndev.enclave.domain.model.chat.MessageId

data class SubmitPromptRequest(
    val message: Message,
    val assistantMessageId: MessageId,
)
