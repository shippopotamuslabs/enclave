package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.UtilityModelEntity
import com.browntowndev.enclave.core.data.local.UtilityModelsDao
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.UtilityModelId
import com.browntowndev.enclave.domain.model.config.UtilityModelMetadata
import com.browntowndev.enclave.domain.model.config.UtilityType
import com.browntowndev.enclave.domain.port.repository.UtilityModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Singleton
class UtilityModelRepositoryImpl
    @Inject
    constructor(
        private val utilityModelsDao: UtilityModelsDao,
        private val syncTrigger: CloudSyncTrigger,
    ) : UtilityModelRepositoryPort {
        override suspend fun getByUtilityType(utilityType: UtilityType): UtilityModelMetadata? =
            utilityModelsDao.getByUtilityType(utilityType)?.toDomain()

        override suspend fun getAllActive(): List<UtilityModelMetadata> =
            utilityModelsDao.getAll().map { it.toDomain() }

        override fun observeAllActive(): Flow<List<UtilityModelMetadata>> =
            utilityModelsDao.observeAllActive().map { entities -> entities.map { it.toDomain() } }

        override suspend fun getById(id: UtilityModelId): UtilityModelMetadata? =
            utilityModelsDao.getById(id)?.toDomain()

        override suspend fun getBySha256(sha256: String): UtilityModelMetadata? =
            utilityModelsDao.getBySha256(sha256)?.toDomain()

        @OptIn(ExperimentalUuidApi::class)
        override suspend fun upsert(metadata: UtilityModelMetadata): UtilityModelId {
            val existingByType = utilityModelsDao.getByUtilityType(metadata.utilityType)
            val resolvedId =
                when {
                    existingByType != null -> existingByType.id
                    metadata.id.value.isNotEmpty() -> metadata.id
                    else -> UtilityModelId(Uuid.random().toString())
                }
            val entity =
                metadata.toEntity(
                    id = resolvedId,
                    pendingSync = 1,
                    deleted = false,
                    updatedAt = System.currentTimeMillis(),
                )
            utilityModelsDao.upsert(entity)
            syncTrigger.trigger()
            return resolvedId
        }

        override suspend fun softDelete(id: UtilityModelId) {
            utilityModelsDao.softDeleteById(id)
            syncTrigger.trigger()
        }

        private fun UtilityModelEntity.toDomain(): UtilityModelMetadata =
            UtilityModelMetadata(
                id = id,
                utilityType = utilityType,
                huggingFaceModelName = huggingFaceModelName,
                remoteFileName = remoteFilename,
                localFileName = localFilename,
                sha256 = sha256,
                sizeInBytes = sizeInBytes,
                modelFileFormat = modelFileFormat,
            )

        private fun UtilityModelMetadata.toEntity(
            id: UtilityModelId,
            pendingSync: Int,
            deleted: Boolean,
            updatedAt: Long,
        ): UtilityModelEntity =
            UtilityModelEntity(
                id = id,
                utilityType = utilityType,
                modelFileFormat = modelFileFormat,
                huggingFaceModelName = huggingFaceModelName,
                remoteFilename = remoteFileName,
                localFilename = localFileName,
                sha256 = sha256,
                sizeInBytes = sizeInBytes,
                updatedAt = updatedAt,
                pendingSync = pendingSync,
                deleted = deleted,
            )
    }
