package com.browntowndev.enclave.feature.inference.openai

import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.openai.models.responses.ResponseOutputItem
import com.openai.models.responses.ResponseOutputMessage

internal fun extractAssistantMessageText(message: ResponseOutputMessage): String =
    message
        .content()
        .mapNotNull { content ->
            when {
                content.isOutputText() -> content.asOutputText().text()
                content.isRefusal() -> content.asRefusal().refusal()
                else -> null
            }
        }.joinToString(separator = "")

internal suspend fun OpenAiStreamHandlerContext.emitNovelAssistantTextIfNeeded(
    state: StreamState,
    finalizedText: String,
    source: String,
) {
    if (finalizedText.isEmpty()) return
    val streamedSoFar = state.streamedAssistantMessage.toString()
    val novelText = novelStreamSuffix(streamedSoFar, finalizedText)
    if (novelText.isEmpty()) return
    loggingPort.debug(
        tag,
        "Responses stream reconciled assistant text from $source model=$modelId " +
            "streamedChars=${streamedSoFar.length} finalizedChars=${finalizedText.length} " +
            "novelChars=${novelText.length}",
    )
    state.streamedAssistantMessage.append(novelText)
    state.emittedAny = true
    emitEvent(InferenceEvent.PartialResponse(novelText, modelType))
}

internal suspend fun OpenAiStreamHandlerContext.reconcileAssistantMessageOutputItem(
    item: ResponseOutputItem,
    state: StreamState,
    source: String,
) {
    if (!item.isMessage()) return
    val finalizedText = extractAssistantMessageText(item.asMessage())
    emitNovelAssistantTextIfNeeded(state, finalizedText, source)
}
