package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.ChatDao
import com.browntowndev.pocketcrew.data.local.MessageDao
import com.browntowndev.pocketcrew.data.mapper.toEntity
import com.browntowndev.pocketcrew.domain.model.Chat
import com.browntowndev.pocketcrew.domain.model.ThinkingData
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Room implementation of ChatRepository.
 *
 * This implementation handles saving chats to the chat table.
 * Transaction management is intentionally NOT handled here - it is
 * delegated to the use case layer via TransactionProvider.
 *
 * @param chatDao The DAO for chat operations
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao
) : ChatRepository {

    /**
     * Creates a new chat in the database.
     *
     * @param chat The chat to create
     * @return The ID of the newly created chat
     */
    override suspend fun createChat(chat: Chat): Long {
        val entity = chat.toEntity()
        return chatDao.insert(entity)
    }

    /**
     * Saves an assistant message to the database.
     *
     * @param messageId The ID of the message
     * @param content The content of the message
     * @param thinkingData Optional thinking data for reasoning models
     */
    override suspend fun saveAssistantMessage(
        messageId: String,
        content: String,
        thinkingData: ThinkingData?
    ) {
        val id = messageId.toLongOrNull() ?: return
        messageDao.updateMessageContent(
            id = id,
            content = content,
            thinkingDuration = thinkingData?.durationSeconds,
            thinkingSteps = thinkingData?.steps?.joinToString("\n"),
            thinkingRaw = thinkingData?.rawFullThought
        )
    }
}
