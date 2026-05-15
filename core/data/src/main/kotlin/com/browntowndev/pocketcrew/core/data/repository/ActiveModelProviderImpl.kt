package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.core.data.security.AadContext
import com.browntowndev.pocketcrew.core.data.security.LocalDataEncryptionService
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveModelProviderImpl
    @Inject
    constructor(
        private val defaultModelRepository: DefaultModelRepositoryPort,
        private val localModelsDao: LocalModelsDao,
        private val localConfigsDao: LocalModelConfigurationsDao,
        private val apiCredentialsDao: ApiCredentialsDao,
        private val apiConfigsDao: ApiModelConfigurationsDao,
        private val localEncryption: LocalDataEncryptionService,
    ) : ActiveModelProviderPort {
        @Suppress("ReturnCount")
        override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
            val defaultAssignment = defaultModelRepository.getDefault(modelType) ?: return null

            val localConfigId = defaultAssignment.localConfigId
            if (localConfigId != null) {
                val config = localConfigsDao.getById(localConfigId) ?: return null
                val model = localModelsDao.getById(config.localModelId) ?: return null
                return ActiveModelConfiguration(
                    id = config.id,
                    isLocal = true,
                    name =
                        localEncryption.decryptString(
                            config.displayName,
                            AadContext("LocalModelConfigurationEntity", "displayName", config.id.value).toBytes(),
                        ),
                    modelId = model.localFilename,
                    systemPrompt =
                        config.systemPrompt?.let {
                            localEncryption.decryptString(
                                it,
                                AadContext("LocalModelConfigurationEntity", "systemPrompt", config.id.value).toBytes(),
                            )
                        },
                    isMultimodal = model.isMultimodal,
                    reasoningEffort = null,
                    temperature = config.temperature,
                    topK = config.topK,
                    topP = config.topP,
                    maxTokens = config.maxTokens,
                    minP = config.minP,
                    repetitionPenalty = config.repetitionPenalty,
                    contextWindow = config.contextWindow,
                    thinkingEnabled = config.thinkingEnabled,
                    maxTokensDisabled = false,
                    temperatureDisabled = false,
                    topPDisabled = false,
                    topKDisabled = false,
                    minPDisabled = false,
                    frequencyPenaltyDisabled = false,
                    presencePenaltyDisabled = false,
                    localModelFormat = model.modelFileFormat,
                    apiProvider = null,
                )
            }

            val apiConfigId = defaultAssignment.apiConfigId
            if (apiConfigId != null) {
                val config = apiConfigsDao.getById(apiConfigId) ?: return null
                val credentials = apiCredentialsDao.getById(config.apiCredentialsId) ?: return null
                return ActiveModelConfiguration(
                    id = config.id,
                    isLocal = false,
                    name =
                        localEncryption.decryptString(
                            config.displayName,
                            AadContext("ApiModelConfigurationEntity", "displayName", config.id.value).toBytes(),
                        ),
                    modelId = credentials.modelId,
                    systemPrompt =
                        localEncryption.decryptString(
                            config.systemPrompt,
                            AadContext("ApiModelConfigurationEntity", "systemPrompt", config.id.value).toBytes(),
                        ),
                    isMultimodal = credentials.isMultimodal,
                    reasoningEffort =
                        config.reasoningEffort?.let {
                            com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
                                .fromWireValue(it)
                        },
                    temperature = config.temperature,
                    topK = config.topK,
                    topP = config.topP,
                    maxTokens = config.maxTokens,
                    minP = null, // API models might not have minP
                    repetitionPenalty = null, // API models might not have repetitionPenalty
                    contextWindow = config.contextWindow,
                    thinkingEnabled = config.reasoningEffort != null,
                    maxTokensDisabled = config.maxTokensDisabled,
                    temperatureDisabled = config.temperatureDisabled,
                    topPDisabled = config.topPDisabled,
                    topKDisabled = config.topKDisabled,
                    minPDisabled = config.minPDisabled,
                    frequencyPenaltyDisabled = config.frequencyPenaltyDisabled,
                    presencePenaltyDisabled = config.presencePenaltyDisabled,
                    localModelFormat = null,
                    apiProvider = credentials.provider,
                )
            }

            return null
        }
    }
