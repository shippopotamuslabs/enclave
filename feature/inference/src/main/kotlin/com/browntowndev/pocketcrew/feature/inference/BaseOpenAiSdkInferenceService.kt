package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.browntowndev.pocketcrew.feature.inference.openai.OpenAiResponseStreamHandler
import com.browntowndev.pocketcrew.feature.inference.openai.StreamState
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.fasterxml.jackson.core.type.TypeReference
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.ResponseCreateParams
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Suppress("LargeClass")
abstract class BaseOpenAiSdkInferenceService(
    protected val client: OpenAIClient,
    protected val modelId: String,
    protected val provider: String,
    protected val modelType: ModelType,
    protected val baseUrl: String? = null,
    protected val loggingPort: LoggingPort,
    private val toolStreamGatePort: ToolStreamGatePort,
) : RustCompatibleInferenceService {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000

        /** Checks if an exception message indicates a client error (400/Bad Request or 404/Not Found). */
        internal fun isClientErrorMessage(message: String?): Boolean =
            message?.contains("400") == true ||
                message?.contains("Bad Request") == true ||
                message?.contains("404") == true ||
                message?.contains("Not Found") == true
    }

    protected abstract val tag: String

    protected open val maxImages: Int = 20

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
            require(options.imageUris.size <= maxImages) {
                "$provider API supports a maximum of $maxImages images per request."
            }
            val requestHistory = buildRequestHistory(options)
            loggingPort.debug(
                tag,
                @Suppress("MaxLineLength")
                "sendPrompt start provider=$provider model=$modelId modelType=$modelType closeConversation=$closeConversation reasoningBudget=${options.reasoningBudget} reasoningEffort=${options.reasoningEffort} maxTokens=${options.maxTokens}"
            )
            val allowToolCall = options.toolingEnabled && options.availableTools.isNotEmpty()
            val textGate = ToolStreamGateSession(
                port = toolStreamGatePort,
                allowToolCall = allowToolCall,
            )
            executePrompt(prompt, options, requestHistory) { event ->
                when (event) {
                    is InferenceEvent.PartialResponse ->
                        textGate.onTextToken(event.chunk) { filtered ->
                            emit(InferenceEvent.PartialResponse(filtered, event.modelType))
                        }
                    else -> emit(event)
                }
            }
            if (allowToolCall) {
                textGate.finish(hasNativeToolCalls = false) { filtered ->
                    emit(InferenceEvent.PartialResponse(filtered, modelType))
                }
            }

            conversationHistory.add(ChatMessage(Role.USER, prompt))
            if (closeConversation) {
                closeSession()
            }
        } catch (e: CancellationException) {
            loggingPort.debug(tag, "sendPrompt cancelled provider=$provider model=$modelId")
            emit(InferenceEvent.Finished(modelType))
            throw e
        } catch (e: IllegalArgumentException) {
            loggingPort.error(tag, "Tool request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(e, modelType))
            return@flow
        } catch (e: IllegalStateException) {
            loggingPort.error(tag, "Tool request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(e, modelType))
            return@flow
        } catch (e: OpenAIServiceException) {
            var errorMsg = e.message ?: "Unknown error"
            val bodyStr = safeSerializeBody(e)
            if (bodyStr.isNotBlank() && !errorMsg.contains(bodyStr)) {
                errorMsg += " | Body: $bodyStr"
            }
            loggingPort.error(tag, "API request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(Exception("API Error ($provider): $errorMsg", e), modelType))
        } catch (e: Exception) {
            var errorMsg = e.message ?: "Unknown error"
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

    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult {
        val messages = sessionMessages.mapNotNull { it.toOpenAiChatParam() }

        val builder = ChatCompletionCreateParams.builder()
            .model(options.modelId ?: modelId)
            .messages(messages)

        if (allowToolCall && availableTools.isNotEmpty()) {
            availableTools.forEach { builder.addTool(it.toChatCompletionTool()) }
            builder.parallelToolCalls(false)
        }

        options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }

        val textGate = ToolStreamGateSession(
            port = toolStreamGatePort,
            allowToolCall = allowToolCall,
        )
        val response = streamChatCompletions(
            params = builder.build(),
            emitEvent = { event ->
                when (event) {
                    is InferenceEvent.PartialResponse -> textGate.onTextToken(event.chunk, onTextToken)
                    is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                    else -> {}
                }
            },
        )

        val toolCalls = response.functionCalls.mapIndexed { index, call ->
            AgentRawToolCall(
                id = response.providerToolCallIds.getOrElse(index) { "" },
                name = call.toolName,
                argumentsJson = call.argumentsJson,
            )
        }
        val bufferedText = textGate.finish(hasNativeToolCalls = toolCalls.isNotEmpty(), onTextToken)

        return AgentModelTurnResult(
            assistantText = bufferedText.takeIf { it.isNotBlank() },
            toolCalls = toolCalls,
            finishReason = if (toolCalls.isNotEmpty()) {
                AgentFinishReason.ToolUse
            } else {
                response.finishReason
                    ?.let { ProviderFinishReasonMapper.fromOpenAiChatStream(it, bufferedText).finishReason }
                    ?: AgentFinishReason.Stop
            },
        )
    }

    private fun AgentLoopMessage.toOpenAiChatParam(): ChatCompletionMessageParam? {
        val metadata = if (metadataJson != "null" && metadataJson.isNotBlank()) {
            runCatching { Json.parseToJsonElement(metadataJson).jsonObject }.getOrNull()
        } else null

        return when (role) {
            Role.SYSTEM -> ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder().content(content).build()
            )
            Role.USER -> ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder().content(content).build()
            )
            Role.ASSISTANT -> {
                // CompactSummary (FfiAgentRole.COMPACT_SUMMARY collapsed to ASSISTANT by toDomain())
                if (id.startsWith("compact-summary-")) {
                    return ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder()
                            .content("[Conversation summary]\n$content")
                            .build()
                    )
                }

                // Tool result (FfiAgentRole.TOOL collapsed to ASSISTANT by toDomain())
                val callId = metadata?.get("call_id")?.jsonPrimitive?.content
                if (callId != null) {
                    return ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                            .content(content)
                            .toolCallId(callId)
                            .build()
                    )
                }

                // Assistant message with optional tool calls
                val assistantBuilder = ChatCompletionAssistantMessageParam.builder()
                if (content.isNotBlank()) {
                    assistantBuilder.content(content)
                }
                val rawCalls = metadata?.get("raw_tool_calls")
                if (rawCalls is JsonArray) {
                    rawCalls.forEach { tc ->
                        val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                        val callName = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                        val rawCallId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"
                        assistantBuilder.addToolCall(
                            ChatCompletionMessageFunctionToolCall.builder()
                                .id(rawCallId)
                                .function(
                                    ChatCompletionMessageFunctionToolCall.Function.builder()
                                        .name(callName)
                                        .arguments(argsJson)
                                        .build()
                                )
                                .build()
                        )
                    }
                }
                ChatCompletionMessageParam.ofAssistant(assistantBuilder.build())
            }
        }
    }

    protected fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
            "Prepared request history provider=$provider model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}"
        )
        return historyWithSystemPrompt
    }

    protected suspend fun streamResponses(
        params: ResponseCreateParams,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        assistantMessageId: MessageId? = null,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): StreamedOpenAiResponse {
        logResponsesRequest(params)
        val handler = OpenAiResponseStreamHandler(
            provider = provider,
            modelId = modelId,
            modelType = modelType,
            loggingPort = loggingPort,
            tag = tag,
            chatId = chatId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            emitEvent = emitEvent,
        )
        val state = StreamState()
        client.responses().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            @Suppress("LoopWithTooManyJumpStatements")
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

    protected open suspend fun streamChatCompletions(
        params: ChatCompletionCreateParams,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        assistantMessageId: MessageId? = null,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): StreamedOpenAiResponse {
        logChatRequest(params)
        val accumulatedText = StringBuilder()
        var responseId: String? = null
        var finishReason: String? = null
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

                val reasoningText = openAiChatDeltaReasoningText(delta._additionalProperties())
                if (reasoningText != null) {
                    emitEvent(InferenceEvent.Thinking(reasoningText, modelType))
                }

                if (choice.finishReason().isPresent) {
                    finishReason = choice.finishReason().get().toString()
                    loggingPort.debug(
                        tag,
                        @Suppress("MaxLineLength")
                        "Chat stream finished model=$modelId finishReason=$finishReason outputTextDeltas=$outputTextDeltaCount"
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
                        chatId = chatId,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
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
            finishReason = finishReason,
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
            @Suppress("MaxLineLength")
            "Responses API request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(serializeBody(params._body()))}"
        )
    }

    private fun logChatRequest(params: ChatCompletionCreateParams) {
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
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
                    @Suppress("MaxLineLength")
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

internal fun openAiChatDeltaReasoningText(additionalProperties: Map<String, JsonValue>): String? {
    val directReasoning = listOf("reasoning_content", "reasoning")
        .firstNotNullOfOrNull { key ->
            additionalProperties[key]
                ?.convert(String::class.java)
                ?.takeIf { value -> value.isNotBlank() }
        }
    if (directReasoning != null) return directReasoning

    return additionalProperties["reasoning_details"]
        ?.let(::reasoningDetailsText)
        ?.takeIf { text -> text.isNotBlank() }
}

private fun reasoningDetailsText(value: JsonValue): String {
    val details = runCatching {
        value.convert(
            object : TypeReference<List<Map<String, Any?>>>() {}
        )
    }.getOrNull().orEmpty()

    return details.joinToString(separator = "") { detail ->
        when (detail["type"]) {
            "reasoning.text" -> detail["text"] as? String
            "reasoning.summary" -> detail["summary"] as? String
            else -> null
        }.orEmpty()
    }
}
