package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import javax.inject.Inject

interface SaveLocalModelMetadataUseCase {
    suspend operator fun invoke(metadata: LocalModelMetadata): Result<Long>
}

class SaveLocalModelMetadataUseCaseImpl @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
) : SaveLocalModelMetadataUseCase {
    override suspend fun invoke(metadata: LocalModelMetadata): Result<Long> {
        return Result.runCatching {
            modelRegistry.saveLocalModelMetadata(metadata)
        }
    }
}