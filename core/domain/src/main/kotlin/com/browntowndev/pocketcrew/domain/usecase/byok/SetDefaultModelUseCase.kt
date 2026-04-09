package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
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
) : SetDefaultModelUseCase {
    override suspend fun invoke(
        modelType: ModelType,
        localConfigId: LocalModelConfigurationId?,
        apiConfigId: ApiModelConfigurationId?,
    ) {
        defaultModelRepository.setDefault(modelType, localConfigId, apiConfigId)
    }
}