package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import kotlinx.coroutines.flow.Flow

interface ApiModelRepositoryPort {
    fun observeAllCredentials(): Flow<List<ApiCredentials>>
    fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>>
    suspend fun getAllCredentials(): List<ApiCredentials>
    suspend fun getCredentialsById(id: Long): ApiCredentials?
    suspend fun saveCredentials(credentials: ApiCredentials, apiKey: String): Long
    suspend fun deleteCredentials(id: Long)
    suspend fun getConfigurationsForCredentials(credentialsId: Long): List<ApiModelConfiguration>
    suspend fun getConfigurationById(id: Long): ApiModelConfiguration?
    suspend fun saveConfiguration(config: ApiModelConfiguration): Long
    suspend fun deleteConfiguration(id: Long)
    suspend fun deleteConfigurationsForCredentials(credentialsId: Long)
}