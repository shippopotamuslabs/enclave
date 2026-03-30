package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject

interface DeleteApiCredentialsUseCase {
    suspend operator fun invoke(id: Long)
}

class DeleteApiCredentialsUseCaseImpl @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
) : DeleteApiCredentialsUseCase {
    override suspend fun invoke(id: Long) {
        transactionProvider.runInTransaction {
            apiModelRepository.deleteCredentials(id)
        }
    }
}