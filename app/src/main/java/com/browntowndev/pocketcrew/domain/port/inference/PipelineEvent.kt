package com.browntowndev.pocketcrew.domain.port.inference

/**
 * Represents the overarching stages of the pipeline orchestration.
 */
enum class PipelinePhase {
    DRAFTING,
    SYNTHESIS,
    REFINEMENT,
    SAFETY_CHECK,
    FAST_INFERENCE
}

/**
 * Identifies the specific agent or model instance acting within the pipeline.
 */
enum class AgentRole {
    DRAFTER_ONE,
    DRAFTER_TWO,
    DRAFTER_THREE,
    DRAFTER_FOUR,
    SYNTHESIZER_ONE,
    SYNTHESIZER_TWO,
    FINAL_THINKER,
    WATCHDOG,
    SYSTEM,
    FAST_MODEL
}

/**
 * Represents the state of the overarching multimodel pipeline.
 */
sealed interface PipelineEvent {
    data class PhaseUpdate(
        val phase: PipelinePhase,
        val activeAgent: AgentRole = AgentRole.SYSTEM
    ) : PipelineEvent

    data class ReasoningChunk(
        val agent: AgentRole,
        val chunk: String,
        val accumulatedThought: String
    ) : PipelineEvent

    data class TextChunk(
        val agent: AgentRole,
        val chunk: String,
        val accumulatedText: String
    ) : PipelineEvent

    data class SafetyIntervention(
        val reason: String,
        val agent: AgentRole = AgentRole.WATCHDOG
    ) : PipelineEvent

    data class Completed(
        val finalResponse: String,
        val allThinkingSteps: List<String>,
        val pipelineDurationSeconds: Int
    ) : PipelineEvent

    data class Error(val cause: Throwable) : PipelineEvent
}