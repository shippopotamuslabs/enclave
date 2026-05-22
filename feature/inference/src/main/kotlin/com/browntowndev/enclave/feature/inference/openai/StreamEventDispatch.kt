package com.browntowndev.enclave.feature.inference.openai

import com.openai.models.responses.ResponseStreamEvent

internal object StreamEventDispatch {
    data class Route(
        val matches: ResponseStreamEvent.() -> Boolean,
        val handle: suspend OpenAiStreamHandlerContext.(ResponseStreamEvent, StreamState) -> StreamState,
    )

    private val routes: List<Route> =
        listOf(
            Route(ResponseStreamEvent::isOutputTextDelta, OpenAiStreamHandlerContext::handleOutputTextDelta),
            Route(ResponseStreamEvent::isOutputTextDone, OpenAiStreamHandlerContext::handleOutputTextDone),
            Route(ResponseStreamEvent::isReasoningTextDelta, OpenAiStreamHandlerContext::handleReasoningTextDelta),
            Route(ResponseStreamEvent::isReasoningTextDone, OpenAiStreamHandlerContext::handleReasoningTextDone),
            Route(
                ResponseStreamEvent::isReasoningSummaryTextDelta,
                OpenAiStreamHandlerContext::handleReasoningSummaryTextDelta,
            ),
            Route(
                ResponseStreamEvent::isReasoningSummaryTextDone,
                OpenAiStreamHandlerContext::handleReasoningSummaryTextDone,
            ),
            Route(ResponseStreamEvent::isRefusalDelta, OpenAiStreamHandlerContext::handleRefusalDelta),
            Route(ResponseStreamEvent::isRefusalDone, OpenAiStreamHandlerContext::handleRefusalDone),
            Route(
                ResponseStreamEvent::isFunctionCallArgumentsDone,
                OpenAiStreamHandlerContext::handleFunctionCallArgumentsDone,
            ),
            Route(ResponseStreamEvent::isOutputItemAdded, OpenAiStreamHandlerContext::handleOutputItemAdded),
            Route(ResponseStreamEvent::isOutputItemDone, OpenAiStreamHandlerContext::handleOutputItemDone),
            Route(ResponseStreamEvent::isCompleted, OpenAiStreamHandlerContext::handleCompleted),
            Route(ResponseStreamEvent::isIncomplete, OpenAiStreamHandlerContext::handleIncomplete),
            Route(ResponseStreamEvent::isFailed, OpenAiStreamHandlerContext::handleFailed),
            Route(ResponseStreamEvent::isError, OpenAiStreamHandlerContext::handleError),
        )

    suspend fun dispatch(
        context: OpenAiStreamHandlerContext,
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        for (route in routes) {
            if (route.matches(event)) {
                return route.handle(context, event, state)
            }
        }
        if (isNoOpStreamEvent(event)) {
            return state
        }
        return context.handleUnexpectedStreamEvent(event, state)
    }
}
