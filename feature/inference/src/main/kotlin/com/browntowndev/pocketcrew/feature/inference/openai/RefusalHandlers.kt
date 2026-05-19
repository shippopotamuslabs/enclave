package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.openai.models.responses.ResponseStreamEvent

internal suspend fun OpenAiStreamHandlerContext.handleRefusalDelta(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val refusalTextDelta = event.refusalDelta().get()
    val text = refusalTextDelta.delta()
    val key =
        streamPartKey(
            itemId = refusalTextDelta.itemId(),
            outputIndex = refusalTextDelta.outputIndex(),
            contentIndex = refusalTextDelta.contentIndex(),
        )
    appendStreamDelta(state.refusalTextByPart, key, text)
    state.streamedAssistantMessage.append(text)
    state.emittedAny = true
    state.refusalTextDeltaCount++
    emitEvent(InferenceEvent.PartialResponse(text, modelType))
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleRefusalDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val refusalTextDone = event.refusalDone().get()
    val key =
        streamPartKey(
            itemId = refusalTextDone.itemId(),
            outputIndex = refusalTextDone.outputIndex(),
            contentIndex = refusalTextDone.contentIndex(),
        )
    val fallbackText =
        resolveNovelStreamText(
            parts = state.refusalTextByPart,
            key = key,
            finalizedText = refusalTextDone.refusal(),
        )
    if (fallbackText.isNotEmpty()) {
        loggingPort.debug(
            tag,
            "Responses stream emitted refusal.done fallback model=$modelId key=$key " +
                "chars=${fallbackText.length}",
        )
        state.streamedAssistantMessage.append(fallbackText)
        state.emittedAny = true
        emitEvent(InferenceEvent.PartialResponse(fallbackText, modelType))
    }
    return state
}
