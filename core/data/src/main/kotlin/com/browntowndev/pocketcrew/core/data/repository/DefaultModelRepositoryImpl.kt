package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultModelRepositoryImpl @Inject constructor(
    private val defaultModelsDao: DefaultModelsDao,
    private val localModelConfigurationsDao: LocalModelConfigurationsDao,
    private val localModelsDao: LocalModelsDao,
    private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
    private val apiCredentialsDao: ApiCredentialsDao
) : DefaultModelRepositoryPort {

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
        val entity = defaultModelsDao.getDefault(modelType) ?: return null
        return resolveAssignment(entity)
    }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> {
        return defaultModelsDao.observeAll().map { entities ->
            entities.map { resolveAssignment(it) }
        }
    }

    override suspend fun setDefault(modelType: ModelType, localConfigId: Long?, apiConfigId: Long?) {
        val entity = DefaultModelEntity(
            modelType = modelType,
            localConfigId = localConfigId,
            apiConfigId = apiConfigId
        )
        defaultModelsDao.upsert(entity)
    }

    override suspend fun clearDefault(modelType: ModelType) {
        defaultModelsDao.delete(modelType)
    }

    private suspend fun resolveAssignment(entity: DefaultModelEntity): DefaultModelAssignment {
        var displayName: String? = null
        var providerName: String? = null

        if (entity.localConfigId != null) {
            val config = localModelConfigurationsDao.getById(entity.localConfigId)
            if (config != null) {
                val model = localModelsDao.getById(config.localModelId)
                displayName = config.displayName
                providerName = model?.huggingFaceModelName
            }
        } else if (entity.apiConfigId != null) {
            val config = apiModelConfigurationsDao.getById(entity.apiConfigId)
            if (config != null) {
                val creds = apiCredentialsDao.getById(config.apiCredentialsId)
                if (creds != null) {
                    displayName = config.displayName
                    providerName = creds.provider.displayName
                }
            }
        }

        return DefaultModelAssignment(
            modelType = entity.modelType,
            localConfigId = entity.localConfigId,
            apiConfigId = entity.apiConfigId,
            displayName = displayName,
            providerName = providerName
        )
    }
}