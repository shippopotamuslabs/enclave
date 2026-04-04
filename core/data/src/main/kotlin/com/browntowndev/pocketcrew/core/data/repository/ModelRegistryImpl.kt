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
import com.browntowndev.pocketcrew.domain.model.config.SlotResolvedLocalModel
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
        return loadAsset(config.localModelId, preferredConfigId = config.id)
    }

    override suspend fun upsertLocalAsset(asset: LocalModelAsset): Long {
        val existingModel = modelsDao.getBySha256(asset.metadata.sha256)
        val entity = LocalModelEntity(
            id = existingModel?.id ?: 0L,
            modelFileFormat = asset.metadata.modelFileFormat,
            huggingFaceModelName = asset.metadata.huggingFaceModelName,
            remoteFilename = asset.metadata.remoteFileName,
            localFilename = asset.metadata.localFileName,
            sha256 = asset.metadata.sha256,
            sizeInBytes = asset.metadata.sizeInBytes,
            visionCapable = asset.metadata.visionCapable,
            thinkingEnabled = asset.configurations.any { it.thinkingEnabled },
            isVision = asset.metadata.visionCapable
        )
        val insertedId = modelsDao.upsert(entity)
        return if (insertedId > 0) insertedId else existingModel?.id ?: 0L
    }

    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): Long {
        require(config.localModelId > 0) {
            "LocalModelConfiguration.localModelId must be set before persisting a config"
        }
        val entity = LocalModelConfigurationEntity(
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
        val insertedId = configsDao.upsert(entity)
        return if (insertedId > 0) insertedId else config.id
    }

    override suspend fun setDefaultLocalConfig(modelType: ModelType, configId: Long) {
        defaultModelsDao.upsert(DefaultModelEntity(modelType, configId, null))
    }

    override suspend fun activateLocalModel(modelType: ModelType, asset: LocalModelAsset): SlotResolvedLocalModel {
        return transactionProvider.runInTransaction {
            val assetId = upsertLocalAsset(asset)
            val primaryConfig = asset.configurations.firstOrNull()
                ?: throw IllegalArgumentException("Asset must contain at least one configuration")
            val existingSelection = getRegisteredSelection(modelType)
            val reusableConfigId = when {
                existingSelection?.asset?.metadata?.id == assetId ->
                    existingSelection.selectedConfig.id
                else -> findMatchingConfigId(assetId, primaryConfig)
            }
            val configToPersist = primaryConfig.copy(
                id = reusableConfigId ?: 0L,
                localModelId = assetId
            )
            logger.debug(
                "ModelRegistry",
                "activateLocalModel($modelType): assetId=$assetId sha=${asset.metadata.sha256} " +
                    "file=${asset.metadata.localFileName} preset=${primaryConfig.displayName} " +
                    "thinking=${primaryConfig.thinkingEnabled} reusedConfigId=${reusableConfigId ?: 0L}"
            )
            val configId = upsertLocalConfiguration(configToPersist)
            setDefaultLocalConfig(modelType, configId)
            getRegisteredSelection(modelType)
                ?: throw IllegalStateException("Failed to resolve activated model for $modelType")
        }
    }

    override suspend fun getRegisteredSelection(modelType: ModelType): SlotResolvedLocalModel? {
        val defaultEntity = defaultModelsDao.getDefault(modelType)
        if (defaultEntity == null || defaultEntity.localConfigId == null) return null
        
        val configEntity = configsDao.getById(defaultEntity.localConfigId) ?: return null
        val modelEntity = modelsDao.getById(configEntity.localModelId) ?: return null
        
        val asset = loadAsset(modelEntity.id, preferredConfigId = configEntity.id) ?: return null
        val selectedConfig = entityToConfiguration(configEntity)
        
        return SlotResolvedLocalModel(modelType, asset, selectedConfig)
    }

    override suspend fun getRegisteredConfiguration(modelType: ModelType): LocalModelConfiguration? {
        val defaultEntity = defaultModelsDao.getDefault(modelType)
        if (defaultEntity == null || defaultEntity.localConfigId == null) return null
        val configEntity = configsDao.getById(defaultEntity.localConfigId) ?: return null
        return entityToConfiguration(configEntity)
    }

    override suspend fun getRegisteredAssets(): List<LocalModelAsset> {
        return modelsDao.getAllActive().mapNotNull { loadAsset(it.id) }
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
                if (configEntity != null) {
                    loadAsset(configEntity.localModelId, preferredConfigId = configEntity.id)
                } else {
                    null
                }
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
        return modelsDao.observeAllActive().map { entities ->
            entities.mapNotNull { loadAsset(it.id) }
        }
    }

    override suspend fun clearAll() {
        val defaults = defaultModelsDao.getAll()
        defaults.forEach { defaultModelsDao.delete(it.modelType) }
        val allModels = modelsDao.getAll()
        allModels.forEach { modelsDao.deleteById(it.id) }
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
                thinkingEnabled = false,
                isVision = false
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

    private suspend fun loadAsset(localModelId: Long, preferredConfigId: Long? = null): LocalModelAsset? {
        val entity = modelsDao.getById(localModelId) ?: return null
        val configs = configsDao.getAllForAsset(localModelId)
        val orderedConfigEntities = if (preferredConfigId == null) {
            configs
        } else {
            configs.sortedWith(
                compareByDescending<LocalModelConfigurationEntity> { it.id == preferredConfigId }
                    .thenBy { it.id }
            )
        }
        return LocalModelAsset(
            metadata = LocalModelMetadata(
                id = entity.id,
                huggingFaceModelName = entity.huggingFaceModelName,
                remoteFileName = entity.remoteFilename,
                localFileName = entity.localFilename,
                sha256 = entity.sha256,
                sizeInBytes = entity.sizeInBytes,
                modelFileFormat = entity.modelFileFormat,
                visionCapable = entity.visionCapable
            ),
            configurations = orderedConfigEntities.map { entityToConfiguration(it) }
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

    private suspend fun findMatchingConfigId(
        assetId: Long,
        desiredConfig: LocalModelConfiguration
    ): Long? {
        return configsDao.getAllForAsset(assetId)
            .firstOrNull { entity ->
                entity.displayName == desiredConfig.displayName &&
                    entity.temperature == desiredConfig.temperature &&
                    entity.topK == (desiredConfig.topK ?: 40) &&
                    entity.topP == desiredConfig.topP &&
                    entity.minP == desiredConfig.minP &&
                    entity.repetitionPenalty == desiredConfig.repetitionPenalty &&
                    entity.maxTokens == desiredConfig.maxTokens &&
                    entity.contextWindow == desiredConfig.contextWindow &&
                    entity.thinkingEnabled == desiredConfig.thinkingEnabled &&
                    (entity.systemPrompt ?: "") == desiredConfig.systemPrompt &&
                    entity.isSystemPreset == desiredConfig.isSystemPreset
            }
            ?.id
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
        // Returns LOCALMODEL rows that have zero configurations.
        // These are models that were downloaded but soft-deleted (configs hard-deleted).
        return modelsDao.getSoftDeletedModels().mapNotNull { loadAsset(it.id) }
    }

    override suspend fun deleteModel(modelId: Long, replacementLocalConfigId: Long?, replacementApiConfigId: Long?): Result<Unit> {
        return runCatching {
            require((replacementLocalConfigId == null) || (replacementApiConfigId == null)) {
                "Only one replacement config source may be provided"
            }

            transactionProvider.runInTransaction {
                val modelConfigIds = configsDao.getAllForAsset(modelId).map { it.id }.toSet()
                if (modelConfigIds.isEmpty()) return@runInTransaction

                val defaultsNeedingUpdate = defaultModelsDao.getAll().filter { default ->
                    default.localConfigId != null && default.localConfigId in modelConfigIds
                }

                if (defaultsNeedingUpdate.isNotEmpty() &&
                    replacementLocalConfigId == null &&
                    replacementApiConfigId == null
                ) {
                    throw IllegalStateException("Default assignments must be reassigned before deleting model $modelId")
                }

                defaultsNeedingUpdate.forEach { default ->
                    defaultModelsDao.upsert(
                        DefaultModelEntity(
                            modelType = default.modelType,
                            localConfigId = replacementLocalConfigId,
                            apiConfigId = replacementApiConfigId
                        )
                    )
                }

                configsDao.deleteAllForAsset(modelId)
            }
        }
    }

    override suspend fun getAssetById(id: Long): LocalModelAsset? {
        return loadAsset(id)
    }
}
