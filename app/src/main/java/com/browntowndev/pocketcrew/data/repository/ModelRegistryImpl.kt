package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.data.local.ModelsDao
import com.browntowndev.pocketcrew.data.local.ModelEntity
import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistryImpl @Inject constructor(
    private val modelsDao: ModelsDao
) : ModelRegistryPort {

    override suspend fun getRegisteredModel(modelType: ModelType): ModelConfiguration? {
        val entity = modelsDao.getModelEntity(modelType) ?: return null
        return entityToModelConfiguration(entity)
    }

    override suspend fun getRegisteredModels(): List<ModelConfiguration> =
        modelsDao.getAll().map { entity ->
            entityToModelConfiguration(entity)
        }

    override fun observeRegisteredModels(): Flow<Map<ModelType, String>> {
        return modelsDao.observeAll().map { entities ->
            entities.associate {
                ModelType.valueOf(it.modelType.name) to it.displayName
            }
        }
    }

    override suspend fun setRegisteredModel(config: ModelConfiguration) {
        modelsDao.insertOrUpdate(
            ModelEntity(
                modelType = config.modelType,
                remoteFilename = config.metadata.localFileName,
                huggingFaceModelName = config.metadata.huggingFaceModelName,
                displayName = config.metadata.displayName,
                modelFileFormat = config.metadata.modelFileFormat,
                md5 = config.metadata.md5,
                sizeInBytes = config.metadata.sizeInBytes,
                temperature = config.tunings.temperature,
                topK = config.tunings.topK,
                topP = config.tunings.topP,
                maxTokens = config.tunings.maxTokens,
                systemPrompt = config.persona.systemPrompt
            )
        )
    }

    override suspend fun clearAll() {
        modelsDao.deleteAll()
    }

    private fun entityToModelConfiguration(entity: ModelEntity): ModelConfiguration {
        return ModelConfiguration(
            modelType = entity.modelType,
            metadata = ModelConfiguration.Metadata(
                huggingFaceModelName = entity.huggingFaceModelName,
                remoteFileName = entity.remoteFilename,
                localFileName = entity.remoteFilename, // Save as-is
                displayName = entity.displayName,
                md5 = entity.md5,
                sizeInBytes = entity.sizeInBytes,
                modelFileFormat = entity.modelFileFormat
            ),
            tunings = ModelConfiguration.Tunings(
                temperature = entity.temperature,
                topK = entity.topK,
                topP = entity.topP,
                maxTokens = entity.maxTokens
            ),
            persona = ModelConfiguration.Persona(
                systemPrompt = entity.systemPrompt ?: ""
            )
        )
    }
}
