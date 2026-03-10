package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.chat.Message

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
     */
    suspend fun saveMessage(message: Message)

    /**
     * Retrieves a message by its ID.
     *
     * @param id The message ID
     * @return The message if found, null otherwise
     */
    suspend fun getMessageById(id: Long): Message?
}
