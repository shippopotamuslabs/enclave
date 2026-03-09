package com.browntowndev.pocketcrew.domain.usecase.modelconfig

import com.browntowndev.pocketcrew.domain.model.ModelConfigurationUi
import com.browntowndev.pocketcrew.domain.model.toModelConfiguration
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.port.repository.TransactionProvider
import javax.inject.Inject

/**
 * Use case to update model configuration (tunings and huggingFaceModelName).
 * Updates the registry within a transaction scope.
 */
class UpdateModelConfigurationUseCase @Inject constructor(
    private val modelRegistry: ModelRegistryPort,
    private val transactionProvider: TransactionProvider
) {
    /**
     * Updates the model configuration with the provided UI model.
     * Registry is updated within a transaction scope.
     * If operation fails, the transaction is rolled back.
     */
    suspend operator fun invoke(uiModel: ModelConfigurationUi) {
        val existingConfig = modelRegistry.getRegisteredModel(uiModel.modelType)
            ?: throw IllegalStateException("No existing config found for model type: ${uiModel.modelType}")

        val updatedConfig = uiModel.toModelConfiguration(existingConfig)

        transactionProvider.runInTransaction {
            modelRegistry.setRegisteredModel(updatedConfig)
        }
    }
}
