package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry

/**
 * Sealed interface for message generation states.
 * AccumulatedMessages is the primary emission for UI updates.
 */
sealed interface MessageGenerationState {
    /**
     * Returns `true` if this state represents a terminal event that signals
     * the end of the generation stream. [Finished], [Failed], and [Blocked]
     * are terminal; all other states (including [StepCompleted]) are not.
     *
     * Pipeline consumers that also treat [StepCompleted] with a terminal
     * [PipelineStep] as a completion signal should use a local extension
     * that adds pipeline-specific terminal logic on top of this property.
     */
    val isTerminal: Boolean
        get() =
            when (this) {
                is Finished -> true
                is Failed -> true
                is Blocked -> true
                else -> false
            }

    data class EngineLoading(
        val modelType: ModelType,
    ) : MessageGenerationState

    data class Processing(
        val modelType: ModelType,
    ) : MessageGenerationState

    data class ThinkingLive(
        val thinkingChunk: String,
        val modelType: ModelType,
    ) : MessageGenerationState

    data class GeneratingText(
        val textDelta: String,
        val modelType: ModelType,
    ) : MessageGenerationState

    /**
     * Marks that the next answer tokens belong to a fresh model response and should not be
     * concatenated directly against prior assistant text unless thinking separates them.
     */
    data class AnswerStreamBoundary(
        val modelType: ModelType,
    ) : MessageGenerationState

    data class Finished(
        val modelType: ModelType,
    ) : MessageGenerationState

    data class Blocked(
        val reason: String,
        val modelType: ModelType,
    ) : MessageGenerationState

    data class Failed(
        val error: Throwable,
        val modelType: ModelType,
    ) : MessageGenerationState

    data class WebSearchSourcesAttached(
        val sources: List<WebSearchSource>,
        val modelType: ModelType,
    ) : MessageGenerationState

    data class ArtifactsAttached(
        val artifacts: List<com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest>,
        val modelType: ModelType,
    ) : MessageGenerationState

    data class MediaAttached(
        val mediaUris: List<String>,
        val mediaIds: List<String> = emptyList(),
        val mediaTypes: List<String> = emptyList(),
        val prompts: List<String> = emptyList(),
        val modelType: ModelType,
    ) : MessageGenerationState

    data class StepCompleted(
        val stepOutput: String,
        val modelDisplayName: String,
        val modelType: ModelType,
        val stepType: PipelineStep,
    ) : MessageGenerationState

    data class TelemetryCaptured(
        val telemetry: TurnTelemetry,
        val modelType: ModelType,
    ) : MessageGenerationState

    /** Provider transport failed transiently; partial streamed text was discarded and a retry is pending. */
    data class ModelApiRetryScheduled(
        val attempt: Int,
        val maxAttempts: Int,
        val delayMs: Long,
        val modelType: ModelType,
    ) : MessageGenerationState
}

/**
 * Pipeline-specific terminal check: treats [MessageGenerationState.StepCompleted] with
 * [PipelineStep.FINAL] as terminal, in addition to the base [MessageGenerationState.isTerminal]
 * states ([Finished], [Failed], [Blocked]).
 */
val MessageGenerationState.isPipelineTerminal: Boolean
    get() =
        isTerminal ||
            (this is MessageGenerationState.StepCompleted && stepType == PipelineStep.FINAL)
