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
import com.browntowndev.pocketcrew.domain.util.WebSearchResultParser
import com.browntowndev.pocketcrew.feature.inference.openai.OpenAiResponseStreamHandler
import com.browntowndev.pocketcrew.feature.inference.openai.StreamState
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
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

    protected abstract fun mapToolingResponseParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
    ): ResponseCreateParams

    protected abstract fun mapToolingFollowUpResponseParams(
        currentParams: ResponseCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean,
    ): ResponseCreateParams

    protected open fun mapToolingChatCompletionParams(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
    ): ChatCompletionCreateParams =
        OpenAiRequestMapper.mapToChatCompletionParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options
        )

    protected open fun mapToolingFollowUpChatCompletionParams(
        currentParams: ChatCompletionCreateParams,
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        initialResponse: StreamedOpenAiResponse,
        results: List<Pair<ToolCallRequest, String>>,
        appendStopToolsWarning: Boolean,
    ): ChatCompletionCreateParams {
        val builder = currentParams.toBuilder()
        val messages = currentParams.messages().toMutableList()

        initialResponse.assistantMessageText
            .takeIf { it.isNotBlank() }
            ?.let { assistantText ->
                messages.add(
                    ChatCompletionMessageParam.ofAssistant(
                        ChatCompletionAssistantMessageParam.builder()
                            .content(assistantText)
                            .build()
                    )
                )
            }

        results.forEach { (_, resultJson) ->
            messages.add(
                ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                        .content(resultJson)
                        .build()
                )
            )
        }

        if (appendStopToolsWarning) {
            messages.add(
                ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                        .content(ContextWindowPlanner.STOP_TOOLS_WARNING)
                        .build()
                )
            )
        }

        builder.messages(messages)
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
                if (toolCall.toolName == ToolDefinition.WEB_SEARCH.name || 
                    toolCall.toolName == ToolDefinition.X_SEARCH.name) {
                    val assistantMessageId = options.assistantMessageId
                    if (assistantMessageId != null) {
                        val sources = WebSearchResultParser.parse(assistantMessageId, resultJson)
                        if (sources.isNotEmpty()) {
                            emitEvent(InferenceEvent.WebSearchResults(sources, modelType))
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

                if (toolCall.toolName == ToolDefinition.GENERATE_IMAGE.name ||
                    toolCall.toolName == ToolDefinition.GENERATE_VIDEO.name ||
                    toolCall.toolName == ToolDefinition.GENERATE_MUSIC.name) {
                    val mediaResult = com.browntowndev.pocketcrew.domain.util.MediaResultParser.parse(resultJson)
                    if (mediaResult.uris.isNotEmpty()) {
                        emitEvent(
                            InferenceEvent.Media(
                                mediaUris = mediaResult.uris,
                                mediaIds = mediaResult.ids,
                                mediaTypes = mediaResult.types,
                                prompts = mediaResult.prompts,
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

    protected suspend fun executeChatCompletionsToolingPrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logImagePayloads(options)
        val toolLoopPayloads = mutableListOf<String>()

        val initialParams = mapToolingChatCompletionParams(
            prompt = prompt,
            options = options,
            requestHistory = requestHistory,
        )

        orchestrator.execute(
            providerName = provider,
            initialParams = initialParams,
            tag = tag,
            maxToolCalls = options.maxToolCalls,
            onInferencePass = { params, _ ->
                streamChatCompletions(
                    params = params,
                    emitEvent = emitEvent,
                )
            },
            onToolCallDetected = { response ->
                val sdkCalls = response.functionCalls.map {
                    it.copy(chatId = options.chatId, userMessageId = options.userMessageId)
                }
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
                mapToolingFollowUpChatCompletionParams(
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
                val result = estimateContextExceeded(
                    requestHistory = requestHistory,
                    prompt = prompt,
                    options = options,
                    toolResultPayloads = toolLoopPayloads,
                )
                ContextExceededResult(contextExceeded = result.contextExceeded, rebuiltParams = null)
            },
            onToolResult = { toolCall, resultJson ->
                if (toolCall.toolName == ToolDefinition.WEB_SEARCH.name || 
                    toolCall.toolName == ToolDefinition.X_SEARCH.name) {
                    val assistantMessageId = options.assistantMessageId
                    if (assistantMessageId != null) {
                        val sources = WebSearchResultParser.parse(assistantMessageId, resultJson)
                        if (sources.isNotEmpty()) {
                            emitEvent(InferenceEvent.WebSearchResults(sources, modelType))
                        }
                    }
                }

                if (toolCall.toolName == ToolDefinition.GENERATE_ARTIFACT.name) {
                    val artifactParams = toolCall.parameters as? GenerateArtifactParams
                    if (artifactParams != null) {
                        emitEvent(
                            InferenceEvent.Artifacts(
                                artifacts = listOf(artifactParams.toRequest()),
                                modelType = modelType
                            )
                        )
                    }
                }

                if (toolCall.toolName == ToolDefinition.GENERATE_IMAGE.name ||
                    toolCall.toolName == ToolDefinition.GENERATE_VIDEO.name ||
                    toolCall.toolName == ToolDefinition.GENERATE_MUSIC.name) {
                    val mediaResult = com.browntowndev.pocketcrew.domain.util.MediaResultParser.parse(resultJson)
                    if (mediaResult.uris.isNotEmpty()) {
                        emitEvent(
                            InferenceEvent.Media(
                                mediaUris = mediaResult.uris,
                                mediaIds = mediaResult.ids,
                                mediaTypes = mediaResult.types,
                                prompts = mediaResult.prompts,
                                modelType = modelType
                            )
                        )
                    }
                }
            },
        ) { _, _, _ ->
            emitEvent(InferenceEvent.Finished(modelType))
        }
    }

    protected suspend fun streamChatCompletions(
        params: ChatCompletionCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): StreamedOpenAiResponse {
        logChatRequest(params)
        val accumulatedText = StringBuilder()
        var responseId: String? = null
        val trackedToolCalls = mutableMapOf<Long, TrackedToolCall>()

        client.chat().completions().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var finishedEmitted = false
            var outputTextDeltaCount = 0
            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                if (responseId == null) {
                    responseId = event.id()
                }
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
                        accumulatedText.append(text)
                        emitEvent(InferenceEvent.PartialResponse(text, modelType))
                    }
                }

                if (delta.toolCalls().isPresent) {
                    delta.toolCalls().get().forEach { toolCallDelta ->
                        val index = toolCallDelta.index()
                        val tracked = trackedToolCalls.getOrPut(index) { TrackedToolCall() }

                        toolCallDelta.id().ifPresent { tracked.id = it }
                        toolCallDelta.function().ifPresent { functionDelta ->
                            functionDelta.name().ifPresent { tracked.name = it }
                            functionDelta.arguments().ifPresent { tracked.arguments.append(it) }
                        }
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

        val functionCalls = mutableListOf<ToolCallRequest>()
        val providerToolCallIds = mutableListOf<String>()
        val providerToolItemIds = mutableListOf<String>()

        trackedToolCalls.entries.sortedBy { it.key }.forEach { (_, tracked) ->
            if (tracked.name != null) {
                functionCalls.add(
                    ToolCallRequest(
                        toolName = tracked.name!!,
                        argumentsJson = tracked.arguments.toString(),
                        provider = provider,
                        modelType = modelType,
                    )
                )
                providerToolCallIds.add(tracked.id ?: "")
                providerToolItemIds.add("fc_${tracked.id ?: ""}")
            }
        }

        return StreamedOpenAiResponse(
            emittedAny = accumulatedText.isNotEmpty() || functionCalls.isNotEmpty(),
            functionCalls = functionCalls,
            responseId = responseId,
            providerToolCallIds = providerToolCallIds,
            providerToolItemIds = providerToolItemIds,
            assistantMessageText = accumulatedText.toString(),
        )
    }

    private data class TrackedToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder()
    )

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

    protected fun describeException(e: Exception): String {
        val baseMessage = buildString {
            append("exception=")
            append(e::class.java.simpleName)
            e.message?.takeIf { it.isNotBlank() }?.let {
                append(" message=")
                append(it)
            }
        }

        if (e !is OpenAIServiceException) {
            return baseMessage
        }

        return buildString {
            append(baseMessage)
            append(" status=")
            append(e.statusCode())
            safeOptionalField { e.code() }?.let {
                append(" code=")
                append(it)
            }
            safeOptionalField { e.type() }?.let {
                append(" type=")
                append(it)
            }
            safeOptionalField { e.param() }?.let {
                append(" param=")
                append(it)
            }
            val body = safeSerializeBody(e)
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
