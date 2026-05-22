package com.browntowndev.enclave.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.config.MediaCapability
import com.browntowndev.enclave.domain.model.inference.ApiProvider

@Entity(tableName = "media_providers")
data class MediaProviderEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val provider: ApiProvider,
    val capability: MediaCapability,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    @androidx.room.ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
)
