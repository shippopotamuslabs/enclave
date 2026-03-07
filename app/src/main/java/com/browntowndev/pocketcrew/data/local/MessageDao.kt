package com.browntowndev.pocketcrew.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessage(messageEntity: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessageSearch(messageSearch: MessageSearch): Long

    @Transaction
    open suspend fun insertMessageWithSearch(messageEntity: MessageEntity): Long {
        // With FTS4 contentEntity, Room automatically synchronizes the FTS table
        // No manual insert needed - let Room handle it
        return insertMessage(messageEntity)
    }

    @Query("SELECT * FROM message WHERE id = :id")
    abstract suspend fun getMessageById(id: Long): MessageEntity?

    @Query("""
        SELECT message.* FROM message
        JOIN message_search ON message.id = message_search.rowid
        WHERE message_search MATCH :query
    """)
    abstract fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message ORDER BY id ASC")
    abstract fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("UPDATE message SET content = :content, thinking_duration_seconds = :thinkingDuration, thinking_steps = :thinkingSteps, thinking_raw = :thinkingRaw WHERE id = :id")
    abstract suspend fun updateMessageContent(
        id: Long,
        content: String,
        thinkingDuration: Int?,
        thinkingSteps: String?,
        thinkingRaw: String?
    )
}
