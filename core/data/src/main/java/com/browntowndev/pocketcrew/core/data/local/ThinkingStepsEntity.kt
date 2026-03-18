package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for storing individual thinking step chunks.
 * Replaces the JSON blob in MessageEntity.thinkingSteps.
 */
@Entity(
    tableName = "thinking_steps",
    foreignKeys = [
        ForeignKey(
            entity = MessageEntity::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("message_id")]
)
data class ThinkingStepsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "message_id")
    val messageId: Long,
    @ColumnInfo(name = "thinking_chunk")
    val thinkingChunk: String
)
