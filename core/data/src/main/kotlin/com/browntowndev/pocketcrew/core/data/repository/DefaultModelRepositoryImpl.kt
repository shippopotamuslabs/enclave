package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiModelsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.ModelsDao
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.config.ModelStatus
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Implementation of DefaultModelRepositoryPort.
 * Joins with ApiModelsDao or ModelsDao depending on source type.
 */
class DefaultModelRepositoryImpl @Inject constructor(
    private val defaultModelsDao: DefaultModelsDao,
    private val apiModelsDao: ApiModelsDao,
    private val modelsDao: ModelsDao,
) : DefaultModelRepositoryPort {

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
        val entity = defaultModelsDao.getDefault(modelType) ?: return null
        return toAssignment(entity)
    }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> {
        return defaultModelsDao.observeAll().map { list ->
            list.map { toAssignment(it) }
        }
    }

    override suspend fun setDefault(modelType: ModelType, source: ModelSource, apiModelId: Long?) {
        defaultModelsDao.upsert(
            DefaultModelEntity(
                modelType = modelType,
                source = source,
                apiModelId = apiModelId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun clearDefault(modelType: ModelType) {
        defaultModelsDao.delete(modelType)
    }

    override suspend fun resetDefaultsForApiModel(apiModelId: Long) {
        defaultModelsDao.resetAssignmentsForApiModel(apiModelId)
    }

    private suspend fun toAssignment(entity: DefaultModelEntity): DefaultModelAssignment {
        return if (entity.source == ModelSource.API && entity.apiModelId != null) {
            val apiConfig = apiModelsDao.getById(entity.apiModelId)?.toDomain()
            if (apiConfig == null) {
                // Fallback to ON_DEVICE if the API config is missing (dangling reference)
                val defaultOnDeviceModel = modelsDao.getModelEntityByStatus(entity.modelType, ModelStatus.CURRENT)
                DefaultModelAssignment(
                    modelType = entity.modelType,
                    source = ModelSource.ON_DEVICE,
                    apiModelConfig = null,
                    onDeviceDisplayName = defaultOnDeviceModel?.displayName
                )
            } else {
                DefaultModelAssignment(
                    modelType = entity.modelType,
                    source = entity.source,
                    apiModelConfig = apiConfig,
                    onDeviceDisplayName = null
                )
            }
        } else {
            val displayName = modelsDao.getDisplayName(entity.modelType)
            DefaultModelAssignment(
                modelType = entity.modelType,
                source = ModelSource.ON_DEVICE, // Force on-device fallback if API missing
                apiModelConfig = null,
                onDeviceDisplayName = displayName
            )
        }
    }
}
