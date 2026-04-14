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
    var toolCallRequest: ToolCallRequest? = null,
    var responseId: String? = null,
    var providerToolCallId: String? = null,
    var providerToolItemId: String? = null,
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
 */
data class StreamedOpenAiResponse(
    val emittedAny: Boolean,
    val functionCall: ToolCallRequest?,
    val responseId: String?,
    val providerToolCallId: String?,
    val providerToolItemId: String?,
    val assistantMessageText: String,
)