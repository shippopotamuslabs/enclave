package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Chat
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData

/**
 * Port (interface) for chat persistence.
 * Implemented by the data layer.
 *
 * Note: This repository handles saving chats to the chat table.
 * Transaction management is delegated to the use case layer to keep
 * this interface agnostic of transaction lifecycle.
 */
interface ChatRepository {
    /**
     * Creates a new chat in the database.
     *
     * @param chat The chat to create
     * @return The ID of the newly created chat
     */
    suspend fun createChat(chat: Chat): Long

    /**
     * Saves an assistant message to the database.
     *
     * @param messageId The ID of the message
     * @param content The content of the message
     * @param thinkingData Optional thinking data for reasoning models
     */
    suspend fun saveAssistantMessage(
        messageId: Long,
        content: String,
        thinkingData: ThinkingData? = null
    )
}
