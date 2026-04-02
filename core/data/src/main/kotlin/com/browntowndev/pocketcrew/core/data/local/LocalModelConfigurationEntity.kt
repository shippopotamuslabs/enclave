package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "local_model_configurations",
    foreignKeys = [
        ForeignKey(
            entity = LocalModelEntity::class,
            parentColumns = ["id"],
            childColumns = ["local_model_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_model_id", "display_name"], unique = true)
    ]
)
data class LocalModelConfigurationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "local_model_id")
    val localModelId: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.7,

    @ColumnInfo(name = "top_k")
    val topK: Int = 40,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "min_p")
    val minP: Double = 0.05,

    @ColumnInfo(name = "repetition_penalty")
    val repetitionPenalty: Double = 1.1,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int = 4096,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int = 4096,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "is_system_preset")
    val isSystemPreset: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)