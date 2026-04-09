package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import java.util.Optional
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible

abstract class BaseOpenAiSdkInferenceService(
    protected val client: OpenAIClient,
    protected val modelId: String,
    protected val provider: String,
    protected val modelType: ModelType,
    protected val baseUrl: String? = null,
    protected val loggingPort: LoggingPort
) : LlmInferencePort {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val STREAM_PREVIEW_CHARS = 120
    }

    protected abstract val tag: String

    private val conversationHistory = mutableListOf<ChatMessage>()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)
    }

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean
    ): Flow<InferenceEvent> = flow {
        try {
            val requestHistory = buildRequestHistory(options)
            loggingPort.debug(
                tag,
                "sendPrompt start provider=$provider model=$modelId modelType=$modelType closeConversation=$closeConversation reasoningBudget=${options.reasoningBudget} reasoningEffort=${options.reasoningEffort} maxTokens=${options.maxTokens}"
            )
            executePrompt(prompt, options, requestHistory) { emit(it) }

            conversationHistory.add(ChatMessage(Role.USER, prompt))
            if (closeConversation) {
                closeSession()
            }
        } catch (e: CancellationException) {
            loggingPort.debug(tag, "sendPrompt cancelled provider=$provider model=$modelId")
            throw e
        } catch (e: Exception) {
            var errorMsg = e.message ?: "Unknown error"
            if (e is OpenAIServiceException) {
                val bodyStr = safeSerializeBody(e)
                if (bodyStr.isNotBlank() && !errorMsg.contains(bodyStr)) {
                    errorMsg += " | Body: $bodyStr"
                }
            }
            loggingPort.error(tag, "API request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(Exception("API Error ($provider): $errorMsg", e), modelType))
        }
    }.flowOn(Dispatchers.IO)

    protected abstract suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    )

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }

    protected fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            tag,
            "Prepared request history provider=$provider model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}"
        )
        return historyWithSystemPrompt
    }

    protected suspend fun streamResponses(
        params: ResponseCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logResponsesRequest(params)
        client.responses().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var finishedEmitted = false
            var outputTextDeltaCount = 0
            var reasoningTextDeltaCount = 0
            var reasoningSummaryDeltaCount = 0
            val outputTextByPart = mutableMapOf<String, StringBuilder>()
            val reasoningTextByPart = mutableMapOf<String, StringBuilder>()
            val reasoningSummaryByPart = mutableMapOf<String, StringBuilder>()
            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                if (event.isOutputTextDelta()) {
                    val outputTextDelta = event.outputTextDelta().get()
                    val text = outputTextDelta.delta()
                    outputTextDeltaCount++
                    appendStreamDelta(
                        parts = outputTextByPart,
                        key = streamPartKey(
                            itemId = outputTextDelta.itemId(),
                            outputIndex = outputTextDelta.outputIndex(),
                            contentIndex = outputTextDelta.contentIndex()
                        ),
                        text = text
                    )
                    emitEvent(InferenceEvent.PartialResponse(text, modelType))
                } else if (event.isOutputTextDone()) {
                    val outputTextDone = event.outputTextDone().get()
                    val key = streamPartKey(
                        itemId = outputTextDone.itemId(),
                        outputIndex = outputTextDone.outputIndex(),
                        contentIndex = outputTextDone.contentIndex()
                    )
                    val fallbackText = resolveNovelStreamText(
                        parts = outputTextByPart,
                        key = key,
                        finalizedText = outputTextDone.text()
                    )
                    if (fallbackText.isNotEmpty()) {
                        loggingPort.debug(
                            tag,
                            "Responses stream emitted output_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
                        )
                        emitEvent(InferenceEvent.PartialResponse(fallbackText, modelType))
                    }
                } else if (event.isReasoningTextDelta()) {
                    val reasoningTextDelta = event.reasoningTextDelta().get()
                    val text = reasoningTextDelta.delta()
                    reasoningTextDeltaCount++
                    appendStreamDelta(
                        parts = reasoningTextByPart,
                        key = streamPartKey(
                            itemId = reasoningTextDelta.itemId(),
                            outputIndex = reasoningTextDelta.outputIndex(),
                            contentIndex = reasoningTextDelta.contentIndex()
                        ),
                        text = text
                    )
                    emitEvent(InferenceEvent.Thinking(text, modelType))
                } else if (event.isReasoningTextDone()) {
                    val reasoningTextDone = event.reasoningTextDone().get()
                    val key = streamPartKey(
                        itemId = reasoningTextDone.itemId(),
                        outputIndex = reasoningTextDone.outputIndex(),
                        contentIndex = reasoningTextDone.contentIndex()
                    )
                    val fallbackText = resolveNovelStreamText(
                        parts = reasoningTextByPart,
                        key = key,
                        finalizedText = reasoningTextDone.text()
                    )
                    if (fallbackText.isNotEmpty()) {
                        loggingPort.debug(
                            tag,
                            "Responses stream emitted reasoning_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
                        )
                        emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
                    }
                } else if (event.isReasoningSummaryTextDelta()) {
                    val reasoningSummaryTextDelta = event.reasoningSummaryTextDelta().get()
                    val text = reasoningSummaryTextDelta.delta()
                    reasoningSummaryDeltaCount++
                    appendStreamDelta(
                        parts = reasoningSummaryByPart,
                        key = streamItemKey(
                            itemId = reasoningSummaryTextDelta.itemId(),
                            outputIndex = reasoningSummaryTextDelta.outputIndex()
                        ),
                        text = text
                    )
                    emitEvent(InferenceEvent.Thinking(text, modelType))
                } else if (event.isReasoningSummaryTextDone()) {
                    val reasoningSummaryTextDone = event.reasoningSummaryTextDone().get()
                    val key = streamItemKey(
                        itemId = reasoningSummaryTextDone.itemId(),
                        outputIndex = reasoningSummaryTextDone.outputIndex()
                    )
                    val fallbackText = resolveNovelStreamText(
                        parts = reasoningSummaryByPart,
                        key = key,
                        finalizedText = reasoningSummaryTextDone.text()
                    )
                    if (fallbackText.isNotEmpty()) {
                        loggingPort.debug(
                            tag,
                            "Responses stream emitted reasoning_summary_text.done fallback model=$modelId key=$key chars=${fallbackText.length}"
                        )
                        emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
                    }
                } else if (event.isCompleted()) {
                    loggingPort.debug(
                        tag,
                        "Responses stream completed model=$modelId outputTextDeltas=$outputTextDeltaCount reasoningTextDeltas=$reasoningTextDeltaCount reasoningSummaryDeltas=$reasoningSummaryDeltaCount"
                    )
                    emitEvent(InferenceEvent.Finished(modelType))
                    finishedEmitted = true
                } else if (event.isFailed()) {
                    val failedEvent = event.failed().get()
                    val errorMsg = failedEvent.response().error().map { it.message() }.orElse("Unknown API error")
                    throw RuntimeException("API Error: $errorMsg")
                } else if (event.isError()) {
                    val errorEvent = event.error().get()
                    val errorMsg = errorEvent.message()
                    throw RuntimeException("Stream Error: $errorMsg")
                } else {
                    loggingPort.debug(
                        tag,
                        "Responses stream ignored event model=$modelId eventType=${detectResponseEventType(event)}"
                    )
                }
            }
            if (!finishedEmitted) {
                loggingPort.debug(
                    tag,
                    "Responses stream ended without completed event model=$modelId outputTextDeltas=$outputTextDeltaCount reasoningTextDeltas=$reasoningTextDeltaCount reasoningSummaryDeltas=$reasoningSummaryDeltaCount"
                )
                emitEvent(InferenceEvent.Finished(modelType))
            }
        }
    }

    protected suspend fun streamChatCompletions(
        params: ChatCompletionCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logChatRequest(params)
        client.chat().completions().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var finishedEmitted = false
            var outputTextDeltaCount = 0
            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                val choices = event.choices()
                if (choices.isEmpty()) {
                    loggingPort.debug(tag, "Chat stream chunk had no choices model=$modelId")
                    continue
                }

                val choice = choices[0]
                val delta = choice.delta()
                if (delta.content().isPresent) {
                    val text = delta.content().get()
                    if (text.isNotEmpty()) {
                        outputTextDeltaCount++
                        emitEvent(InferenceEvent.PartialResponse(text, modelType))
                    }
                }

                val reasoningText = delta._additionalProperties()["reasoning_content"]
                    ?.convert(String::class.java)
                    ?.takeIf { it.isNotEmpty() }
                if (reasoningText != null) {
                    emitEvent(InferenceEvent.Thinking(reasoningText, modelType))
                }

                if (choice.finishReason().isPresent) {
                    loggingPort.debug(
                        tag,
                        "Chat stream finished model=$modelId finishReason=${choice.finishReason().get()} outputTextDeltas=$outputTextDeltaCount"
                    )
                    emitEvent(InferenceEvent.Finished(modelType))
                    finishedEmitted = true
                }
            }

            if (!finishedEmitted) {
                loggingPort.debug(
                    tag,
                    "Chat stream ended without finishReason model=$modelId outputTextDeltas=$outputTextDeltaCount"
                )
                emitEvent(InferenceEvent.Finished(modelType))
            }
        }
    }

    private fun previewChunk(text: String): String {
        val sanitized = text.replace("\n", "\\n")
        return sanitized.take(STREAM_PREVIEW_CHARS)
    }

    private fun appendStreamDelta(
        parts: MutableMap<String, StringBuilder>,
        key: String,
        text: String
    ) {
        if (text.isEmpty()) {
            return
        }
        parts.getOrPut(key) { StringBuilder() }.append(text)
    }

    private fun resolveNovelStreamText(
        parts: MutableMap<String, StringBuilder>,
        key: String,
        finalizedText: String
    ): String {
        if (finalizedText.isEmpty()) {
            return ""
        }
        val priorText = parts[key]?.toString().orEmpty()
        val novelText = novelStreamSuffix(priorText, finalizedText)
        parts[key] = StringBuilder(finalizedText)
        return novelText
    }

    internal fun novelStreamSuffix(
        streamedText: String,
        finalizedText: String
    ): String {
        if (finalizedText.isEmpty()) {
            return ""
        }
        if (streamedText.isEmpty()) {
            return finalizedText
        }
        if (streamedText == finalizedText) {
            return ""
        }

        val commonPrefixLength = streamedText.commonPrefixWith(finalizedText).length
        return finalizedText.drop(commonPrefixLength)
    }

    private fun streamPartKey(
        itemId: String,
        outputIndex: Long,
        contentIndex: Long
    ): String = "$itemId:$outputIndex:$contentIndex"

    private fun streamItemKey(
        itemId: String,
        outputIndex: Long
    ): String = "$itemId:$outputIndex"

    private fun detectResponseEventType(event: com.openai.models.responses.ResponseStreamEvent): String =
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

    private fun logResponsesRequest(params: ResponseCreateParams) {
        loggingPort.debug(
            tag,
            "Responses API request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(serializeBody(params._body()))}"
        )
    }

    private fun logChatRequest(params: ChatCompletionCreateParams) {
        loggingPort.debug(
            tag,
            "Chat Completions request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(serializeBody(params._body()))}"
        )
    }

    protected fun describeException(throwable: Throwable): String {
        val baseMessage = buildString {
            append("exception=")
            append(throwable::class.java.simpleName)
            throwable.message?.takeIf { it.isNotBlank() }?.let {
                append(" message=")
                append(it)
            }
        }

        if (throwable !is OpenAIServiceException) {
            return baseMessage
        }

        return buildString {
            append(baseMessage)
            append(" status=")
            append(throwable.statusCode())
            safeOptionalField { throwable.code() }?.let {
                append(" code=")
                append(it)
            }
            safeOptionalField { throwable.type() }?.let {
                append(" type=")
                append(it)
            }
            safeOptionalField { throwable.param() }?.let {
                append(" param=")
                append(it)
            }
            val body = safeSerializeBody(throwable)
            if (body.isNotBlank()) {
                append(" body=")
                append(truncateForLogs(body))
            }
        }
    }

    private fun safeOptionalField(read: () -> Optional<String>): String? =
        runCatching { read().orElse(null) }
            .getOrElse { error ->
                loggingPort.warning(
                    tag,
                    "Skipping malformed API error field while formatting exception. fieldError=${error.message ?: error::class.java.simpleName}"
                )
                null
            }

    private fun safeSerializeBody(throwable: OpenAIServiceException): String =
        runCatching { serializeBody(throwable.body()) }
            .getOrElse { error ->
                loggingPort.warning(
                    tag,
                    "Skipping API error body while formatting exception. bodyError=${error.message ?: error::class.java.simpleName}"
                )
                ""
            }

    private fun serializeBody(body: Any): String = body.toString()

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

    internal fun mergeSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }
}
