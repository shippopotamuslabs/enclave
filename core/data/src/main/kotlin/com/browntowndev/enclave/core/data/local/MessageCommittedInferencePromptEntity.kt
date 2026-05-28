package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.chat.MessageId

@Entity(
    tableName = "message_committed_inference_prompt",
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
data class MessageCommittedInferencePromptEntity(
    @PrimaryKey
    @ColumnInfo(name = "message_id")
    val messageId: MessageId,
    @ColumnInfo(name = "inference_content")
    val inferenceContent: String,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
