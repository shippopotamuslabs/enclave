package com.browntowndev.pocketcrew.presentation.mapper

import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase

/**
 * Maps the active pipeline phase to a human-readable UI string.
 */
fun PipelinePhase.toUiText(): String = when (this) {
    PipelinePhase.DRAFTING -> "Generating parallel drafts..."
    PipelinePhase.SYNTHESIS -> "Cross-evaluating concepts..."
    PipelinePhase.REFINEMENT -> "Executing final logic check..."
    PipelinePhase.SAFETY_CHECK -> "Verifying operational safety..."
    PipelinePhase.FAST_INFERENCE -> "Processing..."
}

/**
 * Maps the agent role to its anthropomorphic UI alias.
 */
fun AgentRole.toAlias(): String = when (this) {
    AgentRole.DRAFTER_ONE -> "Intuiter"
    AgentRole.DRAFTER_TWO -> "Spitballer"
    AgentRole.DRAFTER_THREE -> "Brainstormer"
    AgentRole.DRAFTER_FOUR -> "Conceptor"
    AgentRole.SYNTHESIZER_ONE -> "The Analyst"
    AgentRole.SYNTHESIZER_TWO -> "The Pragmatist"
    AgentRole.FINAL_THINKER -> "The Stoic"
    AgentRole.WATCHDOG -> "Nightwatchman"
    AgentRole.SYSTEM -> "Orchestrator"
    AgentRole.FAST_MODEL -> "Fast Model"
}

/**
 * Utility to format the UI output for the thinking indicator.
 * Example output: "The Analyst - Cross-evaluating concepts..."
 */
fun formatThinkingStep(agent: AgentRole, phase: PipelinePhase): String {
    return "${agent.toAlias()} - ${phase.toUiText()}"
}

/**
 * Utility to format the raw reasoning chunk for the UI.
 * Example output: "The Stoic: I need to verify this assumption."
 */
fun formatReasoningOutput(agent: AgentRole, thoughtChunk: String): String {
    return "${agent.toAlias()}: $thoughtChunk"
}