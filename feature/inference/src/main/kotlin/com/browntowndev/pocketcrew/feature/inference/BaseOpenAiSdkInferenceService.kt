package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
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
    protected val loggingPort: LoggingPort,
    internal val toolExecutor: ToolExecutorPort? = null,
) : LlmInferencePort {

    protected data class StreamedOpenAiResponse(
        val emittedAny: Boolean,
        val functionCall: ToolCallRequest?,
        val responseId: String?,
        val providerToolCallId: String?,
        val providerToolItemId: String?,
        val assistantMessageText: String,
    )

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
            if (e is IllegalArgumentException || e is IllegalStateException) {
                loggingPort.error(tag, "Tool request failed. ${describeException(e)}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            }
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

    protected open fun mapToolingResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
    ): ResponseCreateParams =
        OpenAiRequestMapper.mapToResponseParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
        )

    protected open fun mapToolingFollowUpResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        toolResultJson: String,
    ): ResponseCreateParams {
        val builder = ResponseCreateParams.builder()
            .model(modelId)
            .previousResponseId(initialResponse.responseId ?: throw IllegalStateException("Missing previous response id for tool call"))
            .inputOfResponse(
                listOf(
                    ResponseInputItem.ofFunctionCallOutput(
                        ResponseInputItem.FunctionCallOutput.builder()
                            .callId(initialResponse.providerToolCallId ?: throw IllegalStateException("Missing provider tool call id for tool call"))
                            .output(toolResultJson)
                            .build()
                    )
                )
            )
        requestHistory
            .filter { it.role == Role.SYSTEM }
            .joinToString(separator = "\n\n", transform = ChatMessage::content)
            .takeIf(String::isNotBlank)
            ?.let(builder::instructions)
        return builder.build()
    }

    protected suspend fun executeToolingPrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        val executor = requireNotNull(toolExecutor) {
            "Tool executor not configured for provider=$provider"
        }

        logImagePayloads(options)

        val initialParams = mapToolingResponseParams(
            prompt = prompt,
            options = options,
            requestHistory = requestHistory,
        )
        val initialResponse = streamResponses(
            params = initialParams,
            allowToolCall = true,
            chatId = options.chatId,
            userMessageId = options.userMessageId,
            emitEvent = emitEvent,
        )
        val toolCall = initialResponse.functionCall

        if (toolCall == null) {
            loggingPort.info(
                tag,
                "Tool loop complete without tool call provider=$provider model=$modelId modelType=$modelType"
            )
            if (!initialResponse.emittedAny) {
                emitEvent(InferenceEvent.Finished(modelType))
            }
            return
        }

        ToolEnvelopeParser.requireSupportedTool(toolCall.toolName)
        val toolArg = when (toolCall.toolName) {
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> ToolEnvelopeParser.extractRequiredQuestion(toolCall.argumentsJson)
            else -> ToolEnvelopeParser.extractRequiredQuery(toolCall.argumentsJson)
        }
        loggingPort.info(
            tag,
            "Tool call detected provider=$provider model=$modelId tool=${toolCall.toolName} arg=$toolArg"
        )
        val toolResult = executor.execute(toolCall)
        loggingPort.info(
            tag,
            "Tool call completed provider=$provider model=$modelId tool=${toolCall.toolName} resultChars=${toolResult.resultJson.length}"
        )

        val followUpParams = mapToolingFollowUpResponseParams(
            prompt = prompt,
            options = options,
            requestHistory = requestHistory,
            initialResponse = initialResponse,
            toolResultJson = toolResult.resultJson,
        )

        val followUpResponse = streamResponses(
            params = followUpParams,
            allowToolCall = false,
            chatId = options.chatId,
            userMessageId = options.userMessageId,
            emitEvent = emitEvent,
        )
        if (followUpResponse.functionCall != null) {
            loggingPort.warning(
                tag,
                "Recursive tool call detected provider=$provider model=$modelId tool=${followUpResponse.functionCall.toolName}"
            )
            throw IllegalStateException("Search skill recursion limit exceeded")
        }
    }

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
        allowToolCall: Boolean = false,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): StreamedOpenAiResponse {
        logResponsesRequest(params)
        client.responses().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var finishedEmitted = false
            var emittedAny = false
            var outputTextDeltaCount = 0
            var reasoningTextDeltaCount = 0
            var reasoningSummaryDeltaCount = 0
            var toolCallRequest: ToolCallRequest? = null
            var responseId: String? = null
            var providerToolCallId: String? = null
            var providerToolItemId: String? = null
            val outputTextByPart = mutableMapOf<String, StringBuilder>()
            val reasoningTextByPart = mutableMapOf<String, StringBuilder>()
            val reasoningSummaryByPart = mutableMapOf<String, StringBuilder>()
            val streamedAssistantMessage = StringBuilder()
            val capturedFunctionCallByKey = mutableMapOf<String, CapturedFunctionCall>()
            loop@ while (currentCoroutineContext().isActive) {
                val hasNext = try {
                    runInterruptible { iterator.hasNext() }
                } catch (e: RuntimeException) {
                    if (!shouldRecoverFromStreamTermination(allowToolCall = allowToolCall, emittedAny = emittedAny, message = e.message)) {
                        throw e
                    }
                    loggingPort.warning(
                        tag,
                        "Responses stream ended unexpectedly while checking next event; recovering with streamed output provider=$provider model=$modelId"
                    )
                    break@loop
                }
                if (!hasNext) {
                    break@loop
                }
                val event = try {
                    runInterruptible { iterator.next() }
                } catch (e: RuntimeException) {
                    if (!shouldRecoverFromStreamTermination(allowToolCall = allowToolCall, emittedAny = emittedAny, message = e.message)) {
                        throw e
                    }
                    loggingPort.warning(
                        tag,
                        "Responses stream ended unexpectedly while reading next event; recovering with streamed output provider=$provider model=$modelId"
                    )
                    break@loop
                }
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
                    emittedAny = true
                    streamedAssistantMessage.append(text)
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
                        emittedAny = true
                        streamedAssistantMessage.append(fallbackText)
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
                    emittedAny = true
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
                        emittedAny = true
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
                    emittedAny = true
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
                        emittedAny = true
                        emitEvent(InferenceEvent.Thinking(fallbackText, modelType))
                    }
                } else if (event.isFunctionCallArgumentsDone()) {
                    val functionCallDone = event.functionCallArgumentsDone().get()
                    val cachedFunctionCall = capturedFunctionCallByKey[functionCallDone.itemId()]
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
                    toolCallRequest = ToolCallRequest(
                        toolName = toolName,
                        argumentsJson = argumentsJson,
                        provider = provider,
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                    )
                    providerToolCallId = cachedFunctionCall?.callId ?: functionCallDone.itemId()
                    providerToolItemId = cachedFunctionCall?.itemId ?: functionCallDone.itemId()
                    if (!allowToolCall) {
                        throw IllegalStateException("Search skill recursion limit exceeded")
                    }
                } else if (event.isOutputItemAdded()) {
                    val outputItemAdded = event.outputItemAdded().get()
                    captureFunctionCallMetadata(
                        item = outputItemAdded.item(),
                        sink = capturedFunctionCallByKey,
                    )
                } else if (event.isOutputItemDone()) {
                    val outputItemDone = event.outputItemDone().get()
                    captureFunctionCallMetadata(
                        item = outputItemDone.item(),
                        sink = capturedFunctionCallByKey,
                    )
                } else if (event.isCompleted()) {
                    responseId = event.completed().get().response().id()
                    loggingPort.debug(
                        tag,
                        "Responses stream completed model=$modelId outputTextDeltas=$outputTextDeltaCount reasoningTextDeltas=$reasoningTextDeltaCount reasoningSummaryDeltas=$reasoningSummaryDeltaCount"
                    )
                    if (!(allowToolCall && toolCallRequest != null)) {
                        emitEvent(InferenceEvent.Finished(modelType))
                    }
                    finishedEmitted = true
                } else if (event.isFailed()) {
                    val failedEvent = event.failed().get()
                    val errorMsg = failedEvent.response().error().map { it.message() }.orElse("Unknown API error")
                    if (shouldRecoverFromStreamTermination(allowToolCall = allowToolCall, emittedAny = emittedAny, message = errorMsg)) {
                        loggingPort.warning(
                            tag,
                            "Responses stream reported recoverable API failure after partial output provider=$provider model=$modelId error=$errorMsg"
                        )
                        break@loop
                    }
                    throw RuntimeException("API Error: $errorMsg")
                } else if (event.isError()) {
                    val errorEvent = event.error().get()
                    val errorMsg = errorEvent.message()
                    if (shouldRecoverFromStreamTermination(allowToolCall = allowToolCall, emittedAny = emittedAny, message = errorMsg)) {
                        loggingPort.warning(
                            tag,
                            "Responses stream reported recoverable stream error after partial output provider=$provider model=$modelId error=$errorMsg"
                        )
                        break@loop
                    }
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
                if (!(allowToolCall && toolCallRequest != null)) {
                    emitEvent(InferenceEvent.Finished(modelType))
                }
            }
            return StreamedOpenAiResponse(
                emittedAny = emittedAny,
                functionCall = toolCallRequest,
                responseId = responseId,
                providerToolCallId = providerToolCallId,
                providerToolItemId = providerToolItemId,
                assistantMessageText = streamedAssistantMessage.toString(),
            )
        }
    }

    private data class CapturedFunctionCall(
        val itemId: String,
        val callId: String,
        val toolName: String,
        val argumentsJson: String,
    )

    private fun captureFunctionCallMetadata(
        item: com.openai.models.responses.ResponseOutputItem,
        sink: MutableMap<String, CapturedFunctionCall>,
    ) {
        if (!item.isFunctionCall()) {
            return
        }
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

    private fun shouldRecoverFromStreamTermination(
        allowToolCall: Boolean,
        emittedAny: Boolean,
        message: String?,
    ): Boolean = !allowToolCall && emittedAny && isRecoverableStreamTermination(message)

    private fun isRecoverableStreamTermination(message: String?): Boolean {
        val normalized = message?.lowercase() ?: return false
        return normalized.contains("internal stream ended unexpectedly") ||
            normalized.contains("stream ended unexpectedly")
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

    protected fun logImagePayloads(options: GenerationOptions) {
        if (options.imageUris.isEmpty()) {
            return
        }
        val payloads = runCatching {
            ImagePayloads.fromUris(options.imageUris)
        }.getOrElse { error ->
            loggingPort.error(
                tag,
                "Failed to load image payloads provider=$provider model=$modelId message=${error.message}",
                error,
            )
            return
        }

        val details = payloads.joinToString(separator = "; ") { payload ->
            "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${payload.sha256.take(8)}"
        }
        loggingPort.info(
            tag,
            "Image payloads provider=$provider model=$modelId count=${payloads.size} $details"
        )
    }

    protected fun truncateForLogs(value: String): String {
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
