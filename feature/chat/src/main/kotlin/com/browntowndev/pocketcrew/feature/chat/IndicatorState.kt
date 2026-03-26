package com.browntowndev.pocketcrew.feature.chat

/**
 * Unified indicator state for all response types.
 * No None - state is always one of these variants.
 */
sealed class IndicatorState {
    /** Processing state - shows processing indicator */
    object Processing : IndicatorState()

    /** Thinking state - shows animated thinking indicator with raw markdown */
    data class Thinking(
        val thinkingRaw: String,
        val thinkingDurationSeconds: Long,
        val thinkingStartTime: Long = 0L,
    ) : IndicatorState()

    /** Generating state - shows generating indicator (with optional thinking data for "Thought For" header) */
    data class Generating(
        val thinkingData: ThinkingDataUi?
    ) : IndicatorState() {
        val hasThinkingData: Boolean
            get() = thinkingData != null && thinkingData.thinkingRaw.isNotBlank()
    }

    /** Complete state - shows final response (with optional thinking data for "Thought For" header) */
    data class Complete(
        val thinkingData: ThinkingDataUi?
    ) : IndicatorState() {
        val hasThinkingData: Boolean
            get() = thinkingData != null && thinkingData.thinkingRaw.isNotBlank()
    }

    /**
     * User Message - never shows an indicator.
     */
    object None : IndicatorState()
}

/**
 * Data class for thinking information.
 * Contains the raw thinking text as markdown and duration.
 *
 * @property thinkingDurationSeconds Duration of thinking in seconds (for completed thinking)
 * @property thinkingRaw Raw thinking text as markdown
 * @property thinkingStartTime Timestamp when thinking started (for active counting)
 */
data class ThinkingDataUi(
    val thinkingDurationSeconds: Long,
    val thinkingRaw: String,
    val thinkingStartTime: Long = 0L,
)
