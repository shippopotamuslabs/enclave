package com.browntowndev.pocketcrew.core.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiModelConfigurationsDao {
    @Query("SELECT * FROM api_model_configurations")
    fun observeAll(): Flow<List<ApiModelConfigurationEntity>>

    @Query("SELECT * FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun getAllForCredentials(credentialsId: Long): List<ApiModelConfigurationEntity>

    @Query("SELECT * FROM api_model_configurations WHERE id = :id")
    suspend fun getById(id: ApiModelConfigurationId): ApiModelConfigurationEntity?

    @Upsert
    suspend fun upsert(entity: ApiModelConfigurationEntity)

    @Query("DELETE FROM api_model_configurations WHERE id = :id")
    suspend fun deleteById(id: ApiModelConfigurationId)

    @Query("DELETE FROM api_model_configurations WHERE api_credentials_id = :credentialsId")
    suspend fun deleteAllForCredentials(credentialsId: Long)
}