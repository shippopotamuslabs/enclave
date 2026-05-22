package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.artifact.MimeType

@Entity(
    tableName = "message_file",
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
data class MessageFileEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "message_id") val messageId: String,
    val name: String,
    val content: String,
    @ColumnInfo(name = "mime_type")
    val mimeType: MimeType,
    @ColumnInfo(name = "remote_url")
    val remoteUrl: String? = null,
    @ColumnInfo(name = "sync_status")
    val syncStatus: String = "PENDING_UPLOAD",
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
    @ColumnInfo(name = "staging_remote_url")
    val stagingRemoteUrl: String? = null,
)
