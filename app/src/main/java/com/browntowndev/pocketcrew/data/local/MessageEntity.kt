package com.browntowndev.pocketcrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Entity(tableName = "message",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["user_message_id"],
        onDelete = ForeignKey.RESTRICT
    ), ForeignKey(
        entity = ChatEntity::class,
        parentColumns = ["id"],
        childColumns = ["chat_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["user_message_id"]),
        Index(value = ["chat_id"])
    ]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "role")
    val role: Role,
    @ColumnInfo(name = "user_message_id")
    val userMessageId: Long? = null,
    @ColumnInfo(name = "thinking_duration_seconds")
    val thinkingDurationSeconds: Long? = null,
    @ColumnInfo(name = "thinking_raw")
    val thinkingRaw: String? = null,
    @ColumnInfo(name = "message_state")
    val messageState: MessageState = MessageState.PROCESSING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "model_type")
    val modelType: ModelType? = null,
)
