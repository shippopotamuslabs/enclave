package com.browntowndev.enclave.core.data.local.dao.message

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.enclave.core.data.local.MessageEntity
import com.browntowndev.enclave.core.data.local.MessageFileEntity
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep

@Dao
@Suppress("TooManyFunctions")
interface MessageCommandDao {
    @Query(
        """
        UPDATE message 
        SET content = :content, 
            thinking_duration_seconds = :thinkingDuration, 
            thinking_raw = :thinkingRaw, 
            pending_sync = 1, 
            updated_at = :timestamp 
        WHERE id = :id
    """,
    )
    suspend fun updateMessageContent(
        id: MessageId,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET message_state = :messageState, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateMessageState(
        id: MessageId,
        messageState: MessageState,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET created_at = :timestamp, pending_sync = 1, updated_at = :syncTimestamp WHERE id = :id")
    suspend fun updateMessageCreatedAt(
        id: MessageId,
        timestamp: Long,
        syncTimestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET content = :content, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateMessageContentText(
        id: MessageId,
        content: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET model_type = :modelType, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateMessageModelType(
        id: MessageId,
        modelType: ModelType,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET thinking_duration_seconds = :thinkingDuration, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun updateThinkingDuration(
        id: MessageId,
        thinkingDuration: Int,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET content = content || :suffix, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun appendMessageContentAtomic(
        id: MessageId,
        suffix: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        "UPDATE message SET thinking_raw = COALESCE(thinking_raw, '') || :suffix, pending_sync = 1, updated_at = :timestamp WHERE id = :id",
    )
    suspend fun appendThinkingRawAtomic(
        id: MessageId,
        suffix: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Upsert
    suspend fun upsert(messageEntity: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(messageEntity: MessageEntity): Long

    @Upsert
    suspend fun insertFiles(files: List<MessageFileEntity>)

    @androidx.room.Update
    suspend fun update(messageEntity: MessageEntity)

    @Query("UPDATE message SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    suspend fun softDeleteById(
        id: MessageId,
        timestamp: Long,
    )

    @Query("DELETE FROM message WHERE id IN (:ids) AND deleted = 1")
    suspend fun purgeDeleted(ids: List<MessageId>)

    @Query("UPDATE message SET thinking_start_time = :startTime, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    suspend fun updateThinkingStartTime(
        messageId: MessageId,
        startTime: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET thinking_end_time = :endTime, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    suspend fun updateThinkingEndTime(
        messageId: MessageId,
        endTime: Long,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET thinking_raw = :thinkingRaw, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    suspend fun updateThinkingRaw(
        messageId: MessageId,
        thinkingRaw: String?,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message SET pipeline_step = :pipelineStep, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    suspend fun updateMessagePipelineStep(
        messageId: MessageId,
        pipelineStep: PipelineStep?,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query(
        """
        UPDATE message 
        SET generated_media_uris_json = :mediaUrisJson,
            generated_media_asset_ids_json = :mediaAssetIdsJson,
            generated_media_types_json = :mediaTypesJson,
            generated_media_prompts_json = :media_prompts_json,
            pending_sync = 1,
            updated_at = :timestamp
        WHERE id = :id
    """,
    )
    suspend fun updateMessageMedia(
        id: MessageId,
        mediaUrisJson: String?,
        mediaAssetIdsJson: String? = null,
        mediaTypesJson: String? = null,
        media_prompts_json: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    )
}
