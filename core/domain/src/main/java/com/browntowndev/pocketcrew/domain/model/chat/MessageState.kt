package com.browntowndev.pocketcrew.domain.model

/**
 * Represents the current state of a message in the generation pipeline.
 */
enum class MessageState {
    /** Initial state - message created but no generation started */
    PROCESSING,
    /** Model is thinking - has thinkingSteps */
    THINKING,
    /** Text is being generated */
    GENERATING,
    /** Generation complete */
    COMPLETE,
}
