@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Upsert
import androidx.room.Transaction
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow
import com.browntowndev.pocketcrew.domain.model.chat.MessageState
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
    @Query("SELECT * FROM message WHERE id = :id AND deleted = 0")
    abstract suspend fun getMessageById(id: MessageId): MessageWithFiles?

    @Transaction
    @Query("""
        SELECT * FROM message
        WHERE id IN (:ids) AND deleted = 0
    """)
    abstract fun getMessages(ids: List<MessageId>): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("""
        SELECT * FROM message
        WHERE id IN (:ids)
        AND chat_id = :chatId
        AND deleted = 0
        ORDER BY created_at IS NULL, created_at ASC
    """)
    abstract suspend fun getMessagesForChat(chatId: ChatId, ids: List<MessageId>): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    abstract fun getAllMessages(): Flow<List<MessageWithFiles>>

    @Query("""
        UPDATE message 
        SET content = :content, 
            thinking_duration_seconds = :thinkingDuration, 
            thinking_raw = :thinkingRaw, 
            pending_sync = 1, 
            updated_at = :timestamp 
        WHERE id = :id
    """)
    abstract suspend fun updateMessageContent(
        id: MessageId,
        content: String,
        thinkingDuration: Int?,
        thinkingRaw: String?,
        timestamp: Long = System.currentTimeMillis()
    )

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    abstract suspend fun getMessagesByChatId(chatId: ChatId): List<MessageWithFiles>

    @Transaction
    @Query("""
        SELECT * FROM message 
        WHERE chat_id = :chatId 
        AND created_at IS NOT NULL 
        AND created_at < :timestamp 
        ORDER BY created_at DESC 
        LIMIT :limit
    """)
    abstract suspend fun getMessagesBefore(chatId: ChatId, timestamp: Long, limit: Int): List<MessageWithFiles>

    @Transaction
    @Query("""
        SELECT * FROM message 
        WHERE chat_id = :chatId 
        AND created_at IS NOT NULL 
        AND created_at > :timestamp 
        ORDER BY created_at ASC 
        LIMIT :limit
    """)
    abstract suspend fun getMessagesAfter(chatId: ChatId, timestamp: Long, limit: Int): List<MessageWithFiles>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND deleted = 0 ORDER BY created_at IS NULL, created_at ASC")
    abstract fun getMessagesByChatIdFlow(chatId: ChatId): Flow<List<MessageWithFiles>>

    @Transaction
    @Query("SELECT * FROM message WHERE chat_id = :chatId AND message_state IN (:states) ORDER BY created_at IS NULL, created_at ASC")
    abstract suspend fun getMessagesByStates(
        chatId: ChatId,
        states: List<MessageState>
    ): List<MessageWithFiles>

    @Query("UPDATE message SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Transaction
    @Query("SELECT * FROM message WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<MessageWithFiles>

    @Query("UPDATE message SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<MessageId>)

    @Query("UPDATE message SET message_state = :messageState, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun updateMessageState(id: MessageId, messageState: MessageState, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET created_at = :timestamp, pending_sync = 1, updated_at = :syncTimestamp WHERE id = :id")
    abstract suspend fun updateMessageCreatedAt(id: MessageId, timestamp: Long, syncTimestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET content = :content, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun updateMessageContentText(id: MessageId, content: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET model_type = :modelType, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun updateMessageModelType(id: MessageId, modelType: ModelType, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET thinking_duration_seconds = :thinkingDuration, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun updateThinkingDuration(id: MessageId, thinkingDuration: Int, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET content = content || :suffix, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun appendMessageContentAtomic(id: MessageId, suffix: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET thinking_raw = COALESCE(thinking_raw, '') || :suffix, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun appendThinkingRawAtomic(id: MessageId, suffix: String, timestamp: Long = System.currentTimeMillis())

    @Upsert
    abstract suspend fun upsert(messageEntity: MessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertIfAbsent(messageEntity: MessageEntity): Long

    @Upsert
    abstract suspend fun insertFiles(files: List<MessageFileEntity>)

    @androidx.room.Update
    abstract suspend fun update(messageEntity: MessageEntity)

    @Query("UPDATE message SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun softDeleteById(id: MessageId, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM message WHERE id IN (:ids) AND deleted = 1")
    abstract suspend fun purgeDeleted(ids: List<MessageId>)

    @Query("UPDATE message SET thinking_start_time = :startTime, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    abstract suspend fun updateThinkingStartTime(messageId: MessageId, startTime: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET thinking_end_time = :endTime, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    abstract suspend fun updateThinkingEndTime(messageId: MessageId, endTime: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET thinking_raw = :thinkingRaw, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    abstract suspend fun updateThinkingRaw(messageId: MessageId, thinkingRaw: String?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET pipeline_step = :pipelineStep, pending_sync = 1, updated_at = :timestamp WHERE id = :messageId")
    abstract suspend fun updateMessagePipelineStep(messageId: MessageId, pipelineStep: PipelineStep?, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE message SET artifacts_json = :artifactsJson, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun updateMessageArtifacts(id: MessageId, artifactsJson: String?, timestamp: Long = System.currentTimeMillis())

    @Query("""
        UPDATE message 
        SET generated_media_uris_json = :mediaUrisJson,
            generated_media_asset_ids_json = :mediaAssetIdsJson,
            generated_media_types_json = :mediaTypesJson,
            generated_media_prompts_json = :media_prompts_json,
            pending_sync = 1,
            updated_at = :timestamp
        WHERE id = :id
    """)
    abstract suspend fun updateMessageMedia(
        id: MessageId, 
        mediaUrisJson: String?,
        mediaAssetIdsJson: String? = null,
        mediaTypesJson: String? = null,
        media_prompts_json: String? = null,
        timestamp: Long = System.currentTimeMillis()
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
        mediaUrisJson: String? = null,
        mediaAssetIdsJson: String? = null,
        mediaTypesJson: String? = null,
        mediaPromptsJson: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ) {
        updateMessageModelType(messageId, modelType, timestamp)
        if (thinkingStartTime > 0) {
            updateThinkingStartTime(messageId, thinkingStartTime, timestamp)
        }
        if (thinkingEndTime > 0) {
            updateThinkingEndTime(messageId, thinkingEndTime, timestamp)
        }
        if (thinkingDuration != null) {
            updateThinkingDuration(messageId, thinkingDuration, timestamp)
        }
        updateThinkingRaw(messageId, thinkingRaw, timestamp)
        updateMessageContentText(messageId, content, timestamp)
        updateMessagePipelineStep(messageId, pipelineStep, timestamp)
        updateMessageState(messageId, messageState, timestamp)

        updateMessageMedia(
            id = messageId, 
            mediaUrisJson = mediaUrisJson,
            mediaAssetIdsJson = mediaAssetIdsJson,
            mediaTypesJson = mediaTypesJson,
            media_prompts_json = mediaPromptsJson,
            timestamp = timestamp
        )

        if (webSearchSources.isNotEmpty()) {
            insertWebSearchSources(webSearchSources)
        }

        if (messageState == MessageState.COMPLETE) {
            updateMessageCreatedAt(messageId, System.currentTimeMillis())
        }
    }

    @Upsert
    abstract suspend fun insertWebSearchSources(sources: List<WebSearchSourceEntity>)

    @Query("SELECT id FROM message")
    abstract suspend fun getAllIds(): List<MessageId>
}
