package com.browntowndev.enclave.domain.usecase.modelconfig

import com.browntowndev.enclave.domain.model.config.LocalModelId
import com.browntowndev.enclave.domain.model.config.LocalModelMetadata
import com.browntowndev.enclave.domain.port.repository.LocalModelRepositoryPort
import javax.inject.Inject

interface SaveLocalModelMetadataUseCase {
    suspend operator fun invoke(metadata: LocalModelMetadata): Result<LocalModelId>
}

class SaveLocalModelMetadataUseCaseImpl
    @Inject
    constructor(
        private val localModelRepository: LocalModelRepositoryPort,
    ) : SaveLocalModelMetadataUseCase {
        override suspend fun invoke(metadata: LocalModelMetadata): Result<LocalModelId> =
            Result.runCatching {
                localModelRepository.saveLocalModelMetadata(metadata)
            }
    }
