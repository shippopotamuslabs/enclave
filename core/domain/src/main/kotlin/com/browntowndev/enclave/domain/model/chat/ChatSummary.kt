package com.browntowndev.enclave.domain.model.chat

/**
 * Used for rolling context summarization.
 */
data class ChatSummary(
    val chatId: ChatId,
    val content: String,
    val lastSummarizedMessageId: MessageId?,
)
