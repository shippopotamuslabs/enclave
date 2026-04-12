package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SaveLocalModelConfigurationUseCase {
    suspend operator fun invoke(configuration: LocalModelConfiguration): Result<LocalModelConfigurationId>
}

class SaveLocalModelConfigurationUseCaseImpl @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
) : SaveLocalModelConfigurationUseCase {
    override suspend fun invoke(configuration: LocalModelConfiguration): Result<LocalModelConfigurationId> {
        return Result.runCatching {
            localModelRepository.saveConfiguration(configuration)
        }
    }
}