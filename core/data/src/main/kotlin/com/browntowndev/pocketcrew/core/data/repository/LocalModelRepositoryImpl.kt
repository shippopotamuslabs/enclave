package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalModelRepositoryImpl @Inject constructor(
    private val modelsDao: LocalModelsDao,
    private val configsDao: LocalModelConfigurationsDao,
    private val defaultModelsDao: DefaultModelsDao,
    private val transactionProvider: TransactionProvider,
    private val logger: LoggingPort
) : LocalModelRepositoryPort {

    @OptIn(ExperimentalUuidApi::class)
    private fun generateGuid(): LocalModelConfigurationId = LocalModelConfigurationId(Uuid.random().toString())

    override suspend fun getAllLocalAssets(): List<LocalModelAsset> {
        return modelsDao.getAllActive().mapNotNull { loadAsset(it.id) }
    }

    override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> {
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

    override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId {
        require(config.localModelId > 0) {
            "LocalModelConfiguration.localModelId must be set before persisting a config"
        }
        // Use existing configId if provided (from remote config), otherwise generate a new GUID
        val configId = if (config.id.value.isNotEmpty()) {
            config.id
        } else {
            generateGuid()
        }
        val entity = LocalModelConfigurationEntity(
            id = configId,
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
        configsDao.upsert(entity)
        return configId
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

    override suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId {
        return upsertLocalConfiguration(config)
    }

    override suspend fun deleteConfiguration(id: LocalModelConfigurationId) {
        configsDao.deleteById(id)
    }

    override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? {
        return configsDao.getById(id)?.let { entityToConfiguration(it) }
    }

    override suspend fun getAllConfigurationsForAsset(localModelId: Long): List<LocalModelConfiguration> {
        return configsDao.getAllForAsset(localModelId).map { entityToConfiguration(it) }
    }

    override suspend fun deleteAllConfigurationsForAsset(localModelId: Long) {
        configsDao.deleteAllForAsset(localModelId)
    }

    override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
        // Returns LOCALMODEL rows that have zero configurations.
        // These are models that were downloaded but soft-deleted (configs hard-deleted).
        return modelsDao.getSoftDeletedModels().mapNotNull { loadAsset(it.id) }
    }

    override suspend fun getAssetById(id: Long): LocalModelAsset? {
        return loadAsset(id)
    }

    override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? {
        val config = configsDao.getById(configId) ?: return null
        return loadAsset(config.localModelId, preferredConfigId = configId)
    }

    private suspend fun loadAsset(localModelId: Long, preferredConfigId: LocalModelConfigurationId? = null): LocalModelAsset? {
        val entity = modelsDao.getById(localModelId) ?: return null
        val configs = configsDao.getAllForAsset(localModelId)
        val orderedConfigEntities = if (preferredConfigId == null) {
            configs
        } else {
            configs.sortedWith(
                compareByDescending<LocalModelConfigurationEntity> { it.id == preferredConfigId }
                    .thenBy { it.id.value }
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
}
