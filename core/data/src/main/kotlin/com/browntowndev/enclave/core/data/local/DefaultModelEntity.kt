package com.browntowndev.enclave.core.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType

@Entity(
    tableName = "default_models",
    foreignKeys = [
        ForeignKey(
            entity = LocalModelConfigurationEntity::class,
            parentColumns = ["id"],
            childColumns = ["local_config_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = ApiModelConfigurationEntity::class,
            parentColumns = ["id"],
            childColumns = ["api_config_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = TtsProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["tts_provider_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = MediaProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["media_provider_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = LiveVoiceProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["live_voice_provider_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["local_config_id"]),
        Index(value = ["api_config_id"]),
        Index(value = ["tts_provider_id"]),
        Index(value = ["media_provider_id"]),
        Index(value = ["live_voice_provider_id"]),
    ],
)
data class DefaultModelEntity(
    @PrimaryKey
    @ColumnInfo(name = "model_type")
    val modelType: ModelType,
    @ColumnInfo(name = "local_config_id")
    val localConfigId: LocalModelConfigurationId? = null,
    @ColumnInfo(name = "api_config_id")
    val apiConfigId: ApiModelConfigurationId? = null,
    @ColumnInfo(name = "tts_provider_id")
    val ttsProviderId: TtsProviderId? = null,
    @ColumnInfo(name = "media_provider_id")
    val mediaProviderId: MediaProviderId? = null,
    @ColumnInfo(name = "live_voice_provider_id")
    val liveVoiceProviderId: LiveVoiceProviderId? = null,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pending_sync")
    val pendingSync: Int = 0,
    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,
) {
    init {
        val nonNullCount =
            listOf(localConfigId, apiConfigId, ttsProviderId, mediaProviderId, liveVoiceProviderId).count { it != null }
        require(nonNullCount == 1) {
            "Exactly one of localConfigId, apiConfigId, ttsProviderId, mediaProviderId, or liveVoiceProviderId must be non-null"
        }
    }
}
