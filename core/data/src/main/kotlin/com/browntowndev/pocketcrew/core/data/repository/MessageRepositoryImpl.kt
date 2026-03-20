package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.MessageDao
import com.browntowndev.pocketcrew.core.data.mapper.toDomain
import com.browntowndev.pocketcrew.core.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of MessageRepository.
 *
 * This implementation handles saving messages to both the message table
 * and the FTS search index. Transaction management is intentionally
 * NOT handled here - it is delegated to the use case layer via
 * TransactionProvider to keep this class agnostic of transaction lifecycle.
 *
 * @param messageDao The DAO for message operations
 */
@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val messageDao: MessageDao
) : MessageRepository {

    /**
     * Saves a message to the database, including the FTS search index.
     * Uses the DAO's transaction-capable method to ensure atomicity of
     * both the message and search index writes.
     *
     * Note: Transaction handling is delegated to the use case layer.
     * If multiple repository calls need to be atomic, the use case should
     * wrap them in runInTransaction.
     *
     * @param message The message to save
     * @return The ID of the saved message
     */
    override suspend fun saveMessage(message: Message): Long {
        val entity = message.toEntity()
        return messageDao.insertMessageWithSearch(entity)
    }

    /**
     * Retrieves a message by its ID.
     *
     * @param id The message ID
     * @return The message if found, null otherwise
     */
    override suspend fun getMessageById(id: Long): Message? {
        return messageDao.getMessageById(id)?.toDomain()
    }

    /**
     * Retrieves all messages for a specific chat, ordered by ID ascending.
     *
     * @param chatId The chat ID
     * @return List of messages in chronological order
     */
    override suspend fun getMessagesForChat(chatId: Long): List<Message> {
        return messageDao.getMessagesByChatId(chatId).map { it.toDomain() }
    }
}
