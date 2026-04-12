package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId

@Dao
interface LocalModelConfigurationsDao {
    @Query("SELECT * FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun getAllForAsset(localModelId: LocalModelId): List<LocalModelConfigurationEntity>

    @Query("SELECT * FROM local_model_configurations WHERE id = :id")
    suspend fun getById(id: LocalModelConfigurationId): LocalModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: LocalModelConfigurationEntity)

    @Query("DELETE FROM local_model_configurations WHERE id = :id")
    suspend fun deleteById(id: LocalModelConfigurationId)

    @Query("DELETE FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun deleteAllForAsset(localModelId: LocalModelId)
}