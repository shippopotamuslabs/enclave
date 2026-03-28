package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ApiModelRepositoryPort
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject

/**
 * Deletes an API model configuration and cascades default assignment resets.
 */
class DeleteApiModelUseCase @Inject constructor(
    private val apiModelRepository: ApiModelRepositoryPort,
    private val defaultModelRepository: DefaultModelRepositoryPort,
) {
    suspend operator fun invoke(id: Long) {
        apiModelRepository.delete(id)
        
        ModelType.entries.forEach { modelType ->
            val default = defaultModelRepository.getDefault(modelType)
            if (default?.source == ModelSource.API && default.apiModelConfig?.id == id) {
                defaultModelRepository.setDefault(modelType, ModelSource.ON_DEVICE)
            }
        }
    }
}
