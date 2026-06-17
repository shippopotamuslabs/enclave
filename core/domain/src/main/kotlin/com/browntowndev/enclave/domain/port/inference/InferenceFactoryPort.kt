package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.config.ApiModelConfigurationId
import com.browntowndev.enclave.domain.model.config.LocalModelConfigurationId
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
     * Executes [block] with an inference service resolved from an explicit local
     * model configuration id, bypassing slot assignment. Used by the chat model
     * override ("Other") path so the override preset's weights are loaded rather
     * than the FAST slot's. Participates in the on-device inference lock.
     */
    suspend fun <T> withInferenceServiceForLocalConfig(
        configId: LocalModelConfigurationId,
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

    /**
     * Resolves an agent-loop-wrapped inference service for a specific API model
     * configuration and executes [block] with it. Used by the chat model override
     * ("Other") path, which drives inference via [LlmInferencePort.sendPrompt] — the
     * raw SDK services intentionally throw on sendPrompt until wrapped by the agent
     * loop. Unlike [withRawApiServiceForConfig], this returns a service ready for
     * prompt-driven chat turns.
     */
    suspend fun <T> withApiServiceForConfig(
        configId: ApiModelConfigurationId,
        block: suspend (LlmInferencePort) -> T,
    ): T
}
