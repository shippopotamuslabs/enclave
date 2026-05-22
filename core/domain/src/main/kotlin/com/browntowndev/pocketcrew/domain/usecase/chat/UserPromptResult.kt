package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

data class UserPromptResult(
    val userMessageId: MessageId,
    val chatId: ChatId,
    val userMessageCreatedAtMillis: Long,
)
