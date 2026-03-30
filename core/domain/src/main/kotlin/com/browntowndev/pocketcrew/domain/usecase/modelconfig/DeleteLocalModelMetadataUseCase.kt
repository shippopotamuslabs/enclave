package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import javax.inject.Inject

interface DeleteLocalModelMetadataUseCase {
    suspend operator fun invoke(id: Long): Result<Unit>
}

class DeleteLocalModelMetadataUseCaseImpl @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
) : DeleteLocalModelMetadataUseCase {
    override suspend fun invoke(id: Long): Result<Unit> {
        return Result.runCatching {
            modelRegistry.deleteLocalModelMetadata(id)
        }
    }
}