package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import java.util.Date

@Entity(tableName = "chat")
data class ChatEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: ChatId,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created")
    val created: Date,
    @ColumnInfo(name = "last_modified")
    val lastModified: Date,
    @ColumnInfo(name = "pinned")
    val pinned: Boolean,
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false
)
