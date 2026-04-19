package com.browntowndev.pocketcrew.domain.model.inference

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/**
 * Represents a single step in the Crew ChatModeUi pipeline.
 * Each step runs as an independent inference pass.
 */
@Serializable
enum class PipelineStep {
    /** Creative/divergent thinking - broad, novel angles */
    DRAFT_ONE,
    /** Analytical/rigorous thinking - structured, logical */
    DRAFT_TWO,
    /** Main synthesis - combining drafts */
    SYNTHESIS,
    /** Final review - producing user-facing response (terminal state) */
    FINAL;

    /**
     * Returns the next step in the pipeline, or null if this is the final step.
     */
    fun next(): PipelineStep? = when (this) {
        DRAFT_ONE -> DRAFT_TWO
        DRAFT_TWO -> SYNTHESIS
        SYNTHESIS -> FINAL
        FINAL -> null
    }

    /**
     * Human-readable display name for UI and notifications.
     */
    fun displayName(): String = when (this) {
        DRAFT_ONE -> "Draft One"
        DRAFT_TWO -> "Draft Two"
        SYNTHESIS -> "Synthesis"
        FINAL -> "Final Review"
    }

    companion object {
        fun fromString(value: String): PipelineStep {
            return entries.find { it.name == value } ?: DRAFT_ONE
        }
    }
}

/**
 * Domain state for the Crew ChatModeUi pipeline.
 * Represents the complete state of a multi-step inference pipeline.
 *
 * @property chatId Unique identifier for the chat session
 * @property currentStep The current pipeline step being executed
 * @property userMessage The original user message
 * @property stepOutputs Map of completed step outputs (for chaining)
 * @property thinkingSteps List of thinking step descriptions for UI
 * @property startTimeMs Timestamp when the pipeline started (for duration calculation)
 */
@Serializable
data class PipelineState(
    val chatId: String,
    val currentStep: PipelineStep,
    val userMessage: String,
    val stepOutputs: Map<PipelineStep, String> = emptyMap(),
    val thinkingSteps: List<String> = emptyList(),
    val startTimeMs: Long = System.currentTimeMillis()
) {
    /**
     * Creates a copy with a new step output added.
     */
    fun withStepOutput(step: PipelineStep, output: String): PipelineState {
        return copy(
            stepOutputs = stepOutputs + (step to output),
            thinkingSteps = thinkingSteps + "${step.displayName()}: ${output.take(100)}..."
        )
    }

    /**
     * Creates a copy with the next step ready to run.
     */
    fun withNextStep(): PipelineState? {
        val next = currentStep.next() ?: return null
        return copy(currentStep = next)
    }

    /**
     * Returns the accumulated thinking from all completed steps.
     */
    fun accumulatedThinking(): String {
        return stepOutputs.entries.joinToString("\n\n") { (step, output) ->
            "=== ${step.displayName()} ===\n$output"
        }
    }

    /**
     * Returns the total duration in seconds since the pipeline started.
     */
    fun durationSeconds(): Int {
        return ((System.currentTimeMillis() - startTimeMs) / 1000).toInt()
    }

    /**
     * Serializes to JSON string for WorkManager Data storage.
     * Internal use only - for WorkManager serialization.
     */
    fun toJson(): String {
        return Json.encodeToString(this)
    }

    companion object {
        const val KEY_CHAT_ID = "chat_id"
        const val KEY_CURRENT_STEP = "current_step"
        const val KEY_USER_MESSAGE = "user_message"
        const val KEY_STEP_OUTPUTS_JSON = "step_outputs_json"
        const val KEY_THINKING_STEPS_JSON = "thinking_steps_json"
        const val KEY_START_TIME_MS = "start_time_ms"

        const val KEY_THINKING_CHUNK = "thinking_chunk"
        const val KEY_THINKING_STEP = "thinking_step"
        const val KEY_CURRENT_MODEL_TYPE = "current_model_type"
        const val KEY_STEP_OUTPUT = "step_output"
        const val KEY_FINAL_RESPONSE = "final_response"
        const val KEY_DURATION_SECONDS = "duration_seconds"
        const val KEY_ALL_THINKING_STEPS_JSON = "all_thinking_steps_json"

        /**
         * Creates initial pipeline state for starting a Crew ChatModeUi conversation.
         */
        fun createInitial(chatId: String, userMessage: String): PipelineState {
            return PipelineState(
                chatId = chatId,
                currentStep = PipelineStep.DRAFT_ONE,
                userMessage = userMessage
            )
        }

        /**
         * Deserializes from JSON string.
         */
        fun fromJson(json: String): PipelineState {
            return Json.decodeFromString(json)
        }
    }
}
