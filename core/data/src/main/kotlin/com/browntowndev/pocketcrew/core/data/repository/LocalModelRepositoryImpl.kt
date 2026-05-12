package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Singleton
class LocalModelRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val modelsDao: LocalModelsDao,
        private val configsDao: LocalModelConfigurationsDao,
        private val defaultModelsDao: DefaultModelsDao,
        private val transactionProvider: TransactionProvider,
    ) : LocalModelRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        @OptIn(ExperimentalUuidApi::class)
        private fun generateConfigGuid(): LocalModelConfigurationId = LocalModelConfigurationId(Uuid.random().toString())

        @OptIn(ExperimentalUuidApi::class)
        private fun generateModelGuid(): LocalModelId = LocalModelId(Uuid.random().toString())

        override suspend fun getAllLocalAssets(): List<LocalModelAsset> = modelsDao.getAllActive().mapNotNull { loadAsset(it.id) }

        override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> =
            modelsDao.observeAllActive().map { entities ->
                entities.mapNotNull { loadAsset(it.id) }
            }

        override suspend fun clearAll() {
            val defaults = defaultModelsDao.getAll()
            defaults.forEach { defaultModelsDao.softDeleteById(it.modelType) }
            val allModels = modelsDao.getAll()
            allModels.forEach { modelsDao.softDeleteById(it.id) }
        }

        override suspend fun upsertLocalAsset(asset: LocalModelAsset): LocalModelId {
            val existingModel = modelsDao.getBySha256(asset.metadata.sha256)
            val entity =
                LocalModelEntity(
                    id = existingModel?.id ?: generateModelGuid(),
                    modelFileFormat = asset.metadata.modelFileFormat,
                    huggingFaceModelName = asset.metadata.huggingFaceModelName,
                    remoteFilename = asset.metadata.remoteFileName,
                    localFilename = asset.metadata.localFileName,
                    sha256 = asset.metadata.sha256,
                    sizeInBytes = asset.metadata.sizeInBytes,
                    isMultimodal = asset.metadata.isMultimodal,
                    mmprojRemoteFilename = asset.metadata.mmprojRemoteFileName,
                    mmprojLocalFilename = asset.metadata.mmprojLocalFileName,
                    mmprojSha256 = asset.metadata.mmprojSha256,
                    mmprojSizeInBytes = asset.metadata.mmprojSizeInBytes,
                    pendingSync = 1,
                )
            modelsDao.upsert(entity)
            triggerSync()
            return entity.id
        }

        override suspend fun upsertLocalConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId {
            val existingConfig = configsDao.getByModelIdAndName(config.localModelId, config.displayName)
            val configId =
                when {
                    config.id.value.isNotEmpty() -> config.id
                    existingConfig != null -> existingConfig.id
                    else -> generateConfigGuid()
                }
            val entity =
                LocalModelConfigurationEntity(
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
                    isSystemPreset = config.isSystemPreset,
                    pendingSync = 1,
                )
            configsDao.upsert(entity)
            triggerSync()
            return configId
        }

        override suspend fun getAssetById(id: LocalModelId): LocalModelAsset? = loadAsset(id)

        override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? =
            configsDao.getById(configId)?.let {
                loadAsset(it.localModelId)
            }

        override suspend fun saveLocalModelMetadata(metadata: LocalModelMetadata): LocalModelId {
            val assignedId = if (metadata.id.value.isEmpty()) generateModelGuid() else metadata.id
            val entity =
                LocalModelEntity(
                    id = assignedId,
                    modelFileFormat = metadata.modelFileFormat,
                    huggingFaceModelName = metadata.huggingFaceModelName,
                    remoteFilename = metadata.remoteFileName,
                    localFilename = metadata.localFileName,
                    sha256 = metadata.sha256,
                    sizeInBytes = metadata.sizeInBytes,
                    isMultimodal = metadata.isMultimodal,
                    mmprojRemoteFilename = metadata.mmprojRemoteFileName,
                    mmprojLocalFilename = metadata.mmprojLocalFileName,
                    mmprojSha256 = metadata.mmprojSha256,
                    mmprojSizeInBytes = metadata.mmprojSizeInBytes,
                    pendingSync = 1,
                )
            modelsDao.upsert(entity)
            triggerSync()
            return entity.id
        }

        override suspend fun deleteLocalModelMetadata(id: LocalModelId) {
            transactionProvider.runInTransaction {
                configsDao.deleteAllForAsset(id)
                modelsDao.softDeleteById(id)
            }
            triggerSync()
        }

        override suspend fun saveConfiguration(config: LocalModelConfiguration): LocalModelConfigurationId =
            upsertLocalConfiguration(config)

        override suspend fun deleteConfiguration(id: LocalModelConfigurationId) {
            configsDao.softDeleteById(id)
            triggerSync()
        }

        override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? =
            configsDao.getById(id)?.let {
                entityToConfiguration(it)
            }

        override suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration> =
            configsDao.getAllForAsset(localModelId).map {
                entityToConfiguration(it)
            }

        override suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId) {
            configsDao.deleteAllForAsset(localModelId)
            triggerSync()
        }

        override suspend fun getSoftDeletedModels(): List<LocalModelAsset> =
            modelsDao.getSoftDeletedModels().mapNotNull { loadAsset(it.id) }

        override suspend fun restoreSoftDeletedModel(
            id: LocalModelId,
            configurations: List<LocalModelConfiguration>,
        ): LocalModelAsset =
            transactionProvider.runInTransaction {
                configurations.forEach { config ->
                    upsertLocalConfiguration(config.copy(localModelId = id))
                }
                getAssetById(id) ?: throw IllegalStateException("Failed to load restored asset")
            }

        private suspend fun loadAsset(id: LocalModelId): LocalModelAsset? {
            val model = modelsDao.getById(id) ?: return null
            val configs = configsDao.getAllForAsset(id)
            return LocalModelAsset(
                metadata = entityToMetadata(model),
                configurations = configs.map { entityToConfiguration(it) },
            )
        }

        private fun entityToMetadata(entity: LocalModelEntity) =
            LocalModelMetadata(
                id = entity.id,
                modelFileFormat = entity.modelFileFormat,
                huggingFaceModelName = entity.huggingFaceModelName,
                remoteFileName = entity.remoteFilename,
                localFileName = entity.localFilename,
                sha256 = entity.sha256,
                sizeInBytes = entity.sizeInBytes,
                isMultimodal = entity.isMultimodal,
                mmprojRemoteFileName = entity.mmprojRemoteFilename,
                mmprojLocalFileName = entity.mmprojLocalFilename,
                mmprojSha256 = entity.mmprojSha256,
                mmprojSizeInBytes = entity.mmprojSizeInBytes,
            )

        private fun entityToConfiguration(entity: LocalModelConfigurationEntity) =
            LocalModelConfiguration(
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
                isSystemPreset = entity.isSystemPreset,
            )
    }
