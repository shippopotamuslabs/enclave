package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * Enum representing the logical model slots in the inference pipeline.
 */
@Serializable
enum class ModelType(val apiValue: String) {
    /**
     * Vision model for image understanding (e.g., Qwen2.5-VL).
     */
    @SerialName("vision")
    VISION("vision"),

    /**
     * First draft model for fast initial generation (e.g., small distilled model).
     */
    @SerialName("draft_one")
    DRAFT_ONE("draft_one"),

    /**
     * Second draft model for fast initial generation.
     */
    @SerialName("draft_two")
    DRAFT_TWO("draft_two"),

    /**
     * Main model for final high-quality output (e.g., Qwen3.5).
     */
    @SerialName("main")
    MAIN("main"),

    /**
     * Fast model for immediate, non-thinking, responses
     */
    @SerialName("fast")
    FAST("fast"),

    /**
     * Thinking model for reasoning tasks with extended context.
     */
    @SerialName("thinking")
    THINKING("thinking"),

    /**
     * Final synthesis model for producing polished final output.
     */
    @SerialName("final_synthesis")
    FINAL_SYNTHESIS("final_synthesis"),

    /**
     * Text-to-speech model for audio synthesis.
     */
    @SerialName("tts")
    TTS("tts"),

    /**
     * Used for models that are being re-downloaded but are not yet assigned to a role.
     */
    @SerialName("unassigned")
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
        TTS -> "Text-to-Speech"
        UNASSIGNED -> "Unassigned"
    }

    /**
     * Returns a human-readable description for this model role.
     */
    val description: String
        get() = when (this) {
            VISION -> "Understand and analyze images provided in your messages."
            DRAFT_ONE -> "Quick initial drafting of responses."
            DRAFT_TWO -> "Refining initial drafts for better quality."
            MAIN -> "Produces the primary, high-quality chat response."
            FAST -> "Immediate, non-thinking, responses for simple queries."
            THINKING -> "Deep reasoning and logic for complex tasks."
            FINAL_SYNTHESIS -> "Polishing and final synthesis of the response."
            TTS -> "Synthesize text responses into natural-sounding speech."
            UNASSIGNED -> "Models not yet assigned to a role."
        }

    companion object {
        /**
         * Maps a string value to a ModelType enum.
         * Defaults to MAIN if the value doesn't match any type.
         */
        fun fromApiValue(value: String): ModelType =
            entries.firstOrNull { it.apiValue == value } ?: MAIN
    }
}
