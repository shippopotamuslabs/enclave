package com.browntowndev.pocketcrew.presentation.screen.chat

/**
 * Unified indicator state for all response types.
 * No None - state is always one of these variants.
 */
sealed class IndicatorState {
    /** Processing state - shows processing indicator */
    object Processing : IndicatorState()

    /** Thinking state - shows animated thinking indicator with steps */
    data class Thinking(
        val thinkingSteps: List<String>,
        val thinkingDurationSeconds: Long,
    ) : IndicatorState()

    /** Generating state - shows generating indicator (with optional thinking data for "Thought For" header) */
    data class Generating(
        val thinkingData: ThinkingDataUi?
    ) : IndicatorState() {
        val hasThinkingData: Boolean
            get() = thinkingData != null && thinkingData.steps.isNotEmpty()
    }

    /** Complete state - shows final response (with optional thinking data for "Thought For" header) */
    data class Complete(
        val thinkingData: ThinkingDataUi?
    ) : IndicatorState() {
        val hasThinkingData: Boolean
            get() = thinkingData != null && thinkingData.steps.isNotEmpty()
    }

    /**
     * User Message - never shows an indicator.
     */
    object None : IndicatorState()
}

/**
 * Data class for thinking information.
 * Contains the thinking steps, duration, and model display name.
 */
data class ThinkingDataUi(
    val thinkingDurationSeconds: Long,
    val steps: List<String>,
)
