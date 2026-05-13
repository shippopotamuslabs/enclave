package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.DefaultModelAssignmentView
import com.browntowndev.pocketcrew.core.data.local.DefaultModelEntity
import com.browntowndev.pocketcrew.core.data.local.DefaultModelsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.local.MediaProviderDao
import com.browntowndev.pocketcrew.core.data.local.TtsProviderDao
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.core.data.sync.CloudSyncTrigger
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.DefaultModelAssignment
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultModelRepositoryImpl
    @Inject
    constructor(
        private val syncTrigger: CloudSyncTrigger,
        private val defaultModelsDao: DefaultModelsDao,
        private val localModelConfigurationsDao: LocalModelConfigurationsDao,
        private val localModelsDao: LocalModelsDao,
        private val apiModelConfigurationsDao: ApiModelConfigurationsDao,
        private val apiCredentialsDao: ApiCredentialsDao,
        private val ttsProviderDao: TtsProviderDao,
        private val mediaProviderDao: MediaProviderDao,
        private val localEncryption: LocalDataEncryptionService,
    ) : DefaultModelRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
            val entity = defaultModelsDao.getDefault(modelType) ?: return null
            return resolveAssignment(entity)
        }

        override fun observeDefaults(): Flow<List<DefaultModelAssignment>> =
            defaultModelsDao.observeAllWithDetails().map { views ->
                views.map { it.toDomain() }
            }

        override suspend fun setDefault(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
        ) {
            val entity =
                DefaultModelEntity(
                    modelType = modelType,
                    localConfigId = localConfigId,
                    apiConfigId = apiConfigId,
                    ttsProviderId = ttsProviderId,
                    mediaProviderId = mediaProviderId,
                    pendingSync = 1,
                )
            defaultModelsDao.upsert(entity)
            triggerSync()
        }

        override suspend fun clearDefault(modelType: ModelType) {
            defaultModelsDao.softDeleteById(modelType)
            triggerSync()
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
                    presetName = config.displayName.decryptStored()
                }
            } else if (entity.apiConfigId != null) {
                val config = apiModelConfigurationsDao.getById(entity.apiConfigId)
                if (config != null) {
                    val creds = apiCredentialsDao.getById(config.apiCredentialsId)
                    if (creds != null) {
                        displayName = creds.displayName.decryptStored()
                        providerName = creds.provider.displayName
                        presetName = config.displayName.decryptStored()
                    }
                }
            } else if (entity.ttsProviderId != null) {
                val ttsProvider = ttsProviderDao.getTtsProvider(entity.ttsProviderId.value)
                if (ttsProvider != null) {
                    displayName = ttsProvider.displayName.decryptStored()
                    providerName = ttsProvider.provider.displayName
                    presetName = ttsProvider.voiceName.decryptStored()
                }
            } else if (entity.mediaProviderId != null) {
                val mediaProvider = mediaProviderDao.getMediaProvider(entity.mediaProviderId.value)
                if (mediaProvider != null) {
                    displayName = mediaProvider.displayName.decryptStored()
                    providerName = mediaProvider.provider.displayName
                    presetName = mediaProvider.capability.displayName
                }
            }

            return DefaultModelAssignment(
                modelType = entity.modelType,
                localConfigId = entity.localConfigId,
                apiConfigId = entity.apiConfigId,
                ttsProviderId = entity.ttsProviderId,
                mediaProviderId = entity.mediaProviderId,
                displayName = displayName,
                providerName = providerName,
                presetName = presetName,
            )
        }

        private fun DefaultModelAssignmentView.toDomain(): DefaultModelAssignment =
            DefaultModelAssignment(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
                displayName =
                    when {
                        localConfigId != null -> localAssetName
                        apiConfigId != null -> apiAssetName.decryptStoredOrNull()
                        ttsProviderId != null -> ttsAssetName.decryptStoredOrNull()
                        mediaProviderId != null -> mediaAssetName.decryptStoredOrNull()
                        else -> null
                    },
                presetName =
                    when {
                        localConfigId != null -> localPresetName.decryptStoredOrNull()
                        apiConfigId != null -> apiPresetName.decryptStoredOrNull()
                        ttsProviderId != null -> ttsVoiceName.decryptStoredOrNull()
                        mediaProviderId != null -> mediaCapability?.displayName
                        else -> null
                    },
                providerName =
                    when {
                        localConfigId != null -> "Local"
                        apiConfigId != null -> apiProviderName?.displayName
                        ttsProviderId != null -> ttsProviderName?.displayName
                        mediaProviderId != null -> mediaProviderName?.displayName
                        else -> null
                    },
            )

        private fun String.decryptStored(): String = localEncryption.decryptString(this)

        private fun String?.decryptStoredOrNull(): String? = this?.let(localEncryption::decryptString)
    }
