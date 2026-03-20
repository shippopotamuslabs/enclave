package com.browntowndev.pocketcrew.domain.model

/**
 * Represents the current state of a message in the generation pipeline.
 * 
 * Note: Database only stores PROCESSING and COMPLETE. THINKING and GENERATING
 * are used by the UI layer for IndicatorState via MessageGenerationState flow.
 */
enum class MessageState {
    /** Message is being processed - streaming generation in progress */
    PROCESSING,
    /** Model is outputting thinking content (hidden) */
    THINKING,
    /** Model is outputting visible text */
    GENERATING,
    /** Generation complete - final response stored */
    COMPLETE,
}
