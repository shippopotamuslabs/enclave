package com.browntowndev.pocketcrew.domain.model.chat

/**
 * Domain model for a chat context summary.
 * Used for rolling context compaction.
 */
data class ChatSummary(
    val chatId: ChatId,
    val content: String,
    val lastSummarizedMessageId: MessageId?,
)
