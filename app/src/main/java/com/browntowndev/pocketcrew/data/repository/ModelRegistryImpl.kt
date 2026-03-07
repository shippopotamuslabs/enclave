package com.browntowndev.pocketcrew.data.repository

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat
import com.browntowndev.pocketcrew.data.local.ModelsDao
import com.browntowndev.pocketcrew.data.local.ModelEntity
import com.browntowndev.pocketcrew.domain.model.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.RegisteredModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRegistryImpl @Inject constructor(
    private val modelsDao: ModelsDao
) : ModelRegistryPort {

    override suspend fun getRegisteredModel(modelType: ModelType): RegisteredModel? {
        val entity = modelsDao.getModelEntity(modelType) ?: return null
        return RegisteredModel(
            remoteFilename = entity.remoteFilename,
            modelType = ModelType.valueOf(entity.modelType.name),
            displayName = entity.displayName,
            modelFileFormat = entity.modelFileFormat,
            md5 = entity.md5,
            sizeInBytes = entity.sizeInBytes,
            temperature = entity.temperature,
            topK = entity.topK,
            topP = entity.topP,
            maxTokens = entity.maxTokens,
            systemPrompt = entity.systemPrompt
        )
    }

    override suspend fun getRegisteredModels(): List<RegisteredModel> =
        modelsDao.getAll().map { entity ->
            RegisteredModel(
                remoteFilename = entity.remoteFilename,
                modelType = ModelType.valueOf(entity.modelType.name),
                displayName = entity.displayName,
                modelFileFormat = entity.modelFileFormat,
                md5 = entity.md5,
                sizeInBytes = entity.sizeInBytes,
                temperature = entity.temperature,
                topK = entity.topK,
                topP = entity.topP,
                maxTokens = entity.maxTokens,
                systemPrompt = entity.systemPrompt
            )
        }

    override fun observeRegisteredModels(): Flow<Map<ModelType, String>> {
        return modelsDao.observeAll().map { entities ->
            entities.associate {
                ModelType.valueOf(it.modelType.name) to it.displayName
            }
        }
    }

    override suspend fun setRegisteredModel(
        remoteFilename: String,
        modelType: ModelType,
        displayName: String,
        modelFileFormat: ModelFileFormat,
        md5: String,
        sizeInBytes: Long,
        temperature: Double,
        topK: Int,
        topP: Double,
        maxTokens: Int,
        systemPrompt: String?
    ) {
        modelsDao.insertOrUpdate(
            ModelEntity(
                remoteFilename = remoteFilename,
                modelType = modelType,
                displayName = displayName,
                modelFileFormat = modelFileFormat,
                md5 = md5,
                sizeInBytes = sizeInBytes,
                temperature = temperature,
                topK = topK,
                topP = topP,
                maxTokens = maxTokens,
                systemPrompt = systemPrompt
            )
        )
    }

    override suspend fun clearAll() {
        modelsDao.deleteAll()
    }
}
