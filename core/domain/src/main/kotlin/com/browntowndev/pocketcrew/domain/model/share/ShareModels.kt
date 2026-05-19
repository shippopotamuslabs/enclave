package com.browntowndev.pocketcrew.domain.model.share

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.serialization.Serializable

@Serializable
data class SharedChat(
    val id: String,
    val messages: List<SharedMessage>,
    val media: List<SharedMedia> = emptyList(),
    val createdAt: Long,
    val expiresAt: Long,
    val shareUrl: String,
)

@Serializable
data class SharedMessage(
    val role: String,
    val content: String,
    val timestamp: Long,
    val modelDisplayName: String = "",
    val attachedImageMediaIds: List<String> = emptyList(),
    val generatedMediaIds: List<String> = emptyList(),
    val fileAttachments: List<SharedFileAttachment> = emptyList(),
)

@Serializable
data class SharedFileAttachment(
    val name: String,
    val content: String,
)

@Serializable
data class SharedMedia(
    val id: String,
    val mediaType: String,
    val encryptedBytes: String? = null, // base64 for small files or as a choice
    val storagePath: String? = null,
    val originalMime: String,
)

sealed interface ShareOption {
    data class SingleTurn(
        val chatId: ChatId,
        val assistantMessageId: MessageId,
    ) : ShareOption

    data class FullConversation(
        val chatId: ChatId,
    ) : ShareOption
}
