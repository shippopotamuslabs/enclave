package com.browntowndev.pocketcrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Entity storing registered model metadata.
 * Uses a composite primary key of ModelType and ModelStatus to track current and old versions.
 */
@Entity(
    tableName = "models",
    primaryKeys = ["model_type", "model_status"]
)
data class ModelEntity(
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,

    @ColumnInfo(name = "model_status")
    val modelStatus: ModelStatus = ModelStatus.CURRENT,

    @ColumnInfo(name = "remote_filename")
    val remoteFilename: String,

    @ColumnInfo(name = "huggingface_model_name")
    val huggingFaceModelName: String = "",

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "model_file_format")
    val modelFileFormat: ModelFileFormat,

    @ColumnInfo(name = "sha256")
    val sha256: String,

    @ColumnInfo(name = "size_in_bytes")
    val sizeInBytes: Long = 0L,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.0,

    @ColumnInfo(name = "top_k")
    val topK: Int = 40,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "min_p")
    val minP: Double = 0.0,

    @ColumnInfo(name = "repetition_penalty")
    val repetitionPenalty: Double = 1.0,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int,

    @ColumnInfo(name = "context_window")
    val contextWindow: Int,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
