package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.mapper.ApiModelMapper
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiCredentials
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiModelRepositoryImpl @Inject constructor(
    private val apiCredentialsDao: ApiCredentialsDao,
    private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
    private val apiKeyManager: ApiKeyManager
) : ApiModelRepositoryPort {

    override fun observeAllCredentials(): Flow<List<ApiCredentials>> =
        apiCredentialsDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeAllConfigurations(): Flow<List<ApiModelConfiguration>> =
        apiModelConfigurationsDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getAllCredentials(): List<ApiCredentials> =
        apiCredentialsDao.getAll().map { it.toDomain() }

    override suspend fun getCredentialsById(id: Long): ApiCredentials? =
        apiCredentialsDao.getById(id)?.toDomain()

    override suspend fun saveCredentials(credentials: ApiCredentials, apiKey: String): Long {
        val entity = ApiCredentialsEntity(
            id = credentials.id,
            displayName = credentials.displayName,
            provider = credentials.provider,
            modelId = credentials.modelId,
            baseUrl = credentials.baseUrl,
            isVision = credentials.isVision,
            credentialAlias = credentials.credentialAlias,
            updatedAt = System.currentTimeMillis()
        )
        
        val id = apiCredentialsDao.upsert(entity)
        
        if (apiKey.isNotBlank()) {
            apiKeyManager.save(credentials.credentialAlias, apiKey)
        }
        
        return id
    }

    override suspend fun deleteCredentials(id: Long) {
        apiCredentialsDao.getById(id)?.let { entity ->
            apiCredentialsDao.deleteById(id)
            apiKeyManager.delete(entity.credentialAlias)
        }
    }

    override suspend fun getConfigurationsForCredentials(credentialsId: Long): List<ApiModelConfiguration> =
        apiModelConfigurationsDao.getAllForCredentials(credentialsId).map { it.toDomain() }

    override suspend fun getConfigurationById(id: Long): ApiModelConfiguration? =
        apiModelConfigurationsDao.getById(id)?.toDomain()

    override suspend fun saveConfiguration(config: ApiModelConfiguration): Long {
        val entity = ApiModelConfigurationEntity(
            id = config.id,
            apiCredentialsId = config.apiCredentialsId,
            displayName = config.displayName,
            maxTokens = config.maxTokens,
            contextWindow = config.contextWindow,
            temperature = config.temperature,
            topP = config.topP,
            topK = config.topK,
            minP = config.minP,
            frequencyPenalty = config.frequencyPenalty,
            presencePenalty = config.presencePenalty,
            systemPrompt = config.systemPrompt,
            reasoningEffort = config.reasoningEffort?.wireValue,
            customHeaders = ApiModelMapper.serializeCustomHeaders(config.customHeaders)
        )
        return apiModelConfigurationsDao.upsert(entity)
    }

    override suspend fun deleteConfiguration(id: Long) {
        apiModelConfigurationsDao.deleteById(id)
    }

    override suspend fun deleteConfigurationsForCredentials(credentialsId: Long) {
        apiModelConfigurationsDao.deleteAllForCredentials(credentialsId)
    }
    
    private fun ApiCredentialsEntity.toDomain() = ApiCredentials(
        id = id,
        displayName = displayName,
        provider = provider,
        modelId = modelId,
        baseUrl = baseUrl,
        isVision = isVision,
        credentialAlias = credentialAlias
    )
    
    private fun ApiModelConfigurationEntity.toDomain() = ApiModelConfiguration(
        id = id,
        apiCredentialsId = apiCredentialsId,
        displayName = displayName,
        maxTokens = maxTokens,
        contextWindow = contextWindow,
        temperature = temperature,
        topP = topP,
        topK = topK,
        minP = minP,
        frequencyPenalty = frequencyPenalty,
        presencePenalty = presencePenalty,
        systemPrompt = systemPrompt,
        reasoningEffort = ApiReasoningEffort.fromWireValue(reasoningEffort),
        customHeaders = ApiModelMapper.deserializeCustomHeaders(customHeaders)
    )
}
