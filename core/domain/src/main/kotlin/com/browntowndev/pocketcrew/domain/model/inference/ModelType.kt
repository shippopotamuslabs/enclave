package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Enum representing the logical model slots in the inference pipeline.
 */
enum class ModelType(val apiValue: String) {
    /**
     * Vision model for image understanding (e.g., Qwen2.5-VL).
     */
    VISION("vision"),

    /**
     * First draft model for fast initial generation (e.g., small distilled model).
     */
    DRAFT_ONE("draft_one"),

    /**
     * Second draft model for fast initial generation.
     */
    DRAFT_TWO("draft_two"),

    /**
     * Main model for final high-quality output (e.g., Qwen3.5).
     */
    MAIN("main"),

    /**
     * Fast model for immediate, non-thinking, responses
     */
    FAST("fast"),

    /**
     * Thinking model for reasoning tasks with extended context.
     */
    THINKING("thinking"),

    /**
     * Final synthesis model for producing polished final output.
     */
    FINAL_SYNTHESIS("final_synthesis"),

    /**
     * Used for models that are being re-downloaded but are not yet assigned to a role.
     */
    UNASSIGNED("unassigned");

    /**
     * Returns the human-readable display name for this model role.
     */
    fun displayName(): String = when (this) {
        VISION -> "Vision"
        DRAFT_ONE -> "Draft One"
        DRAFT_TWO -> "Draft Two"
        MAIN -> "Synthesis"
        FAST -> "Fast"
        THINKING -> "Thinking"
        FINAL_SYNTHESIS -> "Final Review"
        UNASSIGNED -> "Unassigned"
    }

    companion object {
        /**
         * Converts a string value to a ModelType enum.
         * Defaults to MAIN if the value doesn't match any type.
         */
        fun fromApiValue(value: String): ModelType =
            entries.firstOrNull { it.apiValue == value } ?: MAIN
    }
}
