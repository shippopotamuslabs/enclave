package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId

data class UserPromptResult(
    val userMessageId: MessageId,
    val chatId: ChatId,
    val userMessageCreatedAtMillis: Long,
)
