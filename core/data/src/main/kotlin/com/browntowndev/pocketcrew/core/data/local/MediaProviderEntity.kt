package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.config.MediaCapability
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

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
    val updatedAt: Long = System.currentTimeMillis()
)
