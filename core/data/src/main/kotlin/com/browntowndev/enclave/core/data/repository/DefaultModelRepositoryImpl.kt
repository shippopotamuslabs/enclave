package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.DefaultModelAssignmentView
import com.browntowndev.enclave.core.data.local.DefaultModelEntity
import com.browntowndev.enclave.core.data.local.DefaultModelsDao
import com.browntowndev.enclave.core.data.local.LiveVoiceProviderDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.core.data.sync.CloudSyncTrigger
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.DefaultModelAssignment
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.model.config.MediaProviderId
import com.browntowndev.enclave.domain.model.config.TtsProviderId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
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
        private val liveVoiceProviderDao: LiveVoiceProviderDao? = null,
        private val localEncryption: LocalDataEncryptionService,
    ) : DefaultModelRepositoryPort {
        private fun triggerSync() = syncTrigger.trigger()

        override suspend fun getDefault(modelType: ModelType): DefaultModelAssignment? {
            val entity = defaultModelsDao.getDefault(modelType) ?: return null
            return resolveAssignment(entity)
        }

        override fun observeDefaults(): Flow<List<DefaultModelAssignment>> =
            defaultModelsDao.observeAllWithDetails().map { views ->
                views.mapNotNull { view ->
                    runCatching { view.toDomain() }.getOrNull()
                }
            }

        override suspend fun setDefault(
            modelType: ModelType,
            localConfigId: LocalModelConfigurationId?,
            apiConfigId: ApiModelConfigurationId?,
            ttsProviderId: TtsProviderId?,
            mediaProviderId: MediaProviderId?,
            liveVoiceProviderId: com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId?,
        ) {
            val entity =
                DefaultModelEntity(
                    modelType = modelType,
                    localConfigId = localConfigId,
                    apiConfigId = apiConfigId,
                    ttsProviderId = ttsProviderId,
                    mediaProviderId = mediaProviderId,
                    liveVoiceProviderId = liveVoiceProviderId,
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
            var modelId: String? = null

            if (entity.localConfigId != null) {
                val config = localModelConfigurationsDao.getById(entity.localConfigId)
                if (config != null) {
                    val model = localModelsDao.getById(config.localModelId)
                    displayName =
                        model?.displayName?.takeIf { it.isNotBlank() }
                            ?: model?.huggingFaceModelName
                    providerName = "Local"
                    presetName =
                        decryptFieldOrNull(
                            stored = config.displayName,
                            owner = "LocalModelConfigurationEntity",
                            field = "displayName",
                            entityId = config.id.value,
                        )
                }
            } else if (entity.apiConfigId != null) {
                val config = apiModelConfigurationsDao.getById(entity.apiConfigId)
                if (config != null) {
                    val creds = apiCredentialsDao.getById(config.apiCredentialsId)
                    if (creds != null) {
                        displayName =
                            decryptFieldOrNull(
                                stored = creds.displayName,
                                owner = "ApiCredentialsEntity",
                                field = "displayName",
                                entityId = creds.id.value,
                            )
                        providerName = creds.provider.displayName
                        presetName =
                            decryptFieldOrNull(
                                stored = config.displayName,
                                owner = "ApiModelConfigurationEntity",
                                field = "displayName",
                                entityId = config.id.value,
                            )
                        modelId = config.modelId.takeIf { it.isNotBlank() }
                    }
                }
            } else if (entity.ttsProviderId != null) {
                val ttsProvider = ttsProviderDao.getTtsProvider(entity.ttsProviderId.value)
                if (ttsProvider != null) {
                    displayName =
                        decryptFieldOrNull(
                            stored = ttsProvider.displayName,
                            owner = "TtsProviderEntity",
                            field = "displayName",
                            entityId = ttsProvider.id,
                        )
                    providerName = ttsProvider.provider.displayName
                    presetName =
                        decryptFieldOrNull(
                            stored = ttsProvider.voiceName,
                            owner = "TtsProviderEntity",
                            field = "voiceName",
                            entityId = ttsProvider.id,
                        )
                }
            } else if (entity.mediaProviderId != null) {
                val mediaProvider = mediaProviderDao.getMediaProvider(entity.mediaProviderId.value)
                if (mediaProvider != null) {
                    displayName =
                        decryptFieldOrNull(
                            stored = mediaProvider.displayName,
                            owner = "MediaProviderEntity",
                            field = "displayName",
                            entityId = mediaProvider.id,
                        )
                    providerName = mediaProvider.provider.displayName
                    presetName =
                        mediaProvider.modelName?.let { modelName ->
                            decryptFieldOrNull(
                                stored = modelName,
                                owner = "MediaProviderEntity",
                                field = "modelName",
                                entityId = mediaProvider.id,
                            )
                        }
                }
            } else if (entity.liveVoiceProviderId != null) {
                val liveVoiceProvider = liveVoiceProviderDao?.getLiveVoiceProvider(entity.liveVoiceProviderId.value)
                if (liveVoiceProvider != null) {
                    displayName =
                        decryptFieldOrNull(
                            stored = liveVoiceProvider.displayName,
                            owner = "LiveVoiceProviderEntity",
                            field = "displayName",
                            entityId = liveVoiceProvider.id,
                        )
                    providerName = liveVoiceProvider.provider.displayName
                    presetName =
                        decryptFieldOrNull(
                            stored = liveVoiceProvider.voiceName,
                            owner = "LiveVoiceProviderEntity",
                            field = "voiceName",
                            entityId = liveVoiceProvider.id,
                        )
                }
            }

            return DefaultModelAssignment(
                modelType = entity.modelType,
                localConfigId = entity.localConfigId,
                apiConfigId = entity.apiConfigId,
                ttsProviderId = entity.ttsProviderId,
                mediaProviderId = entity.mediaProviderId,
                liveVoiceProviderId = entity.liveVoiceProviderId,
                displayName = displayName,
                providerName = providerName,
                presetName = presetName,
                modelId = modelId,
            )
        }

        private fun DefaultModelAssignmentView.toDomain(): DefaultModelAssignment =
            DefaultModelAssignment(
                modelType = modelType,
                localConfigId = localConfigId,
                apiConfigId = apiConfigId,
                ttsProviderId = ttsProviderId,
                mediaProviderId = mediaProviderId,
                liveVoiceProviderId = liveVoiceProviderId,
                displayName =
                    when {
                        localConfigId != null -> localAssetName
                        apiConfigId != null ->
                            apiCredentialsId?.let { credId ->
                                apiAssetName?.let {
                                    decryptFieldOrNull(
                                        stored = it,
                                        owner = "ApiCredentialsEntity",
                                        field = "displayName",
                                        entityId = credId.value,
                                    )
                                }
                            }
                        ttsProviderId != null ->
                            ttsAssetName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "TtsProviderEntity",
                                    field = "displayName",
                                    entityId = ttsProviderId.value,
                                )
                            }
                        mediaProviderId != null ->
                            mediaAssetName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "MediaProviderEntity",
                                    field = "displayName",
                                    entityId = mediaProviderId.value,
                                )
                            }
                        liveVoiceProviderId != null ->
                            liveVoiceAssetName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "LiveVoiceProviderEntity",
                                    field = "displayName",
                                    entityId = liveVoiceProviderId.value,
                                )
                            }
                        else -> null
                    },
                presetName =
                    when {
                        localConfigId != null ->
                            localPresetName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "LocalModelConfigurationEntity",
                                    field = "displayName",
                                    entityId = localConfigId.value,
                                )
                            }
                        apiConfigId != null ->
                            apiPresetName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "ApiModelConfigurationEntity",
                                    field = "displayName",
                                    entityId = apiConfigId.value,
                                )
                            }
                        ttsProviderId != null ->
                            ttsVoiceName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "TtsProviderEntity",
                                    field = "voiceName",
                                    entityId = ttsProviderId.value,
                                )
                            }
                        mediaProviderId != null ->
                            mediaModelName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "MediaProviderEntity",
                                    field = "modelName",
                                    entityId = mediaProviderId.value,
                                )
                            }
                        liveVoiceProviderId != null ->
                            liveVoiceVoiceName?.let {
                                decryptFieldOrNull(
                                    stored = it,
                                    owner = "LiveVoiceProviderEntity",
                                    field = "voiceName",
                                    entityId = liveVoiceProviderId.value,
                                )
                            }
                        else -> null
                    },
                modelId = apiModelId?.takeIf { it.isNotBlank() },
                providerName =
                    when {
                        localConfigId != null -> "Local"
                        apiConfigId != null -> apiProviderName?.displayName
                        ttsProviderId != null -> ttsProviderName?.displayName
                        mediaProviderId != null -> mediaProviderName?.displayName
                        liveVoiceProviderId != null -> liveVoiceProviderName?.displayName
                        else -> null
                    },
            )

        private fun decryptField(
            stored: String,
            owner: String,
            field: String,
            entityId: String,
        ): String = localEncryption.decryptString(stored, AadContext(owner, field, entityId).toBytes())

        /** Used by [observeDefaults] so Room emissions cannot crash collectors while locked or mid-reconciliation. */
        private fun decryptFieldOrNull(
            stored: String,
            owner: String,
            field: String,
            entityId: String,
        ): String? =
            runCatching {
                decryptField(stored, owner, field, entityId)
            }.getOrNull()
    }
