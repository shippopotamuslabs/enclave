package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Events emitted during llama.cpp text generation.
 */
sealed interface GenerationEvent {
    /**
     * Emitted when a new token is generated.
     */
    data class Token(val text: String) : GenerationEvent

    /**
     * Emitted when generation is complete.
     */
    data class Completed(
        val fullText: String,
        val promptTokens: Int? = null,
        val generatedTokens: Int? = null
    ) : GenerationEvent

    /**
     * Emitted when an error occurs during generation.
     */
    data class Error(val throwable: Throwable) : GenerationEvent
}
