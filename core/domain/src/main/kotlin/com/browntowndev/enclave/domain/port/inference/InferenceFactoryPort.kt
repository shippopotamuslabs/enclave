package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.inference.ModelType

class InferenceBusyException(
    message: String = "Another message is in progress. Please wait until it completes.",
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
        block: suspend (LlmInferencePort) -> T,
    ): T

    /**
     * Resolves a fresh, uncached inference service for a specific API model configuration
     * and executes [block] with it. Intended for background analysis sessions that manage
     * their own lifecycle — does not participate in the chat inference lock.
     */
    suspend fun <T> withRawApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T
}
