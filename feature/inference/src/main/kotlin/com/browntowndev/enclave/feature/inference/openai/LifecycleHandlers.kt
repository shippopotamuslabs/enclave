package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.responses.ResponseStreamEvent

internal suspend fun OpenAiStreamHandlerContext.handleCompleted(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val response =
        event
            .completed()
            .get()
            .response()
    state.responseId = response.id()
    state.receivedTerminalEvent = true
    state.applyResponseUsage(response)
    response.output().forEach { item ->
        reconcileAssistantMessageOutputItem(
            item = item,
            state = state,
            source = "response.completed",
        )
    }
    loggingPort.debug(
        tag,
        "Responses stream completed model=$modelId " +
            "outputTextDeltas=${state.outputTextDeltaCount} " +
            "reasoningTextDeltas=${state.reasoningTextDeltaCount} " +
            "reasoningSummaryDeltas=${state.reasoningSummaryDeltaCount} " +
            "refusalTextDeltas=${state.refusalTextDeltaCount}",
    )
    return state
}

internal fun OpenAiStreamHandlerContext.handleIncomplete(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val incompleteEvent = event.incomplete().get()
    val response = incompleteEvent.response()
    state.responseId = response.id()
    state.receivedTerminalEvent = true
    state.applyResponseUsage(response)
    val details = response.incompleteDetails().map { it.toString() }.orElse("none")
    loggingPort.debug(
        tag,
        "Responses stream incomplete model=$modelId responseId=${state.responseId} " +
            "incompleteDetails=$details",
    )
    return state
}

internal suspend fun OpenAiStreamHandlerContext.handleFailed(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val failedEvent = event.failed().get()
    val errorMsg =
        failedEvent
            .response()
            .error()
            .map { it.message() }
            .orElse("Unknown API error")
    if (shouldRecoverFromStreamTermination(state, errorMsg)) {
        loggingPort.warning(
            tag,
            "Responses stream reported recoverable API failure after partial output " +
                "provider=$provider model=$modelId error=$errorMsg",
        )
        state.receivedTerminalEvent = true
        return state
    }
    throw RuntimeException("API Error: $errorMsg")
}

internal suspend fun OpenAiStreamHandlerContext.handleError(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val errorEvent = event.error().get()
    val errorMsg = errorEvent.message()
    if (shouldRecoverFromStreamTermination(state, errorMsg)) {
        loggingPort.warning(
            tag,
            "Responses stream reported recoverable stream error after partial output " +
                "provider=$provider model=$modelId error=$errorMsg",
        )
        state.receivedTerminalEvent = true
        return state
    }
    throw RuntimeException("Stream Error: $errorMsg")
}

internal fun OpenAiStreamHandlerContext.handleUnexpectedStreamEvent(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val summary = truncateForLog(runCatching { event.toString() }.getOrElse { "<toString failed>" })
    loggingPort.warning(
        tag,
        "Responses stream unrecognized event (update handler for new SDK event?) " +
            "model=$modelId summary=$summary",
    )
    return state
}

/**
 * Minimum streamed text length worth salvaging as the final answer. Below this,
 * rethrowing is better: the Rust model-API retry path replays the turn cleanly
 * (the UI discards the short partial on ModelApiRetryScheduled), instead of
 * silently presenting a truncated reply as complete.
 */
internal const val MIN_SALVAGEABLE_STREAM_CHARS = 200

internal fun shouldRecoverFromStreamTermination(
    state: StreamState,
    message: String?,
): Boolean {
    if (!isRecoverableStreamTermination(message)) return false
    return state.toolCallRequests.isNotEmpty() ||
        state.streamedAssistantMessage.length >= MIN_SALVAGEABLE_STREAM_CHARS
}

internal fun isRecoverableStreamTermination(message: String?): Boolean {
    val normalized = message?.lowercase() ?: return false
    return normalized.contains("stream ended unexpectedly") ||
        normalized.contains("internal stream ended unexpectedly") ||
        com.browntowndev.enclave.domain.model.inference.ModelApiTransportHeuristics
            .isTransientTransportMessage(normalized)
}
