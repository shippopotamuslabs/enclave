package com.browntowndev.pocketcrew.domain.port.inference

import kotlinx.coroutines.flow.Flow

/**
 * Core domain abstraction for LLM inference.
 * The implementation (in the inference layer) will wrap LiteRT-LM or other inference runtimes.
 */
interface LlmInferencePort {
    /**
     * Sends a prompt to the LLM and returns a flow of events.
     * @param prompt The prompt to send to the LLM.
     * @param closeConversation Whether to close the conversation after sending the prompt.
     */
    fun sendPrompt(prompt: String, closeConversation: Boolean = false): Flow<InferenceEvent>

    /**
     * Closes the underlying session and releases resources. Both the engine
     * and conversation are closed.
     */
    fun closeSession()
}

/**
 * Represents the discrete states of a streaming LLM response.
 * This is a pure domain type with no framework dependencies.
 */
sealed interface InferenceEvent {
    /**
     * Emitted when the model is generating reasoning/Chain-of-Thought.
     * @param chunk The raw chunk of thought just generated.
     * @param accumulatedThought The full reasoning generated so far.
     */
    data class Thinking(val chunk: String, val accumulatedThought: String) : InferenceEvent

    /**
     * Emitted when the model is generating the actual user-facing response.
     * @param chunk The raw text chunk just generated.
     */
    data class PartialResponse(val chunk: String) : InferenceEvent

    /**
     * Emitted when generation is completely finished.
     */
    data class Completed(val finalResponse: String, val rawFullThought: String?) : InferenceEvent

    /**
     * Emitted when generation is blocked by safety checks.
     */
    data class SafetyBlocked(val reason: String) : InferenceEvent

    /**
     * Emitted when an error occurs during generation.
     */
    data class Error(val cause: Throwable) : InferenceEvent
}
