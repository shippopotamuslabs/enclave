package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

@Entity(
    tableName = "message_vision_analysis",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["user_message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["user_message_id"]),
        Index(value = ["user_message_id", "image_uri"], unique = true),
    ],
)
data class MessageVisionAnalysisEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "user_message_id")
    val userMessageId: MessageId,
    @ColumnInfo(name = "image_uri")
    val imageUri: String,
    @ColumnInfo(name = "prompt_text")
    val promptText: String,
    @ColumnInfo(name = "analysis_text")
    val analysisText: String,
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
)
