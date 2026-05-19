package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.ResponseStreamEvent

internal fun OpenAiStreamHandlerContext.handleFunctionCallArgumentsDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    val functionCallDone = event.functionCallArgumentsDone().get()
    val cachedFunctionCall = state.capturedFunctionCallByKey[functionCallDone.itemId()]
    val toolName =
        runCatching { functionCallDone.name() }
            .getOrElse { error ->
                cachedFunctionCall?.toolName?.let { return@getOrElse it }
                val defaultTool = ToolDefinition.WEB_SEARCH.name
                val errorDetail = error.message ?: error::class.java.simpleName
                loggingPort.warning(
                    tag,
                    "Function call done event missing name provider=$provider model=$modelId; " +
                        "defaulting to $defaultTool. error=$errorDetail",
                )
                defaultTool
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

internal fun OpenAiStreamHandlerContext.handleOutputItemAdded(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    captureFunctionCallMetadata(event.outputItemAdded().get().item(), state.capturedFunctionCallByKey)
    return state
}

internal fun OpenAiStreamHandlerContext.handleOutputItemDone(
    event: ResponseStreamEvent,
    state: StreamState,
): StreamState {
    captureFunctionCallMetadata(event.outputItemDone().get().item(), state.capturedFunctionCallByKey)
    return state
}

private fun captureFunctionCallMetadata(
    item: ResponseOutputItem,
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
