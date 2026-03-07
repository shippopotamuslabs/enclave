package com.browntowndev.pocketcrew.domain.model

/**
 * Enum representing the logical model slots in the inference pipeline.
 */
enum class ModelType(val apiValue: String) {
    /**
     * Vision model for image understanding (e.g., Gemma 3n).
     */
    VISION("vision"),

    /**
     * Draft model for fast initial generation (e.g., small distilled model).
     */
    DRAFT("draft"),

    /**
     * Main model for final high-quality output (e.g., 7B model).
     */
    MAIN("main"),

    /**
     * Fast model for immediate, non-thinking, responses
     */
    FAST("fast");

    companion object {
        /**
         * Converts a string value to a ModelType enum.
         * Defaults to MAIN if the value doesn't match any type.
         */
        fun fromApiValue(value: String): ModelType =
            entries.firstOrNull { it.apiValue == value } ?: MAIN
    }
}
