package com.browntowndev.enclave.feature.moa.service

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import kotlinx.coroutines.CancellationException
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.feature.inference.InferenceEventBus

internal object CouncilPipelineInferenceEventMapper {
    fun handlePipelineInferenceEvent(
        event: InferenceEvent,
        chatId: String,
        step: PipelineStep,
        inferenceEventBus: InferenceEventBus,
        onPartialChunk: (String) -> Unit,
    ) {
        when (event) {
            is InferenceEvent.EngineLoading -> Unit
            is InferenceEvent.Processing -> Unit
            is InferenceEvent.Thinking -> {
                inferenceEventBus.tryEmitPipelineState(
                    chatId,
                    MessageGenerationState.ThinkingLive(
                        event.chunk,
                        CouncilPipelineStepSupport.getModelTypeForStep(step),
                    ),
                )
            }
            is InferenceEvent.PartialResponse -> {
                onPartialChunk(event.chunk)
                inferenceEventBus.tryEmitPipelineState(
                    chatId,
                    MessageGenerationState.GeneratingText(
                        event.chunk,
                        CouncilPipelineStepSupport.getModelTypeForStep(step),
                    ),
                )
            }
            is InferenceEvent.AnswerStreamBoundary ->
                inferenceEventBus.tryEmitPipelineState(
                    chatId,
                    MessageGenerationState.AnswerStreamBoundary(
                        CouncilPipelineStepSupport.getModelTypeForStep(step),
                    ),
                )
            is InferenceEvent.WebSearchResults -> Unit
            is InferenceEvent.Artifacts -> Unit
            is InferenceEvent.Media -> Unit
            is InferenceEvent.CompactionApplied -> Unit
            is InferenceEvent.PreCompact -> Unit
            is InferenceEvent.PostCompact -> Unit
            is InferenceEvent.ToolRepaired -> Unit
            is InferenceEvent.ModelApiRetryScheduled ->
                inferenceEventBus.tryEmitPipelineState(
                    chatId,
                    MessageGenerationState.ModelApiRetryScheduled(
                        attempt = event.attempt,
                        maxAttempts = event.maxAttempts,
                        delayMs = event.delayMs,
                        modelType = CouncilPipelineStepSupport.getModelTypeForStep(step),
                    ),
                )
            is InferenceEvent.TelemetryCaptured -> Unit
            is InferenceEvent.Finished -> Unit
            is InferenceEvent.SafetyBlocked -> {
                inferenceEventBus.tryEmitPipelineState(
                    chatId,
                    MessageGenerationState.Blocked(
                        event.reason,
                        CouncilPipelineStepSupport.getModelTypeForStep(step),
                    ),
                )
                throw CancellationException("Content blocked: ${event.reason}")
            }
            is InferenceEvent.Error -> {
                throw event.cause
            }
        }
    }
}
