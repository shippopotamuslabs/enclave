package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface DeleteLocalModelConfigurationUseCase {
    suspend operator fun invoke(configurationId: LocalModelConfigurationId): Result<Unit>
}

class DeleteLocalModelConfigurationUseCaseImpl @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
) : DeleteLocalModelConfigurationUseCase {
    override suspend fun invoke(configurationId: LocalModelConfigurationId): Result<Unit> {
        return Result.runCatching {
            localModelRepository.deleteConfiguration(configurationId)
        }
    }
}