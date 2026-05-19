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
    private val chatId: ChatId?,
    private val userMessageId: MessageId?,
    private val assistantMessageId: MessageId?,
    private val emitEvent: suspend (InferenceEvent) -> Unit,
) {
    /**
     * Dispatches a [ResponseStreamEvent] to the appropriate handler method and returns
     * the updated [StreamState].
     *
     * Covers all discriminator branches exposed by `openai-java` [ResponseStreamEvent] for SDK
     * 4.32.x. Lifecycle and tool-progress envelopes are no-ops (no log); unexpected shapes fall
     * through to [handleUnexpectedStreamEvent] with a truncated payload summary.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    suspend fun handleEvent(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState =
        when {
            event.isOutputTextDelta() -> handleOutputTextDelta(event, state)
            event.isOutputTextDone() -> handleOutputTextDone(event, state)
            event.isReasoningTextDelta() -> handleReasoningTextDelta(event, state)
            event.isReasoningTextDone() -> handleReasoningTextDone(event, state)
            event.isReasoningSummaryTextDelta() -> handleReasoningSummaryTextDelta(event, state)
            event.isReasoningSummaryTextDone() -> handleReasoningSummaryTextDone(event, state)
            event.isRefusalDelta() -> handleRefusalDelta(event, state)
            event.isRefusalDone() -> handleRefusalDone(event, state)
            event.isFunctionCallArgumentsDone() -> handleFunctionCallArgumentsDone(event, state)
            event.isOutputItemAdded() -> handleOutputItemAdded(event, state)
            event.isOutputItemDone() -> handleOutputItemDone(event, state)
            event.isCompleted() -> handleCompleted(event, state)
            event.isIncomplete() -> handleIncomplete(event, state)
            event.isFailed() -> handleFailed(event, state)
            event.isError() -> handleError(event, state)
            event.isCreated() -> state
            event.isInProgress() -> state
            event.isQueued() -> state
            event.isContentPartAdded() -> state
            event.isContentPartDone() -> state
            event.isReasoningSummaryPartAdded() -> state
            event.isReasoningSummaryPartDone() -> state
            event.isFunctionCallArgumentsDelta() -> state
            event.isCustomToolCallInputDelta() -> state
            event.isCustomToolCallInputDone() -> state
            event.isOutputTextAnnotationAdded() -> state
            event.isAudioDelta() -> state
            event.isAudioDone() -> state
            event.isAudioTranscriptDelta() -> state
            event.isAudioTranscriptDone() -> state
            event.isWebSearchCallCompleted() -> state
            event.isWebSearchCallInProgress() -> state
            event.isWebSearchCallSearching() -> state
            event.isFileSearchCallCompleted() -> state
            event.isFileSearchCallInProgress() -> state
            event.isFileSearchCallSearching() -> state
            event.isCodeInterpreterCallCodeDelta() -> state
            event.isCodeInterpreterCallCodeDone() -> state
            event.isCodeInterpreterCallCompleted() -> state
            event.isCodeInterpreterCallInProgress() -> state
            event.isCodeInterpreterCallInterpreting() -> state
            event.isImageGenerationCallCompleted() -> state
            event.isImageGenerationCallGenerating() -> state
            event.isImageGenerationCallInProgress() -> state
            event.isImageGenerationCallPartialImage() -> state
            event.isMcpCallArgumentsDelta() -> state
            event.isMcpCallArgumentsDone() -> state
            event.isMcpCallCompleted() -> state
            event.isMcpCallFailed() -> state
            event.isMcpCallInProgress() -> state
            event.isMcpListToolsCompleted() -> state
            event.isMcpListToolsFailed() -> state
            event.isMcpListToolsInProgress() -> state
            else -> handleUnexpectedStreamEvent(event, state)
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
            "Responses stream ended unexpectedly while $context; recovering with streamed output provider=$provider model=$modelId",
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

    private suspend fun handleOutputTextDone(
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
                "Responses stream emitted output_text.done fallback model=$modelId key=$key chars=${fallbackText.length}",
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

    private suspend fun handleReasoningTextDone(
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
                "Responses stream emitted reasoning_text.done fallback model=$modelId key=$key chars=${fallbackText.length}",
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

    private suspend fun handleReasoningSummaryTextDone(
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
                "Responses stream emitted reasoning_summary_text.done fallback model=$modelId key=$key chars=${fallbackText.length}",
            )
            state.emittedAny = true
            emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
        }
        return state
    }

    private suspend fun handleRefusalDelta(
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

    private suspend fun handleRefusalDone(
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
                "Responses stream emitted refusal.done fallback model=$modelId key=$key chars=${fallbackText.length}",
            )
            state.streamedAssistantMessage.append(fallbackText)
            state.emittedAny = true
            emitEvent(InferenceEvent.PartialResponse(fallbackText, modelType))
        }
        return state
    }

    private fun handleFunctionCallArgumentsDone(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val functionCallDone = event.functionCallArgumentsDone().get()
        val cachedFunctionCall = state.capturedFunctionCallByKey[functionCallDone.itemId()]
        val toolName =
            runCatching { functionCallDone.name() }
                .getOrElse { error ->
                    cachedFunctionCall?.toolName?.let { return@getOrElse it }
                    loggingPort.warning(
                        tag,
                        @Suppress("MaxLineLength")
                        "Function call done event missing name provider=$provider model=$modelId; defaulting to ${ToolDefinition.WEB_SEARCH.name}. error=${error.message ?: error::class.java.simpleName}",
                    )
                    ToolDefinition.WEB_SEARCH.name
                }
        val argumentsJson =
            runCatching { functionCallDone.arguments() }
                .getOrElse { error ->
                    cachedFunctionCall?.argumentsJson?.let { return@getOrElse it }
                    throw IllegalStateException(
                        "Function call done event missing arguments provider=$provider model=$modelId",
                        error,
                    )
                }
        state.toolCallRequests +=
            ToolCallRequest(
                toolName = toolName,
                argumentsJson = argumentsJson,
                provider = provider,
                modelType = modelType,
                chatId = chatId,
                userMessageId = userMessageId,
                assistantMessageId = assistantMessageId,
            )
        state.providerToolCallIds += cachedFunctionCall?.callId ?: functionCallDone.itemId()
        state.providerToolItemIds += cachedFunctionCall?.itemId ?: functionCallDone.itemId()
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
        state.responseId =
            event
                .completed()
                .get()
                .response()
                .id()
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
            "Responses stream completed model=$modelId outputTextDeltas=${state.outputTextDeltaCount} reasoningTextDeltas=${state.reasoningTextDeltaCount} reasoningSummaryDeltas=${state.reasoningSummaryDeltaCount} refusalTextDeltas=${state.refusalTextDeltaCount}",
        )
        return state
    }

    private fun handleIncomplete(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val incompleteEvent = event.incomplete().get()
        val response = incompleteEvent.response()
        state.responseId = response.id()
        val details = response.incompleteDetails().map { it.toString() }.orElse("none")
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
            "Responses stream incomplete model=$modelId responseId=${state.responseId} incompleteDetails=$details",
        )
        return state
    }

    private suspend fun handleFailed(
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
        if (shouldRecoverFromStreamTermination(state.emittedAny, errorMsg)) {
            loggingPort.warning(
                tag,
                "Responses stream reported recoverable API failure after partial output provider=$provider model=$modelId error=$errorMsg",
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
                "Responses stream reported recoverable stream error after partial output provider=$provider model=$modelId error=$errorMsg",
            )
            return state
        }
        throw RuntimeException("Stream Error: $errorMsg")
    }

    private fun handleUnexpectedStreamEvent(
        event: ResponseStreamEvent,
        state: StreamState,
    ): StreamState {
        val summary = truncateForLog(runCatching { event.toString() }.getOrElse { "<toString failed>" })
        loggingPort.warning(
            tag,
            @Suppress("MaxLineLength")
            "Responses stream unrecognized event (update handler for new SDK event?) model=$modelId summary=$summary",
        )
        return state
    }

    private fun truncateForLog(
        text: String,
        maxLen: Int = 400,
    ): String = if (text.length <= maxLen) text else text.take(maxLen) + "…"

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

    private fun streamPartKey(
        itemId: String,
        outputIndex: Long,
        contentIndex: Long,
    ): String = "$itemId:$outputIndex:$contentIndex"

    private fun streamItemKey(
        itemId: String,
        outputIndex: Long,
    ): String = "$itemId:$outputIndex"

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

    // ── Function call capture ────────────────────────────────────────────────

    private fun captureFunctionCallMetadata(
        item: com.openai.models.responses.ResponseOutputItem,
        sink: MutableMap<String, CapturedFunctionCall>,
    ) {
        if (!item.isFunctionCall()) return
        val functionCall = item.asFunctionCall()
        val itemId = functionCall.id().orElse(functionCall.callId())
        val captured =
            CapturedFunctionCall(
                itemId = itemId,
                callId = functionCall.callId(),
                toolName = functionCall.name(),
                argumentsJson = functionCall.arguments(),
            )
        sink[itemId] = captured
        sink[functionCall.callId()] = captured
    }
}
