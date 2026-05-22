package com.browntowndev.enclave.feature.inference.openai

import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.openai.models.responses.ResponseStreamEvent

internal suspend fun OpenAiStreamHandlerContext.handleOutputTextDelta(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val outputTextDelta = event.outputTextDelta().get()
    val text = outputTextDelta.delta()
    val key =
        streamPartKey(
            itemId = outputTextDelta.itemId(),
            outputIndex = outputTextDelta.outputIndex(),
            contentIndex = outputTextDelta.contentIndex(),
        )
    appendStreamDelta(state.outputTextByPart, key, text)
    state.streamedAssistantMessage.append(text)
    state.emittedAny = true
    state.outputTextDeltaCount++
    emitEvent(InferenceEvent.PartialResponse(text, modelType))
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleOutputTextDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val outputTextDone = event.outputTextDone().get()
    val key =
        streamPartKey(
            itemId = outputTextDone.itemId(),
            outputIndex = outputTextDone.outputIndex(),
            contentIndex = outputTextDone.contentIndex(),
        )
    val fallbackText =
        resolveNovelStreamText(
            parts = state.outputTextByPart,
            key = key,
            finalizedText = outputTextDone.text(),
        )
    if (fallbackText.isNotEmpty()) {
        loggingPort.debug(
            tag,
            "Responses stream emitted output_text.done fallback model=$modelId key=$key " +
                "chars=${fallbackText.length}",
        )
        state.streamedAssistantMessage.append(fallbackText)
        state.emittedAny = true
        emitEvent(InferenceEvent.PartialResponse(fallbackText, modelType))
    }
    return state
}
