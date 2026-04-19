package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest

/**
 * Tracks the mutable accumulator for an OpenAI Responses API streaming session.
 *
 * Maintains StringBuilder fields for efficient string concatenation across hundreds
 * of delta events. These mutable builders are mutated in place rather than via copy()
 * since they are only accessed sequentially in the streaming loop.
 */
data class StreamState(
    var emittedAny: Boolean = false,
    var outputTextDeltaCount: Int = 0,
    var reasoningTextDeltaCount: Int = 0,
    var reasoningSummaryDeltaCount: Int = 0,
    val toolCallRequests: MutableList<ToolCallRequest> = mutableListOf(),
    var responseId: String? = null,
    val providerToolCallIds: MutableList<String> = mutableListOf(),
    val providerToolItemIds: MutableList<String> = mutableListOf(),
    val outputTextByPart: MutableMap<String, StringBuilder> = mutableMapOf(),
    val reasoningTextByPart: MutableMap<String, StringBuilder> = mutableMapOf(),
    val reasoningSummaryByPart: MutableMap<String, StringBuilder> = mutableMapOf(),
    val streamedAssistantMessage: StringBuilder = StringBuilder(),
    val capturedFunctionCallByKey: MutableMap<String, CapturedFunctionCall> = mutableMapOf(),
)

/**
 * Captured function-call metadata from output_item_added and output_item_done events.
 */
data class CapturedFunctionCall(
    val itemId: String,
    val callId: String,
    val toolName: String,
    val argumentsJson: String,
)

/**
 * Result of an OpenAI Responses API streaming session, returned to callers
 * of [OpenAiResponseStreamHandler.toStreamedResponse].
 *
 * [functionCalls] contains all function calls detected during the stream,
 * preserving order for parallel tool calls. [functionCall] returns the first
 * (or only) call for backward compatibility with single-call usage.
 */
data class StreamedOpenAiResponse(
    val emittedAny: Boolean,
    val functionCalls: List<ToolCallRequest>,
    val responseId: String?,
    val providerToolCallIds: List<String>,
    val providerToolItemIds: List<String>,
    val assistantMessageText: String,
) {
    /** Single-call convenience: the first function call, or null if none. */
    val functionCall: ToolCallRequest? get() = functionCalls.firstOrNull()

    /** Single-call convenience: the first provider tool call ID, or null if none. */
    val providerToolCallId: String? get() = providerToolCallIds.firstOrNull()

    /** Single-call convenience: the first provider tool item ID, or null if none. */
    val providerToolItemId: String? get() = providerToolItemIds.firstOrNull()
}