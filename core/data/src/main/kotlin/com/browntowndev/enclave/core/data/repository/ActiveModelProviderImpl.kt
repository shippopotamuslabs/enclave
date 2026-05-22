package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.error.ActiveModelConfigurationDecryptException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.enclave.domain.port.repository.DefaultModelRepositoryPort
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
                    name = model.displayName,
                    modelId = model.localFilename,
                    systemPrompt =
                        config.systemPrompt?.let {
                            decryptStringOrNull(
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
                        decryptRequired(
                            config.displayName,
                            AadContext("ApiModelConfigurationEntity", "displayName", config.id.value).toBytes(),
                            modelType,
                        ),
                    modelId = credentials.modelId,
                    systemPrompt =
                        decryptRequired(
                            config.systemPrompt,
                            AadContext("ApiModelConfigurationEntity", "systemPrompt", config.id.value).toBytes(),
                            modelType,
                        ),
                    isMultimodal = credentials.isMultimodal,
                    reasoningEffort =
                        config.reasoningEffort?.let {
                            com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
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

        private fun decryptRequired(
            stored: String,
            aad: ByteArray,
            modelType: ModelType,
        ): String =
            decryptStringOrNull(stored, aad)
                ?: throw ActiveModelConfigurationDecryptException(modelType)

        private fun decryptStringOrNull(
            stored: String,
            aad: ByteArray,
        ): String? = runCatching { localEncryption.decryptString(stored, aad) }.getOrNull()
    }
