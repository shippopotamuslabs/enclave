package com.browntowndev.pocketcrew.domain.port.inference

import kotlinx.coroutines.flow.Flow

/**
 * The core entry point for the multi-agent inference pipeline.
 * Implementations are responsible for managing hardware resources, memory constraints,
 * and routing prompts through the Draft → Synthesis → Refine phases.
 */
interface EnginePipelineOrchestrator {

    /**
     * Executes the full multi-agent pipeline for a given user prompt.
     * * @param prompt The raw user text input.
     * @param hasImage Whether an image payload is attached (triggers the Vision model).
     * @return A stream of [PipelineEvent]s to be consumed directly by the UI state holders.
     */
    fun processComplexPrompt(prompt: String, hasImage: Boolean = false): Flow<PipelineEvent>

    /**
     * Executes a simple/fast inference for non-complex prompts.
     * Uses the fast model directly without the full multi-agent pipeline.
     * @param prompt The raw user text input.
     * @return A stream of [PipelineEvent]s to be consumed directly by the UI state holders.
     */
    fun processSimplePrompt(prompt: String): Flow<PipelineEvent>

    /**
     * Immediately interrupts the active pipeline.
     * Implementations must guarantee that all currently executing flows are cancelled
     * and all hardware resources (Engines) are dropped from RAM.
     */
    fun cancelPipeline()
}