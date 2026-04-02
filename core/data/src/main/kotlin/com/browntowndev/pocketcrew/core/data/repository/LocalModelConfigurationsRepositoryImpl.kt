package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelConfigurationsRepositoryPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelConfigurationsRepositoryImpl @Inject constructor(
    private val configsDao: LocalModelConfigurationsDao
) : LocalModelConfigurationsRepositoryPort {

    override suspend fun getAllForAsset(localModelId: Long): List<LocalModelConfiguration> {
        return configsDao.getAllForAsset(localModelId).map { it.toDomain() }
    }

    override suspend fun deleteAllForAsset(localModelId: Long) {
        configsDao.deleteAllForAsset(localModelId)
    }

    override suspend fun deleteById(id: Long) {
        configsDao.deleteById(id)
    }

    override suspend fun getById(id: Long): LocalModelConfiguration? {
        return configsDao.getById(id)?.toDomain()
    }

    override suspend fun saveConfiguration(config: LocalModelConfiguration): Long {
        return configsDao.upsert(config.toEntity())
    }

    private fun LocalModelConfigurationEntity.toDomain(): LocalModelConfiguration {
        return LocalModelConfiguration(
            id = id,
            localModelId = localModelId,
            displayName = displayName,
            temperature = temperature,
            topK = topK,
            topP = topP,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
            thinkingEnabled = thinkingEnabled,
            systemPrompt = systemPrompt ?: "",
            isSystemPreset = isSystemPreset
        )
    }

    private fun LocalModelConfiguration.toEntity(): LocalModelConfigurationEntity {
        return LocalModelConfigurationEntity(
            id = id,
            localModelId = localModelId,
            displayName = displayName,
            temperature = temperature,
            topK = topK ?: 40,
            topP = topP,
            minP = minP,
            repetitionPenalty = repetitionPenalty,
            maxTokens = maxTokens,
            contextWindow = contextWindow,
            thinkingEnabled = thinkingEnabled,
            systemPrompt = systemPrompt,
            isSystemPreset = isSystemPreset
        )
    }
}
