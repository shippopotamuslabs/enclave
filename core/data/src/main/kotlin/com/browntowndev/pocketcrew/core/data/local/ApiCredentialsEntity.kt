package com.browntowndev.pocketcrew.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

@Entity(
    tableName = "api_credentials",
    indices = [
        Index(value = ["credential_alias"], unique = true),
        Index(value = ["api_key_signature"], unique = true)
    ]
)
data class ApiCredentialsEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "provider")
    val provider: ApiProvider,

    @ColumnInfo(name = "base_url")
    val baseUrl: String? = null,

    @ColumnInfo(name = "model_id")
    val modelId: String,

    @ColumnInfo(name = "is_vision")
    val isVision: Boolean = false,

    @ColumnInfo(name = "credential_alias")
    val credentialAlias: String,

    @ColumnInfo(name = "api_key_signature")
    val apiKeySignature: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
