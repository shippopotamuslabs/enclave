package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.ApiCredentialsDao
import com.browntowndev.pocketcrew.core.data.local.ApiModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelConfigurationsDao
import com.browntowndev.pocketcrew.core.data.local.LocalModelsDao
import com.browntowndev.pocketcrew.domain.model.config.ActiveModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActiveModelProviderImpl @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val localModelsDao: LocalModelsDao,
    private val localConfigsDao: LocalModelConfigurationsDao,
    private val apiCredentialsDao: ApiCredentialsDao,
    private val apiConfigsDao: ApiModelConfigurationsDao
) : ActiveModelProviderPort {

    override suspend fun getActiveConfiguration(modelType: ModelType): ActiveModelConfiguration? {
        val defaultAssignment = defaultModelRepository.getDefault(modelType) ?: return null

        val localConfigId = defaultAssignment.localConfigId
        if (localConfigId != null) {
            val config = localConfigsDao.getById(localConfigId) ?: return null
            val model = localModelsDao.getById(config.localModelId) ?: return null
            return ActiveModelConfiguration(
                id = config.id,
                isLocal = true,
                name = config.displayName,
                systemPrompt = config.systemPrompt,
                visionCapable = model.visionCapable,
                reasoningEffort = null,
                temperature = config.temperature,
                topK = config.topK,
                topP = config.topP,
                maxTokens = config.maxTokens,
                minP = config.minP,
                repetitionPenalty = config.repetitionPenalty,
                contextWindow = config.contextWindow,
                thinkingEnabled = config.thinkingEnabled
            )
        }
        
        val apiConfigId = defaultAssignment.apiConfigId
        if (apiConfigId != null) {
            val config = apiConfigsDao.getById(apiConfigId) ?: return null
            val credentials = apiCredentialsDao.getById(config.apiCredentialsId) ?: return null
            return ActiveModelConfiguration(
                id = config.id,
                isLocal = false,
                name = config.displayName,
                systemPrompt = config.systemPrompt,
                visionCapable = credentials.isVision,
                reasoningEffort = config.reasoningEffort?.let {
                    com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort.fromWireValue(it)
                },
                temperature = config.temperature,
                topK = config.topK,
                topP = config.topP,
                maxTokens = config.maxTokens,
                minP = null, // API models might not have minP
                repetitionPenalty = null, // API models might not have repetitionPenalty
                contextWindow = config.contextWindow,
                thinkingEnabled = config.reasoningEffort != null
            )
        }

        return null
    }
}
