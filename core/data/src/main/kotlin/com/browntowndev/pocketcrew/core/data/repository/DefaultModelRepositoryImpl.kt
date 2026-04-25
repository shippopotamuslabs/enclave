package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
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
    private val apiCredentialsDao: ApiCredentialsDao,
    private val ttsProviderDao: TtsProviderDao,
) : DefaultModelRepositoryPort {

    override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
        val entity = defaultModelsDao.getDefault(modelType) ?: return null
        return resolveAssignment(entity)
    }

    override fun observeDefaults(): Flow<List<DefaultModelAssignment>> {
        return defaultModelsDao.observeAllWithDetails().map { views ->
            views.map { view ->
                val displayName = when {
                    view.localConfigId != null -> view.localAssetName
                    view.apiConfigId != null -> view.apiAssetName
                    view.ttsProviderId != null -> view.ttsAssetName
                    else -> null
                }
                val presetName = when {
                    view.localConfigId != null -> view.localPresetName
                    view.apiConfigId != null -> view.apiPresetName
                    view.ttsProviderId != null -> view.ttsVoiceName
                    else -> null
                }
                val providerName = when {
                    view.localConfigId != null -> "Local"
                    view.apiConfigId != null -> view.apiProviderName?.displayName
                    view.ttsProviderId != null -> view.ttsProviderName?.displayName
                    else -> null
                }

                DefaultModelAssignment(
                    modelType = view.modelType,
                    localConfigId = view.localConfigId,
                    apiConfigId = view.apiConfigId,
                    ttsProviderId = view.ttsProviderId,
                    displayName = displayName,
                    presetName = presetName,
                    providerName = providerName
                )
            }
        }
    }

    override suspend fun setDefault(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
        ttsProviderId: TtsProviderId?
    ) {
        val entity = DefaultModelEntity(
            modelType = modelType,
            localConfigId = localConfigId,
            apiConfigId = apiConfigId,
            ttsProviderId = ttsProviderId
        )
        defaultModelsDao.upsert(entity)
    }

    override suspend fun clearDefault(modelType: ModelType) {
        defaultModelsDao.delete(modelType)
    }

    private suspend fun resolveAssignment(entity: DefaultModelEntity): DefaultModelAssignment {
        var displayName: String? = null
        var providerName: String? = null
        var presetName: String? = null

        if (entity.localConfigId != null) {
            val config = localModelConfigurationsDao.getById(entity.localConfigId)
            if (config != null) {
                val model = localModelsDao.getById(config.localModelId)
                displayName = model?.huggingFaceModelName
                providerName = "Local"
                presetName = config.displayName
            }
        } else if (entity.apiConfigId != null) {
            val config = apiModelConfigurationsDao.getById(entity.apiConfigId)
            if (config != null) {
                val creds = apiCredentialsDao.getById(config.apiCredentialsId)
                if (creds != null) {
                    displayName = creds.displayName
                    providerName = creds.provider.displayName
                    presetName = config.displayName
                }
            }
        } else if (entity.ttsProviderId != null) {
            val ttsProvider = ttsProviderDao.getTtsProvider(entity.ttsProviderId.value)
            if (ttsProvider != null) {
                displayName = ttsProvider.displayName
                providerName = ttsProvider.provider.displayName
                presetName = ttsProvider.voiceName
            }
        }

        return DefaultModelAssignment(
            modelType = entity.modelType,
            localConfigId = entity.localConfigId,
            apiConfigId = entity.apiConfigId,
            ttsProviderId = entity.ttsProviderId,
            displayName = displayName,
            providerName = providerName,
            presetName = presetName
        )
    }
}