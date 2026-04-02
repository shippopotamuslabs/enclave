package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface LocalModelConfigurationsDao {
    @Query("SELECT * FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun getAllForAsset(localModelId: Long): List<LocalModelConfigurationEntity>

    @Query("SELECT * FROM local_model_configurations WHERE id = :id")
    suspend fun getById(id: Long): LocalModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: LocalModelConfigurationEntity): Long

    @Query("DELETE FROM local_model_configurations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM local_model_configurations WHERE local_model_id = :localModelId")
    suspend fun deleteAllForAsset(localModelId: Long)
}