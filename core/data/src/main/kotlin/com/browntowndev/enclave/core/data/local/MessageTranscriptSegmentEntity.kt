package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.chat.MessageId

/**
 * Ordered transcript slices for chat UI (interleaved CoT vs visible answer).
 * Synced to Firestore under `users/{uid}/message_transcript_segments/{segmentId}` with the same
 * pending_sync / soft-delete pattern as other child message rows.
 */
@Entity(
    tableName = "message_transcript_segment",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["message_id"]),
        // Non-unique: soft-deleted rows may share ordinals with newer active rows after a replace.
        Index(value = ["message_id", "ordinal"]),
    ],
)
data class MessageTranscriptSegmentEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "message_id")
    val messageId: MessageId,
    @ColumnInfo(name = "ordinal")
    val ordinal: Int,
    /** [MessageTranscriptKindEntity] */
    @ColumnInfo(name = "kind")
    val kind: String,
    @ColumnInfo(name = "text")
    val text: String,
    @ColumnInfo(name = "thinking_start_time")
    val thinkingStartTime: Long? = null,
    @ColumnInfo(name = "thinking_end_time")
    val thinkingEndTime: Long? = null,
    @ColumnInfo(name = "thinking_duration_seconds")
    val thinkingDurationSeconds: Long? = null,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = 0L,
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
)

object MessageTranscriptKindEntity {
    const val THINKING = "THINKING"
    const val ASSISTANT_TEXT = "ASSISTANT_TEXT"
}
