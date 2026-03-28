package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelsDao
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of ApiModelRepositoryPort.
 * Persists entity to Room and key to EncryptedSharedPreferences.
 */
class ApiModelRepositoryImpl @Inject constructor(
    private val apiModelsDao: ApiModelsDao,
    private val apiKeyManager: ApiKeyManager,
) : ApiModelRepositoryPort {

    override fun observeAll(): Flow<List<ApiModelConfig>> {
        return apiModelsDao.observeAll().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getAll(): List<ApiModelConfig> {
        return apiModelsDao.getAll().map { it.toDomain() }
    }

    override suspend fun getById(id: Long): ApiModelConfig? {
        return apiModelsDao.getById(id)?.toDomain()
    }

    override suspend fun save(config: ApiModelConfig, apiKey: String): Long {
        val entityId = apiModelsDao.upsert(config.toEntity())
        apiKeyManager.save(entityId, apiKey)
        return entityId
    }

    override suspend fun delete(id: Long) {
        apiModelsDao.deleteById(id)
        apiKeyManager.delete(id)
    }
}

internal fun ApiModelEntity.toDomain() = ApiModelConfig(
    id = id,
    displayName = displayName,
    provider = provider,
    modelId = modelId,
    baseUrl = baseUrl,
    isVision = isVision,
    maxTokens = maxTokens,
    contextWindow = contextWindow,
    temperature = temperature,
    topP = topP,
    topK = topK,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    stopSequences = if (stopSequences.isEmpty()) emptyList() else stopSequences.split(";;;"),
)

internal fun ApiModelConfig.toEntity() = ApiModelEntity(
    id = id,
    displayName = displayName,
    provider = provider,
    modelId = modelId,
    baseUrl = baseUrl,
    isVision = isVision,
    maxTokens = maxTokens,
    contextWindow = contextWindow,
    temperature = temperature,
    topP = topP,
    topK = topK,
    frequencyPenalty = frequencyPenalty,
    presencePenalty = presencePenalty,
    stopSequences = stopSequences.joinToString(";;;"),
    updatedAt = System.currentTimeMillis()
)
