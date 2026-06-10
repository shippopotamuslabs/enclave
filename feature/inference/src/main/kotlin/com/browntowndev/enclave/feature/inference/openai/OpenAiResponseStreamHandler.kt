package com.browntowndev.enclave.feature.inference.openai

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.openai.models.responses.ResponseStreamEvent

/**
 * Handles individual OpenAI Responses API stream events.
 *
 * Each event type (output_text_delta, reasoning_text_done, function_call_arguments_done, etc.)
 * is processed by a dedicated handler that mutates the [StreamState] accumulator.
 *
 * The class is stateless with respect to streaming — it receives the current [StreamState],
 * applies the event, and returns the updated state. Event emission is a side-effect handled via
 * the injected `emitEvent` callback.
 */
internal class OpenAiResponseStreamHandler(
    provider: String,
    modelId: String,
    modelType: ModelType,
    loggingPort: LoggingPort,
    tag: String,
    chatId: ChatId?,
    userMessageId: MessageId?,
    assistantMessageId: MessageId?,
    private val emitEvent: suspend (InferenceEvent) -> Unit,
) {
    private val streamContext =
        OpenAiStreamHandlerContext(
            provider = provider,
            modelId = modelId,
            modelType = modelType,
            loggingPort = loggingPort,
            tag = tag,
            chatId = chatId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            emitEvent = emitEvent,
        )

    /**
     * Dispatches a [ResponseStreamEvent] to the appropriate handler and returns the updated
     * [StreamState].
     *
     * Covers all discriminator branches exposed by `openai-java` [ResponseStreamEvent] for SDK
     * 4.32.x. Lifecycle and tool-progress envelopes are no-ops (no log); unexpected shapes fall
     * through to [handleUnexpectedStreamEvent] with a truncated payload summary.
     */
    suspend fun handleEvent(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState = StreamEventDispatch.dispatch(streamContext, event, state)

    /**
     * Attempts to recover from a stream termination exception. Returns the current state
     * if recovery is possible (partial output was already emitted and the error is recoverable),
     * or null if the exception should be re-thrown.
     */
    fun handleStreamTermination(
        state: StreamState,
        message: String?,
        context: String,
    ): StreamState? {
        if (!shouldRecoverFromStreamTermination(state, message)) {
            return null
        }
        streamContext.loggingPort.warning(
            streamContext.tag,
            "Responses stream ended unexpectedly while $context; recovering with streamed output " +
                "provider=${streamContext.provider} model=${streamContext.modelId}",
        )
        return state
    }

    fun toStreamedResponse(state: StreamState): StreamedOpenAiResponse =
        StreamedOpenAiResponse(
            emittedAny = state.emittedAny,
            functionCalls = state.toolCallRequests.toList(),
            responseId = state.responseId,
            providerToolCallIds = state.providerToolCallIds.toList(),
            providerToolItemIds = state.providerToolItemIds.toList(),
            assistantMessageText = state.streamedAssistantMessage.toString(),
            inputTokens = state.inputTokens,
            outputTokens = state.outputTokens,
            reasoningTokens = state.reasoningTokens,
            cacheReadInputTokens = state.cacheReadInputTokens,
            cacheWriteInputTokens = state.cacheWriteInputTokens,
        )
}
