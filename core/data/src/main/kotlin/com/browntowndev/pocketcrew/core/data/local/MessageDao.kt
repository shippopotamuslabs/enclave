package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

@Dao
abstract class MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessage(messageEntity: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMessageSearch(messageSearch: MessageSearch): Long

    @Query("SELECT * FROM message WHERE id = :id")
    abstract suspend fun getMessageById(id: MessageId): MessageEntity?

    @Query("""
        SELECT message.* FROM message
        JOIN message_search ON message.rowid = message_search.rowid
        WHERE message_search MATCH :query
    """)
    abstract fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM message ORDER BY created_at ASC")
    abstract fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("UPDATE message SET content = :content, thinking_duration_seconds = :thinkingDuration, thinking_raw = :thinkingRaw WHERE id = :id")
    abstract suspend fun updateMessageContent(
        id: MessageId,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?
    )

    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY created_at ASC")
    abstract suspend fun getMessagesByChatId(chatId: ChatId): List<MessageEntity>

    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY created_at ASC")
    abstract fun getMessagesByChatIdFlow(chatId: ChatId): Flow<List<MessageEntity>>

    /**
     * Get messages by their message states.
     * Used for CREW pipeline resume to find incomplete messages.
     *
     * @param chatId The chat ID to filter by
     * @param states The list of message states to filter by (PROCESSING, THINKING, GENERATING)
     * @return List of messages matching the given states
     */
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND message_state IN (:states) ORDER BY created_at ASC")
    abstract suspend fun getMessagesByStates(
        chatId: ChatId,
        states: List<MessageState>
    ): List<MessageEntity>

    @Query("UPDATE message SET message_state = :messageState WHERE id = :id")
    abstract suspend fun updateMessageState(id: MessageId, messageState: MessageState)

    @Query("UPDATE message SET created_at = :timestamp WHERE id = :id")
    abstract suspend fun updateMessageCreatedAt(id: MessageId, timestamp: Long)

    @Query("UPDATE message SET content = :content WHERE id = :id")
    abstract suspend fun updateMessageContentText(id: MessageId, content: String)

    @Query("UPDATE message SET model_type = :modelType WHERE id = :id")
    abstract suspend fun updateMessageModelType(id: MessageId, modelType: ModelType)

    @Query("UPDATE message SET thinking_duration_seconds = :thinkingDuration WHERE id = :id")
    abstract suspend fun updateThinkingDuration(id: MessageId, thinkingDuration: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(messageEntity: MessageEntity): Long

    @Query("UPDATE message SET thinking_start_time = :startTime WHERE id = :messageId")
    abstract suspend fun updateThinkingStartTime(messageId: MessageId, startTime: Long)

    @Query("UPDATE message SET thinking_end_time = :endTime WHERE id = :messageId")
    abstract suspend fun updateThinkingEndTime(messageId: MessageId, endTime: Long)

    @Query("UPDATE message SET thinking_raw = :thinkingRaw WHERE id = :messageId")
    abstract suspend fun updateThinkingRaw(messageId: MessageId, thinkingRaw: String?)

    @Query("UPDATE message SET pipeline_step = :pipelineStep WHERE id = :messageId")
    abstract suspend fun updateMessagePipelineStep(messageId: MessageId, pipelineStep: PipelineStep?)

    open suspend fun insertMessageWithSearch(messageEntity: MessageEntity): Long {
        return insertMessage(messageEntity)
    }

    /**
     * Persists all message data atomically in a single transaction.
     * Updates model type, thinking timestamps, thinking content, content, pipeline step, and state.
     *
     * @param messageId The ID of the message to update
     * @param modelType The model type used
     * @param thinkingStartTime The thinking start timestamp (0 if not applicable)
     * @param thinkingEndTime The thinking end timestamp (0 if not applicable)
     * @param thinkingDuration The thinking duration in seconds (null if not applicable)
     * @param thinkingRaw The raw thinking content (null to clear)
     * @param content The final message content
     * @param messageState The final message state
     * @param pipelineStep The pipeline step (for CREW mode messages)
     */
    @Transaction
    open suspend fun persistAllMessageData(
        messageId: MessageId,
        modelType: ModelType,
        thinkingStartTime: Long,
        thinkingEndTime: Long,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        content: String,
        messageState: MessageState,
        pipelineStep: PipelineStep?,
        tavilySources: List<TavilySourceEntity> = emptyList()
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
        // Update pipeline step
        updateMessagePipelineStep(messageId, pipelineStep)
        // Update state
        updateMessageState(messageId, messageState)

        // Persist search sources
        if (tavilySources.isNotEmpty()) {
            insertTavilySources(tavilySources)
        }

        if (messageState == MessageState.COMPLETE) {
            updateMessageCreatedAt(messageId, System.currentTimeMillis())
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTavilySources(sources: List<TavilySourceEntity>)
}
