package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

data class MessageWithFiles(
    @Embedded val message: MessageEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "message_id"
    )
    val files: List<MessageFileEntity>
)

@Dao
abstract class MessageDao {
    @Transaction
    @Query("SELECT * FROM message WHERE id = :id")
    abstract suspend fun getMessageById(id: MessageId): MessageWithFiles?

    @Transaction
    @Query("""
        SELECT * FROM message
        WHERE id IN (:ids)
    """)
    abstract fun getMessages(ids: List<MessageId>): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("""
        SELECT * FROM message
        WHERE id IN (:ids)
        AND chat_id = :chatId
        ORDER BY created_at IS NULL, created_at ASC
    """)
    abstract suspend fun getMessagesForChat(chatId: ChatId, ids: List<MessageId>): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message ORDER BY created_at IS NULL, created_at ASC")
    abstract fun getAllMessages(): Flow<List<MessageWithFiles>>

    @Query("UPDATE message SET content = :content, thinking_duration_seconds = :thinkingDuration, thinking_raw = :thinkingRaw WHERE id = :id")
    abstract suspend fun updateMessageContent(
        id: MessageId,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?
    )

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY created_at IS NULL, created_at ASC")
    abstract suspend fun getMessagesByChatId(chatId: ChatId): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND created_at IS NOT NULL AND created_at < :timestamp ORDER BY created_at DESC LIMIT :limit")
    abstract suspend fun getMessagesBefore(chatId: ChatId, timestamp: Long, limit: Int): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND created_at IS NOT NULL AND created_at > :timestamp ORDER BY created_at ASC LIMIT :limit")
    abstract suspend fun getMessagesAfter(chatId: ChatId, timestamp: Long, limit: Int): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId ORDER BY created_at IS NULL, created_at ASC")
    abstract fun getMessagesByChatIdFlow(chatId: ChatId): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND message_state IN (:states) ORDER BY created_at IS NULL, created_at ASC")
    abstract suspend fun getMessagesByStates(
        chatId: ChatId,
        states: List<MessageState>
    ): List<MessageWithFiles>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFiles(files: List<MessageFileEntity>)

    @androidx.room.Update
    abstract suspend fun update(messageEntity: MessageEntity)

    @Query("DELETE FROM message WHERE id = :id")
    abstract suspend fun deleteById(id: MessageId)

    @Query("UPDATE message SET thinking_start_time = :startTime WHERE id = :messageId")
    abstract suspend fun updateThinkingStartTime(messageId: MessageId, startTime: Long)

    @Query("UPDATE message SET thinking_end_time = :endTime WHERE id = :messageId")
    abstract suspend fun updateThinkingEndTime(messageId: MessageId, endTime: Long)

    @Query("UPDATE message SET thinking_raw = :thinkingRaw WHERE id = :messageId")
    abstract suspend fun updateThinkingRaw(messageId: MessageId, thinkingRaw: String?)

    @Query("UPDATE message SET pipeline_step = :pipelineStep WHERE id = :messageId")
    abstract suspend fun updateMessagePipelineStep(messageId: MessageId, pipelineStep: PipelineStep?)

    @Query("UPDATE message SET artifacts_json = :artifactsJson WHERE id = :id")
    abstract suspend fun updateMessageArtifacts(id: MessageId, artifactsJson: String?)

    @Query("""
        UPDATE message 
        SET generated_media_uris_json = :mediaUrisJson,
            generated_media_asset_ids_json = :mediaAssetIdsJson,
            generated_media_types_json = :mediaTypesJson,
            generated_media_prompts_json = :mediaPromptsJson
        WHERE id = :id
    """)
    abstract suspend fun updateMessageMedia(
        id: MessageId, 
        mediaUrisJson: String?,
        mediaAssetIdsJson: String? = null,
        mediaTypesJson: String? = null,
        mediaPromptsJson: String? = null
    )

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
        webSearchSources: List<WebSearchSourceEntity> = emptyList(),
        artifactsJson: String? = null,
        mediaUrisJson: String? = null,
        mediaAssetIdsJson: String? = null,
        mediaTypesJson: String? = null,
        mediaPromptsJson: String? = null
    ) {
        updateMessageModelType(messageId, modelType)
        if (thinkingStartTime > 0) {
            updateThinkingStartTime(messageId, thinkingStartTime)
        }
        if (thinkingEndTime > 0) {
            updateThinkingEndTime(messageId, thinkingEndTime)
        }
        if (thinkingDuration != null) {
            updateThinkingDuration(messageId, thinkingDuration)
        }
        updateThinkingRaw(messageId, thinkingRaw)
        updateMessageContentText(messageId, content)
        updateMessagePipelineStep(messageId, pipelineStep)
        updateMessageState(messageId, messageState)
        updateMessageArtifacts(messageId, artifactsJson)
        updateMessageMedia(
            id = messageId, 
            mediaUrisJson = mediaUrisJson,
            mediaAssetIdsJson = mediaAssetIdsJson,
            mediaTypesJson = mediaTypesJson,
            mediaPromptsJson = mediaPromptsJson
        )

        if (webSearchSources.isNotEmpty()) {
            insertWebSearchSources(webSearchSources)
        }

        if (messageState == MessageState.COMPLETE) {
            updateMessageCreatedAt(messageId, System.currentTimeMillis())
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertWebSearchSources(sources: List<WebSearchSourceEntity>)
}
