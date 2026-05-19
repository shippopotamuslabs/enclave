package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.openai.models.responses.ResponseStreamEvent

internal suspend fun OpenAiStreamHandlerContext.handleReasoningTextDelta(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val reasoningTextDelta = event.reasoningTextDelta().get()
    val text = reasoningTextDelta.delta()
    val key =
        streamPartKey(
            itemId = reasoningTextDelta.itemId(),
            outputIndex = reasoningTextDelta.outputIndex(),
            contentIndex = reasoningTextDelta.contentIndex(),
        )
    appendStreamDelta(state.reasoningTextByPart, key, text)
    state.emittedAny = true
    state.reasoningTextDeltaCount++
    emitEvent(InferenceEvent.Thinking(text, modelType))
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleReasoningTextDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val reasoningTextDone = event.reasoningTextDone().get()
    val key =
        streamPartKey(
            itemId = reasoningTextDone.itemId(),
            outputIndex = reasoningTextDone.outputIndex(),
            contentIndex = reasoningTextDone.contentIndex(),
        )
    val fallbackText =
        resolveNovelStreamText(
            parts = state.reasoningTextByPart,
            key = key,
            finalizedText = reasoningTextDone.text(),
        )
    if (fallbackText.isNotEmpty()) {
        loggingPort.debug(
            tag,
            "Responses stream emitted reasoning_text.done fallback model=$modelId key=$key " +
                "chars=${fallbackText.length}",
        )
        state.emittedAny = true
        emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
    }
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleReasoningSummaryTextDelta(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val reasoningSummaryTextDelta = event.reasoningSummaryTextDelta().get()
    val text = reasoningSummaryTextDelta.delta()
    val key =
        streamItemKey(
            itemId = reasoningSummaryTextDelta.itemId(),
            outputIndex = reasoningSummaryTextDelta.outputIndex(),
        )
    appendStreamDelta(state.reasoningSummaryByPart, key, text)
    state.emittedAny = true
    state.reasoningSummaryDeltaCount++
    emitEvent(InferenceEvent.Thinking(text, modelType))
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleReasoningSummaryTextDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val reasoningSummaryTextDone = event.reasoningSummaryTextDone().get()
    val key =
        streamItemKey(
            itemId = reasoningSummaryTextDone.itemId(),
            outputIndex = reasoningSummaryTextDone.outputIndex(),
        )
    val fallbackText =
        resolveNovelStreamText(
            parts = state.reasoningSummaryByPart,
            key = key,
            finalizedText = reasoningSummaryTextDone.text(),
        )
    if (fallbackText.isNotEmpty()) {
        loggingPort.debug(
            tag,
            "Responses stream emitted reasoning_summary_text.done fallback model=$modelId " +
                "key=$key chars=${fallbackText.length}",
        )
        state.emittedAny = true
        emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
    }
    return state
}
