package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface DeleteLocalModelConfigurationUseCase {
    suspend operator fun invoke(configurationId: LocalModelConfigurationId): Result<Unit>
}

class DeleteLocalModelConfigurationUseCaseImpl
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) : DeleteLocalModelConfigurationUseCase {
        override suspend fun invoke(configurationId: LocalModelConfigurationId): Result<Unit> =
            Result.runCatching {
                localModelRepository.deleteConfiguration(configurationId)
            }
    }
