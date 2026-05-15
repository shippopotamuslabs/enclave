package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelEntity
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.security.SensitiveEntityFieldEncryptors
import com.browntowndev.pocketcrew.core.data.security.SensitivePersistenceSelfHealer
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepair
import com.browntowndev.pocketcrew.core.data.security.SensitiveRoomReadRepairApplier
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
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
        private val healer: SensitivePersistenceSelfHealer,
        private val readRepairApplier: SensitiveRoomReadRepairApplier,
    ) : LocalModelRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        @OptIn(ExperimentalUuidApi::class)
        private fun generateConfigGuid(): LocalModelConfigurationId = LocalModelConfigurationId(Uuid.random().toString())

        @OptIn(ExperimentalUuidApi::class)
        private fun generateModelGuid(): LocalModelId = LocalModelId(Uuid.random().toString())

        override suspend fun getAllLocalAssets(): List<LocalModelAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = modelsDao.getAllActive().mapNotNull { loadAsset(it.id, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override fun observeAllLocalAssets(): Flow<List<LocalModelAsset>> =
            modelsDao.observeAllActive().mapLatest { entities ->
                val repairs = mutableListOf<SensitiveRoomReadRepair>()
                val out = entities.mapNotNull { loadAsset(it.id, repairs) }
                readRepairApplier.apply(repairs)
                out
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
            val configId =
                if (config.id.value.isNotEmpty()) config.id else generateConfigGuid()
            val entity =
                SensitiveEntityFieldEncryptors.encrypt(
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
                    ),
                    healer,
                )
            configsDao.upsert(entity)
            triggerSync()
            return configId
        }

        override suspend fun getAssetById(id: LocalModelId): LocalModelAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = loadAsset(id, repairs)
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getAssetByConfigId(configId: LocalModelConfigurationId): LocalModelAsset? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out =
                configsDao.getById(configId)?.let {
                    loadAsset(it.localModelId, repairs)
                }
            readRepairApplier.apply(repairs)
            return out
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

        override suspend fun getConfigurationById(id: LocalModelConfigurationId): LocalModelConfiguration? {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = configsDao.getById(id)?.let { entityToConfiguration(it, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun getAllConfigurationsForAsset(localModelId: LocalModelId): List<LocalModelConfiguration> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = configsDao.getAllForAsset(localModelId).map { entityToConfiguration(it, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

        override suspend fun deleteAllConfigurationsForAsset(localModelId: LocalModelId) {
            configsDao.deleteAllForAsset(localModelId)
            triggerSync()
        }

        override suspend fun getSoftDeletedModels(): List<LocalModelAsset> {
            val repairs = mutableListOf<SensitiveRoomReadRepair>()
            val out = modelsDao.getSoftDeletedModels().mapNotNull { loadAsset(it.id, repairs) }
            readRepairApplier.apply(repairs)
            return out
        }

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

        private suspend fun loadAsset(
            id: LocalModelId,
            readRepairs: MutableList<SensitiveRoomReadRepair>?,
        ): LocalModelAsset? {
            val model = modelsDao.getById(id) ?: return null
            val configs = configsDao.getAllForAsset(id)
            return LocalModelAsset(
                metadata = entityToMetadata(model),
                configurations = configs.map { entityToConfiguration(it, readRepairs) },
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

        private fun entityToConfiguration(
            entity: LocalModelConfigurationEntity,
            readRepairs: MutableList<SensitiveRoomReadRepair>?,
        ): LocalModelConfiguration {
            val e = SensitiveEntityFieldEncryptors.decrypt(entity, healer, readRepairs)
            return LocalModelConfiguration(
                id = e.id,
                localModelId = e.localModelId,
                displayName = e.displayName,
                temperature = e.temperature,
                topK = e.topK,
                topP = e.topP,
                minP = e.minP,
                repetitionPenalty = e.repetitionPenalty,
                maxTokens = e.maxTokens,
                contextWindow = e.contextWindow,
                thinkingEnabled = e.thinkingEnabled,
                systemPrompt = e.systemPrompt ?: "",
                isSystemPreset = e.isSystemPreset,
            )
        }
    }
