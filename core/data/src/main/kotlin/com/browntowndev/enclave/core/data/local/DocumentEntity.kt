package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.artifact.MimeType
import com.browntowndev.enclave.domain.model.chat.MessageId

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("message_id")],
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "message_id") val messageId: MessageId,
    val json: String,
    val title: String,
    val author: String? = null,
    @ColumnInfo(name = "target_format") val targetFormat: MimeType? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    val deleted: Boolean = false,
    @ColumnInfo(name = "pending_sync") val pendingSync: Int = 0,
)
