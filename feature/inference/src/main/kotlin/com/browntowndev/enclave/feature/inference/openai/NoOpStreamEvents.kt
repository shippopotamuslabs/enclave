package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.responses.ResponseStreamEvent

/** Lifecycle and tool-progress envelopes that require no state mutation or logging. */
internal val noOpStreamEventPredicates: List<ResponseStreamEvent.() -> Boolean> =
    listOf(
        ResponseStreamEvent::isCreated,
        ResponseStreamEvent::isInProgress,
        ResponseStreamEvent::isQueued,
        ResponseStreamEvent::isContentPartAdded,
        ResponseStreamEvent::isContentPartDone,
        ResponseStreamEvent::isReasoningSummaryPartAdded,
        ResponseStreamEvent::isReasoningSummaryPartDone,
        ResponseStreamEvent::isFunctionCallArgumentsDelta,
        ResponseStreamEvent::isCustomToolCallInputDelta,
        ResponseStreamEvent::isCustomToolCallInputDone,
        ResponseStreamEvent::isOutputTextAnnotationAdded,
        ResponseStreamEvent::isAudioDelta,
        ResponseStreamEvent::isAudioDone,
        ResponseStreamEvent::isAudioTranscriptDelta,
        ResponseStreamEvent::isAudioTranscriptDone,
        ResponseStreamEvent::isWebSearchCallCompleted,
        ResponseStreamEvent::isWebSearchCallInProgress,
        ResponseStreamEvent::isWebSearchCallSearching,
        ResponseStreamEvent::isFileSearchCallCompleted,
        ResponseStreamEvent::isFileSearchCallInProgress,
        ResponseStreamEvent::isFileSearchCallSearching,
        ResponseStreamEvent::isCodeInterpreterCallCodeDelta,
        ResponseStreamEvent::isCodeInterpreterCallCodeDone,
        ResponseStreamEvent::isCodeInterpreterCallCompleted,
        ResponseStreamEvent::isCodeInterpreterCallInProgress,
        ResponseStreamEvent::isCodeInterpreterCallInterpreting,
        ResponseStreamEvent::isImageGenerationCallCompleted,
        ResponseStreamEvent::isImageGenerationCallGenerating,
        ResponseStreamEvent::isImageGenerationCallInProgress,
        ResponseStreamEvent::isImageGenerationCallPartialImage,
        ResponseStreamEvent::isMcpCallArgumentsDelta,
        ResponseStreamEvent::isMcpCallArgumentsDone,
        ResponseStreamEvent::isMcpCallCompleted,
        ResponseStreamEvent::isMcpCallFailed,
        ResponseStreamEvent::isMcpCallInProgress,
        ResponseStreamEvent::isMcpListToolsCompleted,
        ResponseStreamEvent::isMcpListToolsFailed,
        ResponseStreamEvent::isMcpListToolsInProgress,
    )

internal fun isNoOpStreamEvent(event: ResponseStreamEvent): Boolean =
    noOpStreamEventPredicates.any { predicate -> predicate(event) }
