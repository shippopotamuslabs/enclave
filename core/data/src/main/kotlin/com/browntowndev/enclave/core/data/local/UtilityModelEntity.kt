package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat

@Entity(
    tableName = "utility_models",
    indices = [
        Index(value = ["sha256"]),
        Index(value = ["utility_type"], unique = true),
    ],
)
data class UtilityModelEntity(
    @PrimaryKey
    val id: UtilityModelId,
    @ColumnInfo(name = "utility_type")
    val utilityType: UtilityType,
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
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
)
