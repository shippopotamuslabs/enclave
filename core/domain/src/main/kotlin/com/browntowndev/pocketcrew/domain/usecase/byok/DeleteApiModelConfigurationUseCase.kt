package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject

interface DeleteApiModelConfigurationUseCase {
    suspend operator fun invoke(configurationId: Long): Result<Unit>
}

class DeleteApiModelConfigurationUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
) : DeleteApiModelConfigurationUseCase {
    override suspend fun invoke(configurationId: Long): Result<Unit> {
        return Result.runCatching {
            transactionProvider.runInTransaction {
                apiModelRepository.deleteConfiguration(configurationId)
            }
        }
    }
}