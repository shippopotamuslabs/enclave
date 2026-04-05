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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

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
        } catch (e: Exception) {
            var errorMsg = e.message ?: "Unknown error"
            if (e is OpenAIServiceException) {
                val bodyStr = serializeBody(e.body())
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
            while (iterator.hasNext()) {
                val event = iterator.next()
                if (event.isOutputTextDelta()) {
                    val text = event.outputTextDelta().get().delta()
                    outputTextDeltaCount++
                    loggingPort.debug(
                        tag,
                        "Responses stream output_text_delta[$outputTextDeltaCount] model=$modelId preview=${previewChunk(text)}"
                    )
                    emitEvent(InferenceEvent.PartialResponse(text, modelType))
                } else if (event.isReasoningTextDelta()) {
                    val text = event.reasoningTextDelta().get().delta()
                    reasoningTextDeltaCount++
                    loggingPort.debug(
                        tag,
                        "Responses stream reasoning_text_delta[$reasoningTextDeltaCount] model=$modelId preview=${previewChunk(text)}"
                    )
                    emitEvent(InferenceEvent.Thinking(text, modelType))
                } else if (event.isReasoningSummaryTextDelta()) {
                    val text = event.reasoningSummaryTextDelta().get().delta()
                    reasoningSummaryDeltaCount++
                    loggingPort.debug(
                        tag,
                        "Responses stream reasoning_summary_text_delta[$reasoningSummaryDeltaCount] model=$modelId preview=${previewChunk(text)}"
                    )
                    emitEvent(InferenceEvent.Thinking(text, modelType))
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
            while (iterator.hasNext()) {
                val event = iterator.next()
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
                        loggingPort.debug(
                            tag,
                            "Chat stream content_delta[$outputTextDeltaCount] model=$modelId preview=${previewChunk(text)}"
                        )
                        emitEvent(InferenceEvent.PartialResponse(text, modelType))
                    }
                }

                val reasoningText = delta._additionalProperties()["reasoning_content"]
                    ?.convert(String::class.java)
                    ?.takeIf { it.isNotEmpty() }
                if (reasoningText != null) {
                    loggingPort.debug(
                        tag,
                        "Chat stream reasoning_content_delta model=$modelId preview=${previewChunk(reasoningText)}"
                    )
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
            throwable.code().orElse(null)?.let {
                append(" code=")
                append(it)
            }
            throwable.type().orElse(null)?.let {
                append(" type=")
                append(it)
            }
            throwable.param().orElse(null)?.let {
                append(" param=")
                append(it)
            }
            val body = serializeBody(throwable.body())
            if (body.isNotBlank()) {
                append(" body=")
                append(truncateForLogs(body))
            }
        }
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
