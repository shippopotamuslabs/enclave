package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

/**
 * Used for rolling context summarization.
 */
data class ChatSummary(
    val chatId: ChatId,
    val content: String,
    val lastSummarizedMessageId: MessageId?,
)
