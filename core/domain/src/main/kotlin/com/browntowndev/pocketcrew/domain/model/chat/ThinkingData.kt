package com.browntowndev.pocketcrew.domain.model.chat

/**
 * Data class for thinking/co chain-of-thought data.
 * Now stores only raw thinking text (no chunking).
 *
 * @property thinkingDurationSeconds Duration of thinking in seconds
 * @property rawFullThought The complete, untruncated chain-of-thought as markdown
 */
data class ThinkingData(
    val thinkingDurationSeconds: Int,
    val rawFullThought: String
)
