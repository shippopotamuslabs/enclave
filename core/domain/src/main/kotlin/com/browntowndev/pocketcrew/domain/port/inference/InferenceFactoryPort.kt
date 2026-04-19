package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

class InferenceBusyException(
    message: String = "Another message is in progress. Please wait until it completes."
) : IllegalStateException(message)

/**
 * Resolves the correct LlmInferencePort for a given ModelType at runtime.
 * Replaces static @FastModelEngine/@ThinkingModelEngine qualifier injection.
 */
interface InferenceFactoryPort {
    /**
     * Executes a block of work with an inference service.
     * Manages model lifecycle and engine-level mutual exclusion.
     */
    suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T
    ): T
}
