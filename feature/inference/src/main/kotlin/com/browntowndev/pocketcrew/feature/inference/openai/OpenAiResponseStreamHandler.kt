package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.models.responses.ResponseStreamEvent

/**
 * Handles individual OpenAI Responses API stream events.
 *
 * Each event type (output_text_delta, reasoning_text_done, function_call_arguments_done, etc.)
 * is processed by a dedicated handler method that mutates the [StreamState] accumulator.
 *
 * The class is stateless with respect to streaming — it receives the current [StreamState],
 * applies the event, and returns the updated state. Event emission is a side-effect handled via
 * the injected `emitEvent` callback.
 */
internal class OpenAiResponseStreamHandler(
    private val provider: String,
    private val modelId: String,
    private val modelType: ModelType,
    private val loggingPort: LoggingPort,
    private val tag: String,
    private val allowToolCall: Boolean,
    private val chatId: ChatId?,
    private val userMessageId: MessageId?,
    private val emitEvent: suspend (InferenceEvent) -> Unit,
) {

    /**
     * Dispatches a [ResponseStreamEvent] to the appropriate handler method and returns
     * the updated [StreamState].
     */
    suspend fun handleEvent(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState = when {
        event.isOutputTextDelta() -> handleOutputTextDelta(event, state)
        event.isOutputTextDone() -> handleOutputTextDone(event, state)
        event.isReasoningTextDelta() -> handleReasoningTextDelta(event, state)
        event.isReasoningTextDone() -> handleReasoningTextDone(event, state)
        event.isReasoningSummaryTextDelta() -> handleReasoningSummaryTextDelta(event, state)
        event.isReasoningSummaryTextDone() -> handleReasoningSummaryTextDone(event, state)
        event.isFunctionCallArgumentsDone() -> handleFunctionCallArgumentsDone(event, state)
        event.isOutputItemAdded() -> handleOutputItemAdded(event, state)
        event.isOutputItemDone() -> handleOutputItemDone(event, state)
        event.isCompleted() -> handleCompleted(event, state)
        event.isFailed() -> handleFailed(event, state)
        event.isError() -> handleError(event, state)
        else -> handleUnknownEvent(event, state)
    }

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
        if (!shouldRecoverFromStreamTermination(state.emittedAny, message)) {
            return null
        }
        loggingPort.warning(
            tag,
            "Responses stream ended unexpectedly while $context; recovering with streamed output provider=$provider model=$modelId"
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
        )

    // ── Per-event handlers ──────────────────────────────────────────────────

    private suspend fun handleOutputTextDelta(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val outputTextDelta = event.outputTextDelta().get()
        val text = outputTextDelta.delta()
        val key = streamPartKey(
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

    private suspend fun handleOutputTextDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val outputTextDone = event.outputTextDone().get()
        val key = streamPartKey(
            itemId = outputTextDone.itemId(),
            outputIndex = outputTextDone.outputIndex(),
            contentIndex = outputTextDone.contentIndex(),
        )
        val fallbackText = resolveNovelStreamText(
            parts = state.outputTextByPart,
            key = key,
            finalizedText = outputTextDone.text(),
        )
        if (fallbackText.isNotEmpty()) {
            loggingPort.debug(
                tag,
                "Responses stream emitted output_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
            )
            state.streamedAssistantMessage.append(fallbackText)
            state.emittedAny = true
            emitEvent(InferenceEvent.PartialResponse(fallbackText, modelType))
        }
        return state
    }

    private suspend fun handleReasoningTextDelta(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val reasoningTextDelta = event.reasoningTextDelta().get()
        val text = reasoningTextDelta.delta()
        val key = streamPartKey(
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

    private suspend fun handleReasoningTextDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val reasoningTextDone = event.reasoningTextDone().get()
        val key = streamPartKey(
            itemId = reasoningTextDone.itemId(),
            outputIndex = reasoningTextDone.outputIndex(),
            contentIndex = reasoningTextDone.contentIndex(),
        )
        val fallbackText = resolveNovelStreamText(
            parts = state.reasoningTextByPart,
            key = key,
            finalizedText = reasoningTextDone.text(),
        )
        if (fallbackText.isNotEmpty()) {
            loggingPort.debug(
                tag,
                "Responses stream emitted reasoning_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
            )
            state.emittedAny = true
            emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
        }
        return state
    }

    private suspend fun handleReasoningSummaryTextDelta(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val reasoningSummaryTextDelta = event.reasoningSummaryTextDelta().get()
        val text = reasoningSummaryTextDelta.delta()
        val key = streamItemKey(
            itemId = reasoningSummaryTextDelta.itemId(),
            outputIndex = reasoningSummaryTextDelta.outputIndex(),
        )
        appendStreamDelta(state.reasoningSummaryByPart, key, text)
        state.emittedAny = true
        state.reasoningSummaryDeltaCount++
        emitEvent(InferenceEvent.Thinking(text, modelType))
        return state
    }

    private suspend fun handleReasoningSummaryTextDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val reasoningSummaryTextDone = event.reasoningSummaryTextDone().get()
        val key = streamItemKey(
            itemId = reasoningSummaryTextDone.itemId(),
            outputIndex = reasoningSummaryTextDone.outputIndex(),
        )
        val fallbackText = resolveNovelStreamText(
            parts = state.reasoningSummaryByPart,
            key = key,
            finalizedText = reasoningSummaryTextDone.text(),
        )
        if (fallbackText.isNotEmpty()) {
            loggingPort.debug(
                tag,
                "Responses stream emitted reasoning_summary_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
            )
            state.emittedAny = true
            emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
        }
        return state
    }

    private fun handleFunctionCallArgumentsDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val functionCallDone = event.functionCallArgumentsDone().get()
        val cachedFunctionCall = state.capturedFunctionCallByKey[functionCallDone.itemId()]
        val toolName = runCatching { functionCallDone.name() }
            .getOrElse { error ->
                cachedFunctionCall?.toolName?.let { return@getOrElse it }
                loggingPort.warning(
                    tag,
                    "Function call done event missing name provider=$provider model=$modelId; defaulting to ${ToolDefinition.TAVILY_WEB_SEARCH.name}. error=${error.message ?: error::class.java.simpleName}"
                )
                ToolDefinition.TAVILY_WEB_SEARCH.name
            }
        val argumentsJson = runCatching { functionCallDone.arguments() }
            .getOrElse { error ->
                cachedFunctionCall?.argumentsJson?.let { return@getOrElse it }
                throw IllegalStateException(
                    "Function call done event missing arguments provider=$provider model=$modelId",
                    error
                )
            }
        state.toolCallRequests += ToolCallRequest(
            toolName = toolName,
            argumentsJson = argumentsJson,
            provider = provider,
            modelType = modelType,
            chatId = chatId,
            userMessageId = userMessageId,
        )
        state.providerToolCallIds += cachedFunctionCall?.callId ?: functionCallDone.itemId()
        state.providerToolItemIds += cachedFunctionCall?.itemId ?: functionCallDone.itemId()
        if (!allowToolCall) {
            throw IllegalStateException("Search skill recursion limit exceeded")
        }
        return state
    }

    private fun handleOutputItemAdded(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        captureFunctionCallMetadata(event.outputItemAdded().get().item(), state.capturedFunctionCallByKey)
        return state
    }

    private fun handleOutputItemDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        captureFunctionCallMetadata(event.outputItemDone().get().item(), state.capturedFunctionCallByKey)
        return state
    }

    private fun handleCompleted(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        state.responseId = event.completed().get().response().id()
        loggingPort.debug(
            tag,
            "Responses stream completed model=$modelId outputTextDeltas=${state.outputTextDeltaCount} reasoningTextDeltas=${state.reasoningTextDeltaCount} reasoningSummaryDeltas=${state.reasoningSummaryDeltaCount}"
        )
        return state
    }

    private suspend fun handleFailed(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val failedEvent = event.failed().get()
        val errorMsg = failedEvent.response().error().map { it.message() }.orElse("Unknown API error")
        if (shouldRecoverFromStreamTermination(state.emittedAny, errorMsg)) {
            loggingPort.warning(
                tag,
                "Responses stream reported recoverable API failure after partial output provider=$provider model=$modelId error=$errorMsg"
            )
            return state
        }
        throw RuntimeException("API Error: $errorMsg")
    }

    private suspend fun handleError(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val errorEvent = event.error().get()
        val errorMsg = errorEvent.message()
        if (shouldRecoverFromStreamTermination(state.emittedAny, errorMsg)) {
            loggingPort.warning(
                tag,
                "Responses stream reported recoverable stream error after partial output provider=$provider model=$modelId error=$errorMsg"
            )
            return state
        }
        throw RuntimeException("Stream Error: $errorMsg")
    }

    private fun handleUnknownEvent(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        loggingPort.debug(
            tag,
            "Responses stream ignored event model=$modelId eventType=${detectResponseEventType(event)}"
        )
        return state
    }

    // ── Stream text helpers ─────────────────────────────────────────────────

    private fun appendStreamDelta(
        parts: MutableMap<String, StringBuilder>,
        key: String,
        text: String,
    ) {
        if (text.isEmpty()) return
        parts.getOrPut(key) { StringBuilder() }.append(text)
    }

    private fun resolveNovelStreamText(
        parts: MutableMap<String, StringBuilder>,
        key: String,
        finalizedText: String,
    ): String {
        if (finalizedText.isEmpty()) return ""
        val priorText = parts[key]?.toString().orEmpty()
        val novelText = novelStreamSuffix(priorText, finalizedText)
        parts[key] = StringBuilder(finalizedText)
        return novelText
    }

    internal fun novelStreamSuffix(
        streamedText: String,
        finalizedText: String,
    ): String {
        if (finalizedText.isEmpty()) return ""
        if (streamedText.isEmpty()) return finalizedText
        if (streamedText == finalizedText) return ""
        val commonPrefixLength = streamedText.commonPrefixWith(finalizedText).length
        return finalizedText.drop(commonPrefixLength)
    }

    private fun streamPartKey(itemId: String, outputIndex: Long, contentIndex: Long): String =
        "$itemId:$outputIndex:$contentIndex"

    private fun streamItemKey(itemId: String, outputIndex: Long): String =
        "$itemId:$outputIndex"

    // ── Error recovery ──────────────────────────────────────────────────────

    private fun shouldRecoverFromStreamTermination(
        emittedAny: Boolean,
        message: String?,
    ): Boolean = emittedAny && isRecoverableStreamTermination(message)

    private fun isRecoverableStreamTermination(message: String?): Boolean {
        val normalized = message?.lowercase() ?: return false
        return normalized.contains("internal stream ended unexpectedly") ||
            normalized.contains("stream ended unexpectedly")
    }

    // ── Event type detection ─────────────────────────────────────────────────

    private fun detectResponseEventType(event: ResponseStreamEvent): String =
        when {
            event.isCreated() -> "created"
            event.isInProgress() -> "in_progress"
            event.isOutputItemAdded() -> "output_item_added"
            event.isOutputItemDone() -> "output_item_done"
            event.isContentPartAdded() -> "content_part_added"
            event.isContentPartDone() -> "content_part_done"
            event.isReasoningSummaryPartAdded() -> "reasoning_summary_part_added"
            event.isReasoningSummaryPartDone() -> "reasoning_summary_part_done"
            event.isReasoningTextDone() -> "reasoning_text_done"
            event.isReasoningSummaryTextDone() -> "reasoning_summary_text_done"
            event.isOutputTextDone() -> "output_text_done"
            event.isIncomplete() -> "incomplete"
            event.isQueued() -> "queued"
            else -> "other"
        }

    // ── Function call capture ────────────────────────────────────────────────

    private fun captureFunctionCallMetadata(
        item: com.openai.models.responses.ResponseOutputItem,
        sink: MutableMap<String, CapturedFunctionCall>,
    ) {
        if (!item.isFunctionCall()) return
        val functionCall = item.asFunctionCall()
        val itemId = functionCall.id().orElse(functionCall.callId())
        val captured = CapturedFunctionCall(
            itemId = itemId,
            callId = functionCall.callId(),
            toolName = functionCall.name(),
            argumentsJson = functionCall.arguments(),
        )
        sink[itemId] = captured
        sink[functionCall.callId()] = captured
    }
}