package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus

@Entity(
    tableName = "local_models",
    indices = [Index(value = ["sha256"])]
)
data class LocalModelEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "model_file_format")
    val modelFileFormat: ModelFileFormat,

    @ColumnInfo(name = "huggingface_model_name")
    val huggingFaceModelName: String,

    @ColumnInfo(name = "remote_filename")
    val remoteFilename: String,

    @ColumnInfo(name = "local_filename")
    val localFilename: String,

    @ColumnInfo(name = "sha256")
    val sha256: String,

    @ColumnInfo(name = "size_in_bytes")
    val sizeInBytes: Long,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "model_status")
    val modelStatus: ModelStatus,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)