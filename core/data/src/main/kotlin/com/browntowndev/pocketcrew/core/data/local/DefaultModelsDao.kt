package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import kotlinx.coroutines.flow.Flow

@Dao
interface DefaultModelsDao {
    @Query("SELECT * FROM default_models WHERE model_type = :modelType")
    suspend fun getDefault(modelType: ModelType): DefaultModelEntity?

    @Query("SELECT * FROM default_models")
    fun observeAll(): Flow<List<DefaultModelEntity>>

    @Query("""
        SELECT 
            dm.model_type as modelType,
            dm.local_config_id as localConfigId,
            dm.api_config_id as apiConfigId,
            lmc.display_name as localPresetName,
            amc.display_name as apiPresetName,
            lm.huggingface_model_name as localAssetName,
            ac.display_name as apiAssetName,
            ac.provider as apiProviderName,
            dm.tts_provider_id as ttsProviderId,
            tp.displayName as ttsAssetName,
            tp.provider as ttsProviderName,
            tp.voiceName as ttsVoiceName
        FROM default_models dm
        LEFT JOIN local_model_configurations lmc ON dm.local_config_id = lmc.id
        LEFT JOIN local_models lm ON lmc.local_model_id = lm.id
        LEFT JOIN api_model_configurations amc ON dm.api_config_id = amc.id
        LEFT JOIN api_credentials ac ON amc.api_credentials_id = ac.id
        LEFT JOIN tts_providers tp ON dm.tts_provider_id = tp.id
    """)
    fun observeAllWithDetails(): Flow<List<DefaultModelAssignmentView>>

    @Query("SELECT * FROM default_models")
    suspend fun getAll(): List<DefaultModelEntity>

    @Upsert
    suspend fun upsert(entity: DefaultModelEntity)

    @Query("DELETE FROM default_models WHERE model_type = :modelType")
    suspend fun delete(modelType: ModelType)
}