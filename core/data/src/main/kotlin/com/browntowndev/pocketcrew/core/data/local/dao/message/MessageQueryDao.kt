package com.browntowndev.pocketcrew.core.data.local.dao.message

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.browntowndev.pocketcrew.core.data.local.MessageEntity
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageQueryDao {
    @Query("SELECT chat_id FROM message WHERE id = :id AND deleted = 0")
    suspend fun getMessageChatIdById(id: MessageId): ChatId?

    @Transaction
    @Query("SELECT * FROM message WHERE id = :id AND deleted = 0")
    suspend fun getMessageById(id: MessageId): MessageWithFiles?

    @Transaction
    @Query(
        """
        SELECT * FROM message
        WHERE id IN (:ids) AND deleted = 0
    """,
    )
    fun getMessages(ids: List<MessageId>): Flow<List<MessageWithFiles>>

    @Transaction
    @Query(
        """
        SELECT * FROM message
        WHERE id IN (:ids)
        AND chat_id = :chatId
        AND deleted = 0
        ORDER BY created_at IS NULL, created_at ASC
    """,
    )
    suspend fun getMessagesForChat(
        chatId: ChatId,
        ids: List<MessageId>,
    ): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    fun getAllMessages(): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    suspend fun getMessagesByChatId(chatId: ChatId): List<MessageWithFiles>

    @Transaction
    @Query(
        """
        SELECT * FROM message 
        WHERE chat_id = :chatId 
        AND created_at IS NOT NULL 
        AND created_at < :timestamp 
        ORDER BY created_at DESC 
        LIMIT :limit
    """,
    )
    suspend fun getMessagesBefore(
        chatId: ChatId,
        timestamp: Long,
        limit: Int,
    ): List<MessageWithFiles>

    @Transaction
    @Query(
        """
        SELECT * FROM message 
        WHERE chat_id = :chatId 
        AND created_at IS NOT NULL 
        AND created_at > :timestamp 
        ORDER BY created_at ASC 
        LIMIT :limit
    """,
    )
    suspend fun getMessagesAfter(
        chatId: ChatId,
        timestamp: Long,
        limit: Int,
    ): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    fun getMessagesByChatIdFlow(chatId: ChatId): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND message_state IN (:states) ORDER BY created_at IS NULL, created_at ASC")
    suspend fun getMessagesByStates(
        chatId: ChatId,
        states: List<MessageState>,
    ): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<MessageWithFiles>

    @Query("SELECT id FROM message")
    suspend fun getAllIds(): List<MessageId>
}
