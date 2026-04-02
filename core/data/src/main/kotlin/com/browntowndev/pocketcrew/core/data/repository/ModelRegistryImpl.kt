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
import kotlinx.coroutines.flow.map
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

    override suspend fun getRegisteredAsset(modelType: ModelType): LocalModelAsset? {
        val config = getRegisteredConfiguration(modelType) ?: return null
        return loadAsset(config.localModelId)
    }

    override suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration? {
        val defaultEntity = defaultModelsDao.getDefault(modelType)
        if (defaultEntity == null || defaultEntity.localConfigId == null) return null
        val configEntity = configsDao.getById(defaultEntity.localConfigId) ?: return null
        return entityToConfiguration(configEntity)
    }

    override suspend fun getRegisteredAssets(): List<LocalModelAsset> {
        return modelsDao.getAllCurrent().mapNotNull { loadAsset(it.id) }
    }

    override suspend fun getRegisteredConfigurations(): List<LocalModelConfiguration> {
        val defaults = defaultModelsDao.getAll()
        return defaults.mapNotNull { defaultEntity ->
            if (defaultEntity.localConfigId != null) {
                configsDao.getById(defaultEntity.localConfigId)?.let { entityToConfiguration(it) }
            } else null
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
                        visionCapable = asset.metadata.visionCapable,
                        modelStatus = status,
                        thinkingEnabled = asset.configurations.any { it.thinkingEnabled },
                        isVision = modelType == ModelType.VISION
                    )
                 )
            }

            var registeredConfigId: Long? = null
            for (config in asset.configurations) {
                // Determine the correct ID to use (if it already exists, use its ID so we update the correct row instead of inserting)
                val existingConfig = configsDao.getAllForAsset(modelId).find { it.displayName == config.displayName }
                val configIdToUse = existingConfig?.id ?: config.id
                
                var configId = configsDao.upsert(
                    LocalModelConfigurationEntity(
                        id = configIdToUse,
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
                        systemPrompt = config.systemPrompt,
                        isSystemPreset = config.isSystemPreset
                    )
                )
                
                // Room's @Upsert returns -1 when it falls back to an UPDATE on conflict
                if (configId == -1L && existingConfig != null) {
                    configId = existingConfig.id
                }
                
                if (registeredConfigId == null) registeredConfigId = configId
            }

            if (registeredConfigId == null && asset.configurations.isNotEmpty()) {
                registeredConfigId = configsDao.getAllForAsset(modelId).firstOrNull()?.id
            }

            if (registeredConfigId != null) {
                defaultModelsDao.upsert(DefaultModelEntity(modelType, registeredConfigId, null))
            }
        }
    }

    override suspend fun clearAll() {
        val defaults = defaultModelsDao.getAll()
        defaults.forEach { defaultModelsDao.delete(it.modelType) }
        val allModels = modelsDao.getAllCurrent()
        allModels.forEach { modelsDao.deleteById(it.id) }
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

        return result
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
                visionCapable = metadata.visionCapable,
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
                systemPrompt = config.systemPrompt,
                isSystemPreset = config.isSystemPreset
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
                sha256 = modelEntity.sha256,
                sizeInBytes = modelEntity.sizeInBytes,
                modelFileFormat = modelEntity.modelFileFormat,
                visionCapable = modelEntity.visionCapable
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
            systemPrompt = entity.systemPrompt ?: "",
            isSystemPreset = entity.isSystemPreset
        )
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
        // Returns LOCALMODEL rows that are CURRENT but have zero configurations.
        // These are models that were downloaded but soft-deleted (configs hard-deleted).
        return modelsDao.getSoftDeletedModels().mapNotNull { loadAsset(it.id) }
    }

    override suspend fun reuseModelForRedownload(modelId: Long, newAsset: LocalModelAsset): Long {
        // Reuse existing LocalModelEntity row for re-download.
        // The entity should already exist (soft-deleted state).
        // We update the metadata to reflect the new asset and create a new config.
        val existingEntity = modelsDao.getById(modelId)
            ?: throw IllegalStateException("Model $modelId not found for reuse")

        // Update the entity with the new asset metadata (same ID, new sha256/size/etc.)
        val updatedEntity = existingEntity.copy(
            modelFileFormat = newAsset.metadata.modelFileFormat,
            huggingFaceModelName = newAsset.metadata.huggingFaceModelName,
            remoteFilename = newAsset.metadata.remoteFileName,
            localFilename = newAsset.metadata.localFileName,
            sha256 = newAsset.metadata.sha256,
            sizeInBytes = newAsset.metadata.sizeInBytes,
            modelStatus = ModelStatus.CURRENT
        )
        modelsDao.upsert(updatedEntity)

        // Create new configuration(s) with isSystemPreset=true for each config in the new asset
        // Use the first config's modelType to determine which DefaultModelEntity to create/update
        val firstConfig = newAsset.configurations.firstOrNull()
            ?: throw IllegalStateException("Asset must have at least one configuration")

        val modelType = ModelType.entries.find {
            newAsset.metadata.huggingFaceModelName.contains(it.name, ignoreCase = true)
        } ?: ModelType.FAST

        // Determine which ModelType this asset represents based on DefaultModelEntity
        // Check if there's an existing DefaultModelEntity pointing to a config on this model
        val existingDefaults = defaultModelsDao.getAll()
        val existingDefault = existingDefaults.find { default ->
            val config = configsDao.getById(default.localConfigId ?: -1)
            config?.localModelId == modelId
        }
        val targetModelType = existingDefault?.modelType ?: modelType

        // Save the new configs and find/create the primary config
        var primaryConfigId: Long? = null
        for (config in newAsset.configurations) {
            val existingConfig = configsDao.getAllForAsset(modelId).find { it.displayName == config.displayName }
            val configIdToUse = existingConfig?.id ?: 0L
            
            var configId = configsDao.upsert(
                LocalModelConfigurationEntity(
                    id = configIdToUse,
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
                    systemPrompt = config.systemPrompt,
                    isSystemPreset = true // Always true for re-downloaded configs
                )
            )
            
            if (configId == -1L && existingConfig != null) {
                configId = existingConfig.id
            }
            
            if (primaryConfigId == null) {
                primaryConfigId = configId
            }
        }

        // Update or create DefaultModelEntity for this modelType
        if (primaryConfigId != null) {
            defaultModelsDao.upsert(
                DefaultModelEntity(
                    modelType = targetModelType,
                    localConfigId = primaryConfigId,
                    apiConfigId = null
                )
            )
        }

        return modelId
    }

    override suspend fun getAssetById(id: Long): LocalModelAsset? {
        return loadAsset(id)
    }
}
