package com.browntowndev.enclave.domain.usecase.byok

import com.browntowndev.enclave.domain.model.config.ApiModelConfiguration
import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

interface SaveApiModelConfigurationUseCase {
    suspend operator fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId>
}

class SaveApiModelConfigurationUseCaseImpl
    @Inject
    constructor(
        private val apiModelRepository: ApiModelRepositoryPort,
    ) : SaveApiModelConfigurationUseCase {
        override suspend fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId> {
            apiModelRepository.getCredentialsById(configuration.apiCredentialsId)
                ?: return Result.failure(IllegalArgumentException("Parent credentials not found"))

            return Result.runCatching {
                apiModelRepository.saveConfiguration(configuration)
            }
        }
    }
