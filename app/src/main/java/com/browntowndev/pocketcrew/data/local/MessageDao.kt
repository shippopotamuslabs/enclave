package com.browntowndev.pocketcrew.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

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

    // ===== Message State Methods =====

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

    // ===== Crew Pipeline Steps Methods =====

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertCrewPipelineStep(pipelineStep: CrewPipelineStepEntity): Long

    @Query("SELECT * FROM crew_pipeline_steps WHERE message_id = :messageId")
    abstract suspend fun getCrewPipelineStepForMessage(messageId: Long): CrewPipelineStepEntity?

    @Query("DELETE FROM crew_pipeline_steps WHERE message_id = :messageId")
    abstract suspend fun deleteCrewPipelineStepForMessage(messageId: Long)

    // ===== Thinking Steps Methods =====

    @Upsert
    abstract suspend fun upsertManyThinkingSteps(thinkingSteps: List<ThinkingStepsEntity>)

    @Query("DELETE FROM thinking_steps WHERE message_id = :messageId")
    abstract suspend fun deleteThinkingStepsForMessage(messageId: Long)

    // ===== Queries with Relations =====
    /**
     * Get all messages for a chat with both pipeline step and thinking steps.
     */
    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY id ASC")
    abstract fun getMessagesWithAllRelations(chatId: Long): Flow<List<MessageWithAllRelations>>
}
