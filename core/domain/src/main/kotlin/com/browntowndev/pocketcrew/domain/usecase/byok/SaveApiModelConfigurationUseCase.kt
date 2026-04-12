package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import javax.inject.Inject

interface SaveApiModelConfigurationUseCase {
    suspend operator fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId>
}

class SaveApiModelConfigurationUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
) : SaveApiModelConfigurationUseCase {
    override suspend fun invoke(configuration: ApiModelConfiguration): Result<ApiModelConfigurationId> {
        val parent = apiModelRepository.getCredentialsById(configuration.apiCredentialsId)
            ?: return Result.failure(IllegalArgumentException("Parent credentials not found"))
            
        return Result.runCatching {
            apiModelRepository.saveConfiguration(configuration)
        }
    }
}