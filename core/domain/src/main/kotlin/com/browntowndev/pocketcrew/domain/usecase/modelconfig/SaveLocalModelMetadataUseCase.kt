package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.model.config.LocalModelMetadata
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SaveLocalModelMetadataUseCase {
    suspend operator fun invoke(metadata: LocalModelMetadata): Result<LocalModelId>
}

class SaveLocalModelMetadataUseCaseImpl @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
) : SaveLocalModelMetadataUseCase {
    override suspend fun invoke(metadata: LocalModelMetadata): Result<LocalModelId> {
        return Result.runCatching {
            localModelRepository.saveLocalModelMetadata(metadata)
        }
    }
}