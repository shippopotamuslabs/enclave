package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelEntity
import com.browntowndev.pocketcrew.core.data.local.ApiModelsDao
import com.browntowndev.pocketcrew.core.data.security.ApiKeyManager
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfig
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import org.json.JSONArray
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implementation of ApiModelRepositoryPort.
 * Persists entity to Room and key to EncryptedSharedPreferences.
 */
@Singleton
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

    override suspend fun save(config: ApiModelConfig, apiKey: String): Long = withContext(Dispatchers.IO) {
        val entityId = apiModelsDao.upsert(config.toEntity())
        if (apiKey.isNotBlank()) {
            apiKeyManager.save(entityId, apiKey)
        }
        entityId
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        apiKeyManager.delete(id) // Delete key first to prevent orphaned keys if DB write fails
        apiModelsDao.deleteById(id)
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
    stopSequences = parseStopSequences(stopSequences),
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
    stopSequences = serializeStopSequences(stopSequences),
    updatedAt = System.currentTimeMillis()
)

private fun parseStopSequences(jsonString: String): List<String> {
    if (jsonString.isBlank()) return emptyList()
    return try {
        val array = JSONArray(jsonString)
        List(array.length()) { array.getString(it) }
    } catch (e: Exception) {
        // Fallback for legacy format if any
        if (jsonString.contains(";;;")) jsonString.split(";;;") else emptyList()
    }
}

private fun serializeStopSequences(sequences: List<String>): String {
    if (sequences.isEmpty()) return ""
    return JSONArray(sequences).toString()
}
