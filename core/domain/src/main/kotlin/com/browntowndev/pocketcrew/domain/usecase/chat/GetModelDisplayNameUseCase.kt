package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import javax.inject.Inject

/**
 * Use case for getting the display name of a model.
 * Wraps ModelRegistryPort.getRegisteredModelSync() for use in ViewModels.
 */
class GetModelDisplayNameUseCase @Inject constructor(
    private val modelRegistry: ModelRegistryPort
) {
    /**
     * Returns the display name of the model for the given type.
     *
     * @param modelType The type of model to get display name for
     * @return The display name, or empty string if no model is registered
     */
    operator fun invoke(modelType: ModelType): String {
        return modelRegistry.getRegisteredModelSync(modelType)?.metadata?.displayName ?: ""
    }
}
