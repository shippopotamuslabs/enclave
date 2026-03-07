package com.browntowndev.pocketcrew.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.ModelType

/**
 * Entity storing registered model metadata.
 * Used to track which model is currently installed for each slot.
 */
@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,

    @ColumnInfo(name = "remote_filename")
    val remoteFilename: String,

    @ColumnInfo(name = "huggingface_model_name")
    val huggingFaceModelName: String = "",

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "model_file_format")
    val modelFileFormat: ModelFileFormat,

    @ColumnInfo(name = "md5")
    val md5: String,

    @ColumnInfo(name = "size_in_bytes")
    val sizeInBytes: Long = 0L,

    @ColumnInfo(name = "temperature")
    val temperature: Double = 0.0,

    @ColumnInfo(name = "top_k")
    val topK: Int = 40,

    @ColumnInfo(name = "top_p")
    val topP: Double = 0.95,

    @ColumnInfo(name = "max_tokens")
    val maxTokens: Int,

    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String? = null,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
