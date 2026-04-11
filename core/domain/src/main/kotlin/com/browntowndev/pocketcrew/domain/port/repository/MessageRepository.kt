package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Port (interface) for message persistence.
 * Implemented by the data layer.
 *
 * Note: This repository handles saving messages to the message table
 * and maintaining the FTS search index. Transaction management is
 * delegated to the use case layer to keep this interface agnostic
 * of transaction lifecycle.
 */
interface MessageRepository {
    /**
     * Saves a message to the database.
     * This also handles updating the FTS search index.
     *
     * @param message The message to save
     * @return The ID of the saved message
     */
    suspend fun saveMessage(message: Message): MessageId

    /**
     * Retrieves a message by its ID.
     *
     * @param id The message ID
     * @return The message if found, null otherwise
     */
    suspend fun getMessageById(id: MessageId): Message?

    /**
     * Retrieves all messages for a specific chat, ordered by ID ascending.
     *
     * @param chatId The chat ID
     * @return List of messages in chronological order
     */
    suspend fun getMessagesForChat(chatId: ChatId): List<Message>

    suspend fun saveVisionAnalysis(
        userMessageId: MessageId,
        imageUri: String,
        promptText: String,
        analysisText: String,
        modelType: ModelType,
    )

    suspend fun getVisionAnalysesForMessages(
        userMessageIds: List<MessageId>
    ): Map<MessageId, List<MessageVisionAnalysis>>
}
