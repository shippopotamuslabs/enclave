package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Resolves the correct LlmInferencePort for a given ModelType at runtime.
 * Replaces static @FastModelEngine/@ThinkingModelEngine qualifier injection.
 */
interface InferenceFactoryPort {
    /**
     * Resolves the correct LlmInferencePort for a given ModelType at runtime.
     * Replaces static @FastModelEngine/@ThinkingModelEngine qualifier injection.
     *
     * IMPORTANT: This method MUST be called at least once for a ModelType before
     * calling [registerUsage] or [releaseUsage] for its associated service instance,
     * as this method establishes the underlying identity (SHA-256) mapping.
     */
    suspend fun getInferenceService(modelType: ModelType): LlmInferencePort

    /**
     * Registers that a service instance is actively being used.
     * Increments the reference count for the underlying SHA-identity engine.
     *
     * Note: Calling this with a service instance that has not been resolved via
     * [getInferenceService] or has already been fully released will result in a no-op.
     */
    suspend fun registerUsage(service: LlmInferencePort)

    /**
     * Releases a reference on a service instance.
     * Decrements the reference count.
     *
     * Reaching zero means there is no active in-flight usage, but the factory may still
     * keep the service cached for reuse by later requests that resolve to the same model
     * identity.
     */
    suspend fun releaseUsage(service: LlmInferencePort)
}
