package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.config.LocalModelId
import com.browntowndev.pocketcrew.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface DeleteLocalModelMetadataUseCase {
    suspend operator fun invoke(id: LocalModelId): Result<Unit>
}

class DeleteLocalModelMetadataUseCaseImpl @Inject constructor(
    private val localModelRepository: LocalModelRepositoryPort,
) : DeleteLocalModelMetadataUseCase {
    override suspend fun invoke(id: LocalModelId): Result<Unit> {
        return Result.runCatching {
            localModelRepository.deleteLocalModelMetadata(id)
        }
    }
}