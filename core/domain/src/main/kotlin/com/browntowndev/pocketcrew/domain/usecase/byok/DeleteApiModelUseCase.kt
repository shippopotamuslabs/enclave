package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject

/**
 * Deletes an API model configuration and cascades default assignment resets.
 */
class DeleteApiModelUseCase @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
    private val transactionProvider: TransactionProvider,
) {
    suspend operator fun invoke(id: Long) {
        transactionProvider.runInTransaction {
            // Reset any defaults pointing to this API model to ON_DEVICE
            defaultModelRepository.resetDefaultsForApiModel(id)

            // Then delete the API model configuration
            apiModelRepository.delete(id)
        }
    }
}
