package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SetDefaultModelUseCase {
    suspend operator fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?
    )
}

class SetDefaultModelUseCaseImpl @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val localModelRepository: LocalModelRepositoryPort,
    private val apiModelRepository: ApiModelRepositoryPort,
) : SetDefaultModelUseCase {
    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
    ) {
        if (modelType == ModelType.VISION) {
            require(localConfigId == null) {
                "Vision slot is API-only."
            }
            val configId = apiConfigId ?: throw IllegalArgumentException("Vision slot requires an API model assignment.")
            val config = apiModelRepository.getConfigurationById(configId)
                ?: throw IllegalArgumentException("Vision slot requires a valid API model configuration.")
            val credentials = apiModelRepository.getCredentialsById(config.apiCredentialsId)
                ?: throw IllegalArgumentException("Vision slot requires a valid API model provider.")
            require(credentials.isMultimodal) {
                "Vision slot requires a multimodal API model."
            }
        }
        defaultModelRepository.setDefault(modelType, localConfigId, apiConfigId)
    }
}
