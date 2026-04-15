package com.browntowndev.pocketcrew.domain.model.chat

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep


/**
 * Sealed interface for message generation states.
 * AccumulatedMessages is the primary emission for UI updates.
 */
sealed interface MessageGenerationState {
    data class EngineLoading(val modelType: ModelType) : MessageGenerationState
    data class Processing(val modelType: ModelType) : MessageGenerationState
    data class ThinkingLive(val thinkingChunk: String, val modelType: ModelType) : MessageGenerationState
    data class GeneratingText(val textDelta: String, val modelType: ModelType) : MessageGenerationState
    data class Finished(val modelType: ModelType) : MessageGenerationState
    data class Blocked(val reason: String, val modelType: ModelType) : MessageGenerationState
    data class Failed(val error: Throwable, val modelType: ModelType) : MessageGenerationState
    data class TavilySourcesAttached(
        val sources: List<TavilySource>,
        val modelType: ModelType,
    ) : MessageGenerationState
    data class StepCompleted(
        val stepOutput: String,
        val modelDisplayName: String,
        val modelType: ModelType,
        val stepType: PipelineStep
    ) : MessageGenerationState
}