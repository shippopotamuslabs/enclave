package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep

@Entity(tableName = "message",
    foreignKeys = [ForeignKey(
        entity = MessageEntity::class,
        parentColumns = ["id"],
        childColumns = ["user_message_id"],
        onDelete = ForeignKey.CASCADE
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
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: MessageId,
    @ColumnInfo(name = "chat_id")
    val chatId: ChatId,
    @ColumnInfo(name = "content")
    val content: String,
    @ColumnInfo(name = "image_uri")
    val imageUri: String? = null,
    @ColumnInfo(name = "role")
    val role: Role,
    @ColumnInfo(name = "user_message_id")
    val userMessageId: MessageId? = null,
    @ColumnInfo(name = "thinking_duration_seconds")
    val thinkingDurationSeconds: Long? = null,
    @ColumnInfo(name = "thinking_raw")
    val thinkingRaw: String? = null,
    @ColumnInfo(name = "thinking_start_time")
    val thinkingStartTime: Long? = null,
    @ColumnInfo(name = "thinking_end_time")
    val thinkingEndTime: Long? = null,
    @ColumnInfo(name = "message_state")
    val messageState: MessageState = MessageState.PROCESSING,
    @ColumnInfo(name = "created_at")
    val createdAt: Long? = null,
    @ColumnInfo(name = "model_type")
    val modelType: ModelType? = null,
    @ColumnInfo(name = "pipeline_step")
    val pipelineStep: PipelineStep? = null,
)
