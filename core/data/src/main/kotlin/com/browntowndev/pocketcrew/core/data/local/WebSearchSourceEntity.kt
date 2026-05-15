package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@Entity(
    tableName = "web_search_source",
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
    ],
)
data class WebSearchSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "message_id")
    val messageId: MessageId,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "url")
    val url: String,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "score")
    val score: Double,
    @ColumnInfo(name = "extracted", defaultValue = "0")
    val extracted: Boolean = false,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
)
