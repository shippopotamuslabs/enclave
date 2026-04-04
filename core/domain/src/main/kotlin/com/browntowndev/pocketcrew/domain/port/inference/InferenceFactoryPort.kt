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
     * Runs [block] with the resolved inference service for [modelType].
     *
     * Implementations own both lifecycle and concurrency:
     * - only one on-device inference may run at a time
     * - same model identity reuses the already loaded engine
     * - a different model identity closes the previous idle engine before loading the next
     *
     * If another on-device inference is already in progress, implementations should throw
     * [InferenceBusyException].
     */
    suspend fun <T> withInferenceService(
        modelType: ModelType,
        block: suspend (LlmInferencePort) -> T
    ): T
}
