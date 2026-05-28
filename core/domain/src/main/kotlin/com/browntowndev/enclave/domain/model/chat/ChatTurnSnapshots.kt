package com.browntowndev.enclave.domain.model.chat

import com.browntowndev.enclave.domain.model.chat.MessageState
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineStep
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry

data class AccumulatedMessages(
    val messages: Map<MessageId, MessageSnapshot>,
)

data class MessageSnapshot(
    val messageId: MessageId,
    val modelType: ModelType,
    val content: String,
    val thinkingRaw: String,
    val thinkingDurationSeconds: Long = 0,
    val thinkingStartTime: Long = 0,
    val thinkingEndTime: Long = 0,
    val isComplete: Boolean = false,
    val messageState: MessageState = if (isComplete) MessageState.COMPLETE else MessageState.GENERATING,
    val pipelineStep: PipelineStep? = null,
    val webSearchSources: List<WebSearchSource> = emptyList(),
    val artifacts: List<com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest> = emptyList(),
    val generatedMediaUris: List<String> = emptyList(),
    val generatedMediaAssetIds: List<String> = emptyList(),
    val generatedMediaTypes: List<String> = emptyList(),
    val generatedMediaPrompts: List<String> = emptyList(),
    val transcriptSegments: List<MessageTranscriptSegment> = emptyList(),
    val telemetryRows: List<TurnTelemetry> = emptyList(),
)
