package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Room entity representing a configured Text-to-Speech voice/provider.
 */
@Entity(tableName = "tts_providers")
data class TtsProviderEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
    @androidx.room.ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @androidx.room.ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(name = "deleted")
    val deleted: Boolean = false
)
