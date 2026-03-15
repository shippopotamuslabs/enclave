package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.ModelsDao
import com.browntowndev.pocketcrew.data.local.ModelEntity
import com.browntowndev.pocketcrew.domain.model.config.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistryImpl @Inject constructor(
    private val modelsDao: ModelsDao,
    private val logger: LoggingPort
) : ModelRegistryPort {

    private val mutex = Mutex()

    @Volatile
    private var cache: Map<ModelType, ModelConfiguration> = emptyMap()

    private fun isCacheInitialized(): Boolean = cache.isNotEmpty()

    private fun getFromCache(modelType: ModelType): ModelConfiguration? = cache[modelType]

    private fun getAllFromCache(): List<ModelConfiguration> = cache.values.toList()

    private suspend fun updateCache(config: ModelConfiguration) {
        mutex.withLock {
            cache = cache.toMutableMap().apply {
                put(config.modelType, config)
            }
        }
        logger.debug("ModelRegistry", "Updated cache for ${config.modelType}: ${config.metadata.displayName}")
    }

    private suspend fun clearCache() {
        mutex.withLock {
            cache = emptyMap()
        }
        logger.debug("ModelRegistry", "Cache cleared")
    }

    override suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration? {
        // Check cache first
        getFromCache(modelType)?.let { return it }

        // Cache miss - load from DAO (only CURRENT)
        val entity = modelsDao.getModelEntity(modelType) ?: return null
        val config = entityToModelConfiguration(entity)

        // Update cache
        updateCache(config)
        return config
    }

    override fun getRegisteredModelSync(modelType: ModelType): ModelConfiguration? {
        // Just return from cache - assumes cache has been initialized
        return getFromCache(modelType)
    }

    override suspend fun getRegisteredModels(): List<ModelConfiguration> {
        // If cache is empty, populate from DAO (only CURRENT)
        if (!isCacheInitialized()) {
            val entities = modelsDao.getAll()
            val configs = entities.map { entityToModelConfiguration(it) }
            // Initialize cache with all configs
            configs.forEach { updateCache(it) }
        }
        return getAllFromCache()
    }

    override fun getRegisteredModelsSync(): List<ModelConfiguration> {
        // Just return from cache - assumes cache has been initialized
        return getAllFromCache()
    }

    override fun observeRegisteredModels(): Flow<Map<ModelType, String>> {
        return modelsDao.observeAll().map { entities ->
            entities.associate {
                ModelType.valueOf(it.modelType.name) to it.displayName
            }
        }
    }

    override fun observeModel(modelType: ModelType): Flow<ModelConfiguration?> {
        return modelsDao.observeModelEntity(modelType).map { entity ->
            entity?.let { entityToModelConfiguration(it) }
        }
    }

    override suspend fun setRegisteredModel(
        config: ModelConfiguration,
        status: ModelStatus,
        markExistingAsOld: Boolean
    ) {
        // First, check if there's an existing CURRENT model for this type
        val existingCurrent = modelsDao.getModelEntityByStatus(config.modelType, ModelStatus.CURRENT)

        // If we're setting a new CURRENT and there was an existing CURRENT, optionally update it to OLD
        if (status == ModelStatus.CURRENT && existingCurrent != null && markExistingAsOld) {
            modelsDao.upsert(
                existingCurrent.copy(modelStatus = ModelStatus.OLD)
            )
            logger.debug("ModelRegistry", "Marked existing model as OLD: ${existingCurrent.displayName}")
        }

        // Insert or update the new model with the specified status
        logger.debug("ModelRegistry", "Saving model ${config.modelType} with thinkingEnabled=${config.tunings.thinkingEnabled}")
        modelsDao.upsert(
            ModelEntity(
                modelType = config.modelType,
                modelStatus = status,
                remoteFilename = config.metadata.localFileName,
                huggingFaceModelName = config.metadata.huggingFaceModelName,
                displayName = config.metadata.displayName,
                modelFileFormat = config.metadata.modelFileFormat,
                sha256 = config.metadata.sha256,
                sizeInBytes = config.metadata.sizeInBytes,
                temperature = config.tunings.temperature,
                topK = config.tunings.topK,
                topP = config.tunings.topP,
                minP = config.tunings.minP,
                maxTokens = config.tunings.maxTokens,
                contextWindow = config.tunings.contextWindow,
                thinkingEnabled = config.tunings.thinkingEnabled,
                systemPrompt = config.persona.systemPrompt,
                repetitionPenalty = config.tunings.repetitionPenalty
            )
        )
        // Update cache after DB update
        updateCache(config)
    }

    override suspend fun clearAll() {
        modelsDao.deleteAll()
        clearCache()
    }

    override suspend fun clearOld() {
        modelsDao.deleteAllOld()
        logger.debug("ModelRegistry", "Cleared all OLD entries")
    }

    override suspend fun getModelsPreferringOld(): List<ModelConfiguration> {
        // For each ModelType, prefer OLD if it exists, otherwise use CURRENT
        val result = mutableListOf<ModelConfiguration>()
        for (modelType in ModelType.entries) {
            val oldEntity = modelsDao.getModelEntityByStatus(modelType, ModelStatus.OLD)
            if (oldEntity != null) {
                result.add(entityToModelConfiguration(oldEntity))
            } else {
                // Fall back to CURRENT if no OLD exists
                val currentEntity = modelsDao.getModelEntityByStatus(modelType, ModelStatus.CURRENT)
                if (currentEntity != null) {
                    result.add(entityToModelConfiguration(currentEntity))
                }
            }
        }
        return result
    }

    private fun entityToModelConfiguration(entity: ModelEntity): ModelConfiguration {
        return ModelConfiguration(
            modelType = entity.modelType,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = entity.huggingFaceModelName,
                remoteFileName = entity.remoteFilename,
                localFileName = entity.remoteFilename, // Save as-is
                displayName = entity.displayName,
                sha256 = entity.sha256,
                sizeInBytes = entity.sizeInBytes,
                modelFileFormat = entity.modelFileFormat
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = entity.temperature,
                topK = entity.topK,
                topP = entity.topP,
                minP = entity.minP,
                repetitionPenalty = entity.repetitionPenalty,
                maxTokens = entity.maxTokens,
                contextWindow = entity.contextWindow,
                thinkingEnabled = entity.thinkingEnabled
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = entity.systemPrompt ?: ""
            )
        )
    }
}
