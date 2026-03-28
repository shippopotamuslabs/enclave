package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Resolves the correct LlmInferencePort for a given ModelType at runtime.
 * Replaces static @FastModelEngine/@ThinkingModelEngine qualifier injection.
 */
interface InferenceFactoryPort {
    suspend fun getInferenceService(modelType: ModelType): LlmInferencePort
}
