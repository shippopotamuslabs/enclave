package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MessageTranscriptSegmentDao {
    @Query(
        """
        SELECT s.* FROM message_transcript_segment s
        INNER JOIN message m ON m.id = s.message_id
        WHERE m.chat_id = :chatId AND m.deleted = 0 AND s.deleted = 0
        ORDER BY s.message_id ASC, s.ordinal ASC
        """,
    )
    abstract fun observeSegmentsForChat(chatId: ChatId): Flow<List<MessageTranscriptSegmentEntity>>

    @Query("SELECT * FROM message_transcript_segment WHERE id = :id")
    abstract suspend fun getById(id: String): MessageTranscriptSegmentEntity?

    @Query(
        """
        UPDATE message_transcript_segment
        SET deleted = 1, pending_sync = 1, updated_at = :timestamp
        WHERE message_id = :messageId AND deleted = 0
        """,
    )
    abstract suspend fun softDeleteAllActiveForMessage(
        messageId: MessageId,
        timestamp: Long,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAll(segments: List<MessageTranscriptSegmentEntity>)

    @Upsert
    abstract suspend fun upsert(entity: MessageTranscriptSegmentEntity)

    @Query("UPDATE message_transcript_segment SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE id = :id")
    abstract suspend fun softDeleteById(
        id: String,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE message_transcript_segment SET pending_sync = 2 WHERE pending_sync = 1")
    abstract suspend fun claimPending()

    @Query("SELECT * FROM message_transcript_segment WHERE pending_sync = 2")
    abstract suspend fun getClaimedForSync(): List<MessageTranscriptSegmentEntity>

    @Query("UPDATE message_transcript_segment SET pending_sync = 0 WHERE id IN (:ids) AND pending_sync = 2")
    abstract suspend fun markSynced(ids: List<String>)

    @Transaction
    open suspend fun replaceSegmentsForMessage(
        messageId: MessageId,
        segments: List<MessageTranscriptSegmentEntity>,
    ) {
        val ts = System.currentTimeMillis()
        softDeleteAllActiveForMessage(messageId, ts)
        if (segments.isNotEmpty()) {
            insertAll(
                segments.map { seg ->
                    seg.copy(
                        pendingSync = 1,
                        updatedAt = ts,
                        deleted = false,
                    )
                },
            )
        }
    }
}
