package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelConfiguration
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SaveLocalModelConfigurationUseCase {
    suspend operator fun invoke(configuration: LocalModelConfiguration): Result<LocalModelConfigurationId>
}

class SaveLocalModelConfigurationUseCaseImpl
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) : SaveLocalModelConfigurationUseCase {
        override suspend fun invoke(configuration: LocalModelConfiguration): Result<LocalModelConfigurationId> =
            Result.runCatching {
                localModelRepository.saveConfiguration(configuration)
            }
    }
