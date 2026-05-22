package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface DeleteLocalModelMetadataUseCase {
    suspend operator fun invoke(id: LocalModelId): Result<Unit>
}

class DeleteLocalModelMetadataUseCaseImpl
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) : DeleteLocalModelMetadataUseCase {
        override suspend fun invoke(id: LocalModelId): Result<Unit> =
            Result.runCatching {
                localModelRepository.deleteLocalModelMetadata(id)
            }
    }
