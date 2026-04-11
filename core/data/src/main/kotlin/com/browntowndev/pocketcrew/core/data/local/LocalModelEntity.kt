package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat

@Entity(
    tableName = "local_models",
    indices = [Index(value = ["sha256"])]
)
data class LocalModelEntity(
    @PrimaryKey
    val id: LocalModelId,

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

    @ColumnInfo(name = "vision_capable")
    val visionCapable: Boolean = false,

    @ColumnInfo(name = "mmproj_remote_filename")
    val mmprojRemoteFilename: String? = null,

    @ColumnInfo(name = "mmproj_local_filename")
    val mmprojLocalFilename: String? = null,

    @ColumnInfo(name = "mmproj_sha256")
    val mmprojSha256: String? = null,

    @ColumnInfo(name = "mmproj_size_in_bytes")
    val mmprojSizeInBytes: Long? = null,

    @ColumnInfo(name = "thinking_enabled")
    val thinkingEnabled: Boolean = false,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
