package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelConfiguration
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import javax.inject.Inject

interface SaveLocalModelConfigurationUseCase {
    suspend operator fun invoke(configuration: LocalModelConfiguration): Result<Long>
}

class SaveLocalModelConfigurationUseCaseImpl @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
) : SaveLocalModelConfigurationUseCase {
    override suspend fun invoke(configuration: LocalModelConfiguration): Result<Long> {
        return Result.runCatching {
            modelRegistry.saveConfiguration(configuration)
        }
    }
}