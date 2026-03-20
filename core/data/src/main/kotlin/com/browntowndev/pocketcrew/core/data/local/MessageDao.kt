package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Dao
abstract class MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessage(messageEntity: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessageSearch(messageSearch: MessageSearch): Long

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

    @Query("UPDATE message SET content = :content, thinking_duration_seconds = :thinkingDuration, thinking_raw = :thinkingRaw WHERE id = :id")
    abstract suspend fun updateMessageContent(
        id: Long,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?
    )

    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY id ASC")
    abstract suspend fun getMessagesByChatId(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY id ASC")
    abstract fun getMessagesByChatIdFlow(chatId: Long): Flow<List<MessageEntity>>

    /**
     * Get messages by their message states.
     * Used for CREW pipeline resume to find incomplete messages.
     *
     * @param chatId The chat ID to filter by
     * @param states The list of message states to filter by (PROCESSING, THINKING, GENERATING)
     * @return List of messages matching the given states
     */
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND message_state IN (:states) ORDER BY id ASC")
    abstract suspend fun getMessagesByStates(
        chatId: Long,
        states: List<MessageState>
    ): List<MessageEntity>

    @Query("UPDATE message SET message_state = :messageState WHERE id = :id")
    abstract suspend fun updateMessageState(id: Long, messageState: MessageState)

    @Query("UPDATE message SET content = :content WHERE id = :id")
    abstract suspend fun updateMessageContentText(id: Long, content: String)

    @Query("UPDATE message SET model_type = :modelType WHERE id = :id")
    abstract suspend fun updateMessageModelType(id: Long, modelType: ModelType)

    @Query("UPDATE message SET thinking_duration_seconds = :thinkingDuration WHERE id = :id")
    abstract suspend fun updateThinkingDuration(id: Long, thinkingDuration: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(messageEntity: MessageEntity): Long

    @Query("UPDATE message SET thinking_start_time = :startTime WHERE id = :messageId")
    abstract suspend fun updateThinkingStartTime(messageId: Long, startTime: Long)

    @Query("UPDATE message SET thinking_end_time = :endTime WHERE id = :messageId")
    abstract suspend fun updateThinkingEndTime(messageId: Long, endTime: Long)

    @Query("UPDATE message SET thinking_raw = :thinkingRaw WHERE id = :messageId")
    abstract suspend fun updateThinkingRaw(messageId: Long, thinkingRaw: String?)

    open suspend fun insertMessageWithSearch(messageEntity: MessageEntity): Long {
        return insertMessage(messageEntity)
    }

    /**
     * Persists all message data atomically in a single transaction.
     * Updates model type, thinking timestamps, thinking content, content, and state.
     * 
     * @param messageId The ID of the message to update
     * @param modelType The model type used
     * @param thinkingStartTime The thinking start timestamp (0 if not applicable)
     * @param thinkingEndTime The thinking end timestamp (0 if not applicable)
     * @param thinkingDuration The thinking duration in seconds (null if not applicable)
     * @param thinkingRaw The raw thinking content (null to clear)
     * @param content The final message content
     * @param messageState The final message state
     */
    @Transaction
    open suspend fun persistAllMessageData(
        messageId: Long,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState
    ) {
        // Update model type
        updateMessageModelType(messageId, modelType)
        // Update thinking start time
        if (thinkingStartTime > 0) {
            updateThinkingStartTime(messageId, thinkingStartTime)
        }
        // Update thinking end time
        if (thinkingEndTime > 0) {
            updateThinkingEndTime(messageId, thinkingEndTime)
        }
        // Update thinking duration
        if (thinkingDuration != null) {
            updateThinkingDuration(messageId, thinkingDuration)
        }
        // Update thinking raw
        updateThinkingRaw(messageId, thinkingRaw)
        // Update content
        updateMessageContentText(messageId, content)
        // Update state
        updateMessageState(messageId, messageState)
    }
}
