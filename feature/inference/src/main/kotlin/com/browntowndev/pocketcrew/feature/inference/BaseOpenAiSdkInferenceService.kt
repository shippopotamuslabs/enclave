package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.ContextExceededResult
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.ToolContextBudget
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.browntowndev.pocketcrew.domain.util.TavilyResultParser
import com.browntowndev.pocketcrew.feature.inference.openai.OpenAiResponseStreamHandler
import com.browntowndev.pocketcrew.feature.inference.openai.StreamState
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseInputItem
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList
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
    val orchestrator: LlmToolingOrchestrator,
) : LlmInferencePort {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
    }

    protected abstract val tag: String

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

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
            emit(InferenceEvent.Finished(modelType))
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
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean = false,
    ): ResponseCreateParams {
        // Truncate large tool results when context window is known
        val truncationAvailableTokens = options.contextWindow?.let { cw ->
            val budget = ContextWindowPlanner.budgetFor(
                contextWindowTokens = cw,
                options = options,
                modelId = modelId,
                tokenCounter = JTokkitTokenCounter,
            )
            val usedTokens = ContextWindowPlanner.estimatePromptTokens(
                history = requestHistory,
                systemPrompt = null,
                currentPrompt = prompt,
                toolResultPayloads = results.map { it.second },
                modelId = modelId,
                tokenCounter = JTokkitTokenCounter,
            ) ?: 0
            (budget.usablePromptTokens - usedTokens).coerceAtLeast(0)
        }
        val functionCallOutputs = results.map { (toolCall, resultJson) ->
            val truncatedResult = if (truncationAvailableTokens != null && truncationAvailableTokens > 0) {
                NativeToolResultFormatter.truncateForApiContext(
                    resultJson = resultJson,
                    availableTokens = maxOf(truncationAvailableTokens / results.size.coerceAtLeast(1), 100),
                    tokenCounter = JTokkitTokenCounter,
                    modelId = modelId,
                )
            } else {
                resultJson
            }
            // results and initialResponse.functionCalls are in the same order for parallel calls
            val index = initialResponse.functionCalls.indexOf(toolCall)
            val callId = if (index != -1) {
                initialResponse.providerToolCallIds.getOrElse(index) {
                    throw IllegalStateException("Missing provider tool call id at index $index for tool call provider=$provider model=$modelId")
                }
            } else {
                "fallback_${java.util.UUID.randomUUID().toString().replace("-", "")}"
            }

            ResponseInputItem.ofFunctionCallOutput(
                ResponseInputItem.FunctionCallOutput.builder()
                    .callId(callId)
                    .output(truncatedResult)
                    .build()
            )
        }
        val followUpInputs = if (appendStopToolsWarning) {
            functionCallOutputs + ResponseInputItem.ofMessage(
                ResponseInputItem.Message.builder()
                    .role(ResponseInputItem.Message.Role.of("user"))
                    .addInputTextContent(ContextWindowPlanner.STOP_TOOLS_WARNING)
                    .build()
            )
        } else {
            functionCallOutputs
        }
        val builder = ResponseCreateParams.builder()
            .model(modelId)
            .previousResponseId(initialResponse.responseId ?: throw IllegalStateException("Missing previous response id for tool call provider=$provider model=$modelId"))
            .inputOfResponse(followUpInputs)
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
        logImagePayloads(options)
        val toolLoopPayloads = mutableListOf<String>()

        val initialParams = mapToolingResponseParams(
            prompt = prompt,
            options = options,
            requestHistory = requestHistory,
        )

        orchestrator.execute(
            providerName = provider,
            initialParams = initialParams,
            tag = tag,
            maxToolCalls = options.maxToolCalls,
            onInferencePass = { params, allowToolCall ->

                streamResponses(
                    params = params,
                    allowToolCall = allowToolCall,
                    chatId = options.chatId,
                    userMessageId = options.userMessageId,
                    emitEvent = emitEvent,
                )
            },
            onToolCallDetected = { response ->
                val sdkCalls = response.functionCalls
                sdkCalls.ifEmpty {
                    val rawText = response.assistantMessageText
                    val envelope =
                        runCatching { ToolEnvelopeParser.extractLocalToolEnvelope(rawText) }.getOrNull()
                    if (envelope != null) {
                        listOf(
                            ToolCallRequest(
                                toolName = envelope.toolName,
                                argumentsJson = envelope.argumentsJson,
                                provider = provider,
                                modelType = modelType,
                                chatId = options.chatId,
                                userMessageId = options.userMessageId,
                            )
                        )
                    } else {
                        emptyList()
                    }
                }
            },
            onToolResultsMapped = { params, response, results ->
                toolLoopPayloads += results.map { it.second }
                val contextExceeded = estimateContextExceeded(
                    requestHistory = requestHistory,
                    prompt = prompt,
                    options = options,
                    toolResultPayloads = toolLoopPayloads,
                ).contextExceeded
                mapToolingFollowUpResponseParams(
                    currentParams = params,
                    prompt = prompt,
                    options = options,
                    requestHistory = requestHistory,
                    initialResponse = response,
                    results = results,
                    appendStopToolsWarning = contextExceeded,
                )
            },
            onContextExceeded = { _, _ ->
                estimateContextExceeded(
                    requestHistory = requestHistory,
                    prompt = prompt,
                    options = options,
                    toolResultPayloads = toolLoopPayloads,
                )
            },
            onToolResult = { toolCall, resultJson ->
                if (toolCall.toolName == ToolDefinition.TAVILY_WEB_SEARCH.name) {
                    val assistantMessageId = options.assistantMessageId
                    if (assistantMessageId != null) {
                        val sources = TavilyResultParser.parse(assistantMessageId, resultJson)
                        if (sources.isNotEmpty()) {
                            emitEvent(InferenceEvent.TavilyResults(sources, modelType))
                        }
                    }
                }

                if (toolCall.toolName == ToolDefinition.GENERATE_ARTIFACT.name) {
                    val artifactParams = toolCall.parameters as? com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
                    if (artifactParams != null) {
                        emitEvent(
                            InferenceEvent.Artifacts(
                                artifacts = listOf(artifactParams.toRequest()),
                                modelType = modelType
                            )
                        )
                    }
                }
            },
            onFinished = { _, _, _ ->
                emitEvent(InferenceEvent.Finished(modelType))
            }
        )
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }

    /**
     * Estimates whether the request plus accumulated tool results exceeds the context window threshold.
     * For OpenAI Responses API, compaction mid-chain isn't possible (server manages state
     * via previousResponseId), so this only detects and reports.
     * Returns a [ContextExceededResult] indicating whether context is exceeded after
     * any compaction attempt.
     */
    protected open fun estimateContextExceeded(
        requestHistory: List<ChatMessage>,
        prompt: String,
        options: GenerationOptions,
        toolResultPayloads: List<String> = emptyList(),
    ): ContextExceededResult<ResponseCreateParams> {
        val contextWindow = options.contextWindow ?: return ContextExceededResult(false)
        val contextExceeded = ToolContextBudget.isApiContextExceeded(
            contextWindowTokens = contextWindow,
            history = requestHistory,
            systemPrompt = null,
            currentPrompt = prompt,
            toolResultPayloads = toolResultPayloads,
            options = options,
            modelId = modelId,
        )
        if (contextExceeded) {
            loggingPort.warning(
                tag,
                "Context exceeded mid-loop: window=$contextWindow provider=$provider"
            )
        }
        return ContextExceededResult(contextExceeded)
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
        val handler = OpenAiResponseStreamHandler(
            provider = provider,
            modelId = modelId,
            modelType = modelType,
            loggingPort = loggingPort,
            tag = tag,
            allowToolCall = allowToolCall,
            chatId = chatId,
            userMessageId = userMessageId,
            emitEvent = emitEvent,
        )
        val state = StreamState()
        client.responses().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            loop@ while (currentCoroutineContext().isActive) {
                val hasNext = try {
                    runInterruptible { iterator.hasNext() }
                } catch (e: RuntimeException) {
                    handler.handleStreamTermination(state, e.message, "checking next event")
                        ?.let { break@loop }
                        ?: throw e
                }
                if (!hasNext) {
                    break@loop
                }
                val event = try {
                    runInterruptible { iterator.next() }
                } catch (e: RuntimeException) {
                    handler.handleStreamTermination(state, e.message, "reading next event")
                        ?.let { break@loop }
                        ?: throw e
                }
                handler.handleEvent(event, state)
            }
            return handler.toStreamedResponse(state)
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
                    finishedEmitted = true
                }
            }

            if (!finishedEmitted) {
                loggingPort.debug(
                    tag,
                    "Chat stream ended without finishReason model=$modelId outputTextDeltas=$outputTextDeltaCount"
                )
            }
        }
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
