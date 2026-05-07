package com.browntowndev.pocketcrew.domain.model.share

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.serialization.Serializable

@Serializable
data class SharedChat(
    val id: String,
    val messages: List<SharedMessage>,
    val createdAt: Long,
    val expiresAt: Long,
    val shareUrl: String
)

@Serializable
data class SharedMessage(
    val role: String,
    val content: String,
    val timestamp: Long,
    val modelDisplayName: String = ""
)

sealed interface ShareOption {
    data class SingleTurn(val chatId: ChatId, val assistantMessageId: MessageId) : ShareOption
    data class FullConversation(val chatId: ChatId) : ShareOption
}
