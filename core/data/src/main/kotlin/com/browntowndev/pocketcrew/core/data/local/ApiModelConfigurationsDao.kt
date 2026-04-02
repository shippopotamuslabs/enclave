package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ApiModelConfigurationsDao {
    @Query("SELECT * FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun getAllForCredentials(credentialsId: Long): List<ApiModelConfigurationEntity>

    @Query("SELECT * FROM api_model_configurations WHERE id = :id")
    suspend fun getById(id: Long): ApiModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelConfigurationEntity): Long

    @Query("DELETE FROM api_model_configurations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun deleteAllForCredentials(credentialsId: Long)
}