package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistryImpl @Inject constructor(
    private val modelsDao: LocalModelsDao,
    private val configsDao: LocalModelConfigurationsDao,
    private val defaultModelsDao: DefaultModelsDao,
    private val transactionProvider: TransactionProvider,
    private val logger: LoggingPort
) : ModelRegistryPort {

    private val mutex = Mutex()

    @Volatile
    private var cache: Map<ModelType, Pair<LocalModelAsset, LocalModelConfiguration>> = emptyMap()

    private fun isCacheInitialized(): Boolean = cache.isNotEmpty()

    private fun getAssetFromCache(modelType: ModelType): LocalModelAsset? = cache[modelType]?.first

    private fun getConfigurationFromCache(modelType: ModelType): LocalModelConfiguration? = cache[modelType]?.second

    private fun getAllAssetsFromCache(): List<LocalModelAsset> = cache.values.map { it.first }.distinctBy { it.metadata.id }

    private fun getAllConfigurationsFromCache(): List<LocalModelConfiguration> = cache.values.map { it.second }

    private suspend fun updateCache(modelType: ModelType, asset: LocalModelAsset, config: LocalModelConfiguration) {
        mutex.withLock {
            cache = cache.toMutableMap().apply {
                put(modelType, asset to config)
            }
        }
        logger.debug("ModelRegistry", "Updated cache for $modelType: ${config.displayName}")
    }

    private suspend fun clearCache() {
        mutex.withLock {
            cache = emptyMap()
        }
        logger.debug("ModelRegistry", "Cache cleared")
    }

    override suspend fun getRegisteredAsset(modelType: ModelType): LocalModelAsset? {
        getAssetFromCache(modelType)?.let { return it }
        val config = getRegisteredConfiguration(modelType) ?: return null
        val asset = loadAsset(config.localModelId) ?: return null
        updateCache(modelType, asset, config)
        return asset
    }

    override suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration? {
        getConfigurationFromCache(modelType)?.let { return it }
        val defaultEntity = defaultModelsDao.getDefault(modelType)
        if (defaultEntity == null || defaultEntity.localConfigId == null) return null
        val configEntity = configsDao.getById(defaultEntity.localConfigId) ?: return null
        val config = entityToConfiguration(configEntity)
        val asset = loadAsset(config.localModelId)
        if (asset != null) updateCache(modelType, asset, config)
        return config
    }

    override fun getRegisteredAssetSync(modelType: ModelType): LocalModelAsset? = getAssetFromCache(modelType)

    override fun getRegisteredConfigurationSync(modelType: ModelType): LocalModelConfiguration? = getConfigurationFromCache(modelType)

    override suspend fun getRegisteredAssets(): List<LocalModelAsset> {
        if (!isCacheInitialized()) refreshCache()
        return getAllAssetsFromCache()
    }

    override fun getRegisteredAssetsSync(): List<LocalModelAsset> = getAllAssetsFromCache()

    override suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration> {
        if (!isCacheInitialized()) refreshCache()
        return getAllConfigurationsFromCache()
    }

    override fun getRegisteredConfigurationsSync(): List<LocalModelConfiguration> = getAllConfigurationsFromCache()

    private suspend fun refreshCache() {
        val defaults = defaultModelsDao.getAll()
        defaults.forEach { defaultEntity ->
            if (defaultEntity.localConfigId != null) {
                val configEntity = configsDao.getById(defaultEntity.localConfigId)
                if (configEntity != null) {
                    val asset = loadAsset(configEntity.localModelId)
                    if (asset != null) {
                        updateCache(defaultEntity.modelType, asset, entityToConfiguration(configEntity))
                    }
                }
            }
        }
    }

    override fun observeAsset(modelType: ModelType): Flow<LocalModelAsset?> {
        return defaultModelsDao.observeAll().map { defaults ->
            val defaultEntity = defaults.find { it.modelType == modelType }
            if (defaultEntity != null && defaultEntity.localConfigId != null) {
                val configEntity = configsDao.getById(defaultEntity.localConfigId)
                if (configEntity != null) loadAsset(configEntity.localModelId) else null
            } else null
        }
    }

    override fun observeConfiguration(modelType: ModelType): Flow<LocalModelConfiguration?> {
        return defaultModelsDao.observeAll().map { defaults ->
            val defaultEntity = defaults.find { it.modelType == modelType }
            if (defaultEntity != null && defaultEntity.localConfigId != null) {
                val configEntity = configsDao.getById(defaultEntity.localConfigId)
                if (configEntity != null) entityToConfiguration(configEntity) else null
            } else null
        }
    }

    override fun observeAssets(): Flow<List<LocalModelAsset>> {
        return modelsDao.observeAllCurrent().map { entities ->
            entities.mapNotNull { loadAsset(it.id) }
        }
    }

    override suspend fun setRegisteredModel(
        modelType: ModelType,
        asset: LocalModelAsset,
        status: ModelStatus,
        markExistingAsOld: Boolean
    ) {
        transactionProvider.runInTransaction {
            val existingModel = modelsDao.getBySha256(asset.metadata.sha256)
            val modelId = if (existingModel != null) {
                 if (status == ModelStatus.CURRENT && markExistingAsOld && existingModel.modelStatus == ModelStatus.CURRENT) {
                     modelsDao.upsert(existingModel.copy(modelStatus = ModelStatus.OLD))
                 } else if (existingModel.modelStatus != status) {
                     modelsDao.upsert(existingModel.copy(modelStatus = status))
                 }
                 existingModel.id
            } else {
                 modelsDao.upsert(
                    LocalModelEntity(
                        modelFileFormat = asset.metadata.modelFileFormat,
                        huggingFaceModelName = asset.metadata.huggingFaceModelName,
                        remoteFilename = asset.metadata.remoteFileName,
                        localFilename = asset.metadata.localFileName,
                        sha256 = asset.metadata.sha256,
                        sizeInBytes = asset.metadata.sizeInBytes,
                        displayName = asset.metadata.displayName,
                        modelStatus = status,
                        thinkingEnabled = asset.configurations.any { it.thinkingEnabled },
                        isVision = modelType == ModelType.VISION
                    )
                 )
            }

            var registeredConfigId: Long? = null
            for (config in asset.configurations) {
                val configId = configsDao.upsert(
                    LocalModelConfigurationEntity(
                        id = config.id,
                        localModelId = modelId,
                        displayName = config.displayName,
                        temperature = config.temperature,
                        topK = config.topK ?: 40,
                        topP = config.topP,
                        minP = config.minP,
                        repetitionPenalty = config.repetitionPenalty,
                        maxTokens = config.maxTokens,
                        contextWindow = config.contextWindow,
                        thinkingEnabled = config.thinkingEnabled,
                        systemPrompt = config.systemPrompt
                    )
                )
                if (config.displayName == asset.metadata.displayName) registeredConfigId = configId
            }

            if (registeredConfigId == null && asset.configurations.isNotEmpty()) {
                registeredConfigId = configsDao.getAllForAsset(modelId).firstOrNull()?.id
            }

            if (registeredConfigId != null) {
                defaultModelsDao.upsert(DefaultModelEntity(modelType, registeredConfigId, null))
                val updatedAsset = loadAsset(modelId)
                val updatedConfig = configsDao.getById(registeredConfigId)?.let { entityToConfiguration(it) }
                if (updatedAsset != null && updatedConfig != null) updateCache(modelType, updatedAsset, updatedConfig)
            }
        }
    }

    override suspend fun clearAll() {
        val defaults = defaultModelsDao.getAll()
        defaults.forEach { defaultModelsDao.delete(it.modelType) }
        val allModels = modelsDao.getAllCurrent()
        allModels.forEach { modelsDao.deleteById(it.id) }
        clearCache()
    }

    override suspend fun clearOld() {
        logger.debug("ModelRegistry", "Cleared all OLD entries")
    }

    override suspend fun getAssetsPreferringOld(): Map<ModelType, LocalModelAsset> {
        val result = mutableMapOf<ModelType, LocalModelAsset>()
        val defaults = defaultModelsDao.getAll()
        for (default in defaults) {
            if (default.localConfigId != null) {
                val configEntity = configsDao.getById(default.localConfigId)
                if (configEntity != null) {
                    val currentModel = modelsDao.getById(configEntity.localModelId)
                    if (currentModel != null) {
                        val asset = loadAsset(currentModel.id)
                        if (asset != null) result[default.modelType] = asset
                    }
                }
            }
        }
    }

    override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): Long {
        return modelsDao.upsert(
            LocalModelEntity(
                id = metadata.id,
                modelFileFormat = metadata.modelFileFormat,
                huggingFaceModelName = metadata.huggingFaceModelName,
                remoteFilename = metadata.remoteFileName,
                localFilename = metadata.localFileName,
                sha256 = metadata.sha256,
                sizeInBytes = metadata.sizeInBytes,
                displayName = metadata.displayName,
                modelStatus = ModelStatus.CURRENT
            )
        )
    }

    override suspend fun deleteLocalModelMetadata(id: Long) {
        modelsDao.deleteById(id)
    }

    override suspend fun saveConfiguration(config: LocalModelConfiguration): Long {
        return configsDao.upsert(
            LocalModelConfigurationEntity(
                id = config.id,
                localModelId = config.localModelId,
                displayName = config.displayName,
                temperature = config.temperature,
                topK = config.topK ?: 40,
                topP = config.topP,
                minP = config.minP,
                repetitionPenalty = config.repetitionPenalty,
                maxTokens = config.maxTokens,
                contextWindow = config.contextWindow,
                thinkingEnabled = config.thinkingEnabled,
                systemPrompt = config.systemPrompt
            )
        )
    }

    override suspend fun deleteConfiguration(id: Long) {
        configsDao.deleteById(id)
    }

    private suspend fun loadAsset(modelId: Long): LocalModelAsset? {
        val modelEntity = modelsDao.getById(modelId) ?: return null
        val configEntities = configsDao.getAllForAsset(modelId)
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = modelEntity.id,
                huggingFaceModelName = modelEntity.huggingFaceModelName,
                remoteFileName = modelEntity.remoteFilename,
                localFileName = modelEntity.localFilename,
                displayName = modelEntity.displayName,
                sha256 = modelEntity.sha256,
                sizeInBytes = modelEntity.sizeInBytes,
                modelFileFormat = modelEntity.modelFileFormat
            ),
            configurations = configEntities.map { entityToConfiguration(it) }
        )
    }

    private fun entityToConfiguration(entity: LocalModelConfigurationEntity): LocalModelConfiguration {
        return LocalModelConfiguration(
            id = entity.id,
            localModelId = entity.localModelId,
            displayName = entity.displayName,
            temperature = entity.temperature,
            topK = entity.topK,
            topP = entity.topP,
            minP = entity.minP,
            repetitionPenalty = entity.repetitionPenalty,
            maxTokens = entity.maxTokens,
            contextWindow = entity.contextWindow,
            thinkingEnabled = entity.thinkingEnabled,
            systemPrompt = entity.systemPrompt ?: ""
        )
    }
}