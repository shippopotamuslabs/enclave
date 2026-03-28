package com.browntowndev.pocketcrew.domain.usecase.byok

import com.browntowndev.pocketcrew.domain.model.inference.ModelSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.DefaultModelRepositoryPort
import javax.inject.Inject

/**
 * Updates the default model assignment for a given ModelType slot.
 */
class SetDefaultModelUseCase @Inject constructor(
    private val defaultModelRepository: DefaultModelRepositoryPort,
) {
    suspend operator fun invoke(
        modelType: ModelType,
        source: ModelSource,
        apiModelId: Long? = null,
    ) {
        if (source == ModelSource.API) {
            requireNotNull(apiModelId) { "API model ID cannot be null when source is API" }
        }
        
        val actualApiModelId = if (source == ModelSource.ON_DEVICE) null else apiModelId
        defaultModelRepository.setDefault(modelType, source, actualApiModelId)
    }
}
