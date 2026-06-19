package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.core.data.local.ApiCredentialsDao
import com.browntowndev.enclave.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.enclave.core.data.local.LocalModelsDao
import com.browntowndev.enclave.core.data.local.MediaProviderDao
import com.browntowndev.enclave.core.data.local.TtsProviderDao
import com.browntowndev.enclave.core.data.security.AadContext
import com.browntowndev.enclave.core.data.security.LocalDataEncryptionService
import com.browntowndev.enclave.domain.model.chat.ChatModelOverride
import com.browntowndev.enclave.domain.model.config.ActiveModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
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
        private val mediaProviderDao: MediaProviderDao,
        private val ttsProviderDao: TtsProviderDao,
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
                    reasoningBudget = config.reasoningBudget,
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
                    modelId = config.modelId,
                    systemPrompt =
                        decryptRequired(
                            config.systemPrompt,
                            AadContext("ApiModelConfigurationEntity", "systemPrompt", config.id.value).toBytes(),
                            modelType,
                        ),
                    isMultimodal = config.isMultimodal,
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
                    apiModelPricing =
                        ApiModelSavedPricing(
                            promptUsdPerMillion = config.promptPriceUsdPerMillion,
                            completionUsdPerMillion = config.completionPriceUsdPerMillion,
                            cachedPromptUsdPerMillion = config.cachedPromptPriceUsdPerMillion,
                        ).takeIf { it.hasPromptAndCompletionRates() },
                    apiBaseUrl = credentials.baseUrl ?: credentials.provider.defaultBaseUrl(),
                    includeReasoningContentOnRequest = config.includeReasoningContentOnRequest,
                )
            }

            val mediaProviderId = defaultAssignment.mediaProviderId
            if (mediaProviderId != null) {
                val provider = mediaProviderDao.getMediaProvider(mediaProviderId.value) ?: return null
                val decryptedName = decryptStringOrNull(
                    provider.displayName,
                    AadContext("MediaProviderEntity", "displayName", provider.id).toBytes()
                ) ?: provider.displayName
                val decryptedModelName = provider.modelName?.let {
                    decryptStringOrNull(
                        it,
                        AadContext("MediaProviderEntity", "modelName", provider.id).toBytes()
                    )
                } ?: ""
                return ActiveModelConfiguration(
                    id = com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId(provider.id),
                    isLocal = false,
                    name = decryptedName,
                    modelId = decryptedModelName,
                    systemPrompt = null,
                    apiProvider = provider.provider,
                    apiBaseUrl = provider.baseUrl ?: provider.provider.defaultBaseUrl(),
                    thinkingEnabled = false,
                    temperature = null,
                    topP = null,
                    topK = null,
                    maxTokens = null,
                    minP = null,
                    repetitionPenalty = null,
                    contextWindow = null,
                )
            }

            val ttsProviderId = defaultAssignment.ttsProviderId
            if (ttsProviderId != null) {
                val provider = ttsProviderDao.getTtsProvider(ttsProviderId.value) ?: return null
                val decryptedName = decryptStringOrNull(
                    provider.displayName,
                    AadContext("TtsProviderEntity", "displayName", provider.id).toBytes()
                ) ?: provider.displayName
                val decryptedVoiceName = decryptStringOrNull(
                    provider.voiceName,
                    AadContext("TtsProviderEntity", "voiceName", provider.id).toBytes()
                ) ?: provider.voiceName
                return ActiveModelConfiguration(
                    id = com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId(provider.id),
                    isLocal = false,
                    name = decryptedName,
                    modelId = decryptedVoiceName,
                    systemPrompt = null,
                    apiProvider = provider.provider,
                    apiBaseUrl = provider.baseUrl ?: provider.provider.defaultBaseUrl(),
                    thinkingEnabled = false,
                    temperature = null,
                    topP = null,
                    topK = null,
                    maxTokens = null,
                    minP = null,
                    repetitionPenalty = null,
                    contextWindow = null,
                )
            }

            return null
        }

        override suspend fun getActiveConfigurationByOverride(override: ChatModelOverride): ActiveModelConfiguration? =
            when (override) {
                is ChatModelOverride.Local -> loadLocalConfiguration(override)
                is ChatModelOverride.Api -> loadApiConfiguration(override)
            }

        private suspend fun loadLocalConfiguration(override: ChatModelOverride.Local): ActiveModelConfiguration? {
            val config = localConfigsDao.getById(override.configId)
            val model = config?.let { localModelsDao.getById(it.localModelId) }
            return ActiveModelConfiguration(
                id = override.configId,
                isLocal = true,
                name = model?.displayName?.takeIf { it.isNotBlank() } ?: override.displayName,
                modelId = model?.localFilename ?: "",
                systemPrompt =
                    config?.systemPrompt?.let {
                        decryptStringOrNull(
                            it,
                            AadContext("LocalModelConfigurationEntity", "systemPrompt", override.configId.value).toBytes(),
                        )
                    },
                isMultimodal = model?.isMultimodal ?: false,
                reasoningEffort = null,
                temperature = config?.temperature,
                topK = config?.topK,
                topP = config?.topP,
                maxTokens = config?.maxTokens,
                minP = config?.minP,
                repetitionPenalty = config?.repetitionPenalty,
                contextWindow = config?.contextWindow,
                thinkingEnabled = config?.thinkingEnabled ?: false,
                reasoningBudget = config?.reasoningBudget,
                maxTokensDisabled = false,
                temperatureDisabled = false,
                topPDisabled = false,
                topKDisabled = false,
                minPDisabled = false,
                frequencyPenaltyDisabled = false,
                presencePenaltyDisabled = false,
                localModelFormat = model?.modelFileFormat,
                apiProvider = null,
            )
        }

        private suspend fun loadApiConfiguration(override: ChatModelOverride.Api): ActiveModelConfiguration? {
            val config = apiConfigsDao.getById(override.configId)
            val credentials = config?.let { apiCredentialsDao.getById(it.apiCredentialsId) }
            return ActiveModelConfiguration(
                id = override.configId,
                isLocal = false,
                name =
                    config
                        ?.let {
                            decryptStringOrNull(
                                it.displayName,
                                AadContext("ApiModelConfigurationEntity", "displayName", override.configId.value).toBytes(),
                            )
                        }?.takeIf { it.isNotBlank() }
                        ?: override.displayName,
                modelId = config?.modelId ?: "",
                systemPrompt =
                    config?.systemPrompt?.let {
                        decryptStringOrNull(
                            it,
                            AadContext("ApiModelConfigurationEntity", "systemPrompt", override.configId.value).toBytes(),
                        )
                    },
                isMultimodal = config?.isMultimodal ?: false,
                reasoningEffort =
                    config?.reasoningEffort?.let {
                        com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort.fromWireValue(it)
                    },
                temperature = config?.temperature,
                topK = config?.topK,
                topP = config?.topP,
                maxTokens = config?.maxTokens,
                minP = null,
                repetitionPenalty = null,
                contextWindow = config?.contextWindow,
                thinkingEnabled = config?.reasoningEffort != null,
                maxTokensDisabled = config?.maxTokensDisabled ?: false,
                temperatureDisabled = config?.temperatureDisabled ?: false,
                topPDisabled = config?.topPDisabled ?: false,
                topKDisabled = config?.topKDisabled ?: false,
                minPDisabled = config?.minPDisabled ?: false,
                frequencyPenaltyDisabled = config?.frequencyPenaltyDisabled ?: false,
                presencePenaltyDisabled = config?.presencePenaltyDisabled ?: false,
                localModelFormat = null,
                apiProvider = credentials?.provider,
                apiModelPricing =
                    config?.let {
                        ApiModelSavedPricing(
                            promptUsdPerMillion = it.promptPriceUsdPerMillion,
                            completionUsdPerMillion = it.completionPriceUsdPerMillion,
                            cachedPromptUsdPerMillion = it.cachedPromptPriceUsdPerMillion,
                        ).takeIf { p -> p.hasPromptAndCompletionRates() }
                    },
                apiBaseUrl = credentials?.baseUrl ?: credentials?.provider?.defaultBaseUrl(),
                includeReasoningContentOnRequest = config?.includeReasoningContentOnRequest ?: false,
            )
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
