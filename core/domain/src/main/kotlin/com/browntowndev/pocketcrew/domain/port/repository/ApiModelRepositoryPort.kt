package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import kotlinx.coroutines.flow.Flow

interface ApiModelRepositoryPort {
    fun observeAllCredentials(): Flow<List<ApiCredentials>>
    fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>>
    suspend fun getAllCredentials(): List<ApiCredentials>
    suspend fun getCredentialsById(id: Long): ApiCredentials?
    suspend fun saveCredentials(
        credentials: ApiCredentials,
        apiKey: String,
        sourceCredentialAlias: String? = null
    ): Long
    suspend fun findMatchingCredentials(
        provider: ApiProvider,
        modelId: String,
        baseUrl: String?,
        apiKey: String,
        sourceCredentialAlias: String? = null,
    ): ApiCredentials?
    suspend fun deleteCredentials(id: Long)
    suspend fun getConfigurationsForCredentials(credentialsId: Long): List<ApiModelConfiguration>
    suspend fun getConfigurationById(id: ApiModelConfigurationId): ApiModelConfiguration?
    suspend fun saveConfiguration(config: ApiModelConfiguration): ApiModelConfigurationId
    suspend fun deleteConfiguration(id: ApiModelConfigurationId)
    suspend fun deleteConfigurationsForCredentials(credentialsId: Long)
}
