package com.browntowndev.enclave.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface DefaultModelsDao {
    @Query("SELECT * FROM default_models WHERE model_type = :modelType AND deleted = 0")
    suspend fun getDefault(modelType: ModelType): DefaultModelEntity?

    @Query("SELECT * FROM default_models WHERE deleted = 0")
    fun observeAll(): Flow<List<DefaultModelEntity>>

    @Query(
        """
        SELECT 
            dm.model_type as modelType,
            dm.local_config_id as localConfigId,
            dm.api_config_id as apiConfigId,
            lmc.display_name as localPresetName,
            amc.display_name as apiPresetName,
            amc.model_id as apiModelId,
            COALESCE(NULLIF(lm.display_name, ''), lm.huggingface_model_name) as localAssetName,
            ac.display_name as apiAssetName,
            ac.id as apiCredentialsId,
            ac.provider as apiProviderName,
            dm.tts_provider_id as ttsProviderId,
            tp.displayName as ttsAssetName,
            tp.provider as ttsProviderName,
            tp.voiceName as ttsVoiceName,
            dm.media_provider_id as mediaProviderId,
            mp.displayName as mediaAssetName,
            mp.provider as mediaProviderName,
            mp.capability as mediaCapability
        FROM default_models dm
        LEFT JOIN local_model_configurations lmc ON dm.local_config_id = lmc.id
        LEFT JOIN local_models lm ON lmc.local_model_id = lm.id
        LEFT JOIN api_model_configurations amc ON dm.api_config_id = amc.id
        LEFT JOIN api_credentials ac ON amc.api_credentials_id = ac.id
        LEFT JOIN tts_providers tp ON dm.tts_provider_id = tp.id
        LEFT JOIN media_providers mp ON dm.media_provider_id = mp.id
        WHERE dm.deleted = 0
    """,
    )
    fun observeAllWithDetails(): Flow<List<DefaultModelAssignmentView>>

    @Query("SELECT * FROM default_models WHERE deleted = 0")
    suspend fun getAll(): List<DefaultModelEntity>

    @Upsert
    suspend fun upsert(entity: DefaultModelEntity)

    @Query("UPDATE default_models SET deleted = 1, pending_sync = 1, updated_at = :timestamp WHERE model_type = :modelType")
    suspend fun softDeleteById(
        modelType: ModelType,
        timestamp: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM default_models WHERE model_type IN (:modelTypes) AND deleted = 1")
    suspend fun purgeDeleted(modelTypes: List<ModelType>)

    @Query("UPDATE default_models SET pending_sync = 2 WHERE pending_sync = 1")
    suspend fun claimPending()

    @Query("SELECT * FROM default_models WHERE pending_sync = 2")
    suspend fun getClaimedForSync(): List<DefaultModelEntity>

    @Query("UPDATE default_models SET pending_sync = 0 WHERE model_type IN (:modelTypes) AND pending_sync = 2")
    suspend fun markSynced(modelTypes: List<ModelType>)
}
