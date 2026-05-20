package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("LargeClass")
abstract class BaseOpenAiSdkInferenceService(
    protected val client: OpenAIClient,
    protected val modelId: String,
    protected val provider: String,
    protected val modelType: ModelType,
    protected val baseUrl: String? = null,
    protected val loggingPort: LoggingPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
) : RustCompatibleInferenceService {
    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
    }

    protected abstract val tag: String

    protected open val maxImages: Int = 20

    protected open val useStrictToolSchemas: Boolean = false

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

    override fun sendPrompt(
        prompt: String,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = error("sendPrompt not supported; all OpenAI-SDK services are wrapped by RustLoopInferenceService")

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = error("sendPrompt not supported; all OpenAI-SDK services are wrapped by RustLoopInferenceService")

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

        val builder =
            ChatCompletionCreateParams
                .builder()
                .model(options.modelId ?: modelId)
                .messages(messages)

        if (allowToolCall && availableTools.isNotEmpty()) {
            availableTools.forEach { builder.addTool(it.toChatCompletionTool(strict = useStrictToolSchemas)) }
            builder.parallelToolCalls(false)
        }

        options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
        options.temperature?.let { builder.temperature(it.toDouble()) }
        options.topP?.let { builder.topP(it.toDouble()) }
        options.maxTokens?.let { builder.maxCompletionTokens(it.toLong()) }

        val response =
            streamChatCompletions(
                params = builder.build(),
                emitEvent = { event ->
                    when (event) {
                        is InferenceEvent.PartialResponse -> onTextToken(event.chunk)
                        is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                        else -> {}
                    }
                },
            )

        val toolCalls =
            response.functionCalls.mapIndexed { index, call ->
                AgentRawToolCall(
                    id = response.providerToolCallIds.getOrElse(index) { "" },
                    name = call.toolName,
                    argumentsJson = call.argumentsJson,
                )
            }
        val bufferedText = response.assistantMessageText

        return AgentModelTurnResult(
            assistantText = bufferedText.takeIf { it.isNotBlank() },
            toolCalls = toolCalls,
            finishReason =
                if (toolCalls.isNotEmpty()) {
                    AgentFinishReason.ToolUse
                } else {
                    response.finishReason
                        ?.let { ProviderFinishReasonMapper.fromOpenAiChatStream(it, bufferedText).finishReason }
                        ?: AgentFinishReason.Stop
                },
        )
    }

    private fun AgentLoopMessage.toOpenAiChatParam(): ChatCompletionMessageParam? {
        val metadata =
            if (metadataJson != "null" && metadataJson.isNotBlank()) {
                runCatching { Json.parseToJsonElement(metadataJson).jsonObject }.getOrNull()
            } else {
                null
            }

        return when (role) {
            Role.SYSTEM ->
                ChatCompletionMessageParam.ofSystem(
                    ChatCompletionSystemMessageParam.builder().content(content).build(),
                )
            Role.USER ->
                ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder().content(content).build(),
                )
            Role.ASSISTANT -> {
                // CompactSummary (FfiAgentRole.COMPACT_SUMMARY collapsed to ASSISTANT by toDomain())
                if (id.startsWith("compact-summary-")) {
                    return ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam
                            .builder()
                            .content("[Conversation summary]\n$content")
                            .build(),
                    )
                }

                // Tool result (FfiAgentRole.TOOL collapsed to ASSISTANT by toDomain())
                val callId = metadata?.get("call_id")?.jsonPrimitive?.content
                if (callId != null) {
                    return ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam
                            .builder()
                            .content(content)
                            .toolCallId(callId)
                            .build(),
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
                            ChatCompletionMessageFunctionToolCall
                                .builder()
                                .id(rawCallId)
                                .function(
                                    ChatCompletionMessageFunctionToolCall.Function
                                        .builder()
                                        .name(callName)
                                        .arguments(argsJson)
                                        .build(),
                                ).build(),
                        )
                    }
                }
                ChatCompletionMessageParam.ofAssistant(assistantBuilder.build())
            }
        }
    }

    protected open suspend fun streamChatCompletions(
        params: ChatCompletionCreateParams,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        assistantMessageId: MessageId? = null,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedOpenAiResponse {
        logChatRequest(params)
        val state = StreamAccumulationState()

        try {
            client.chat().completions().createStreaming(params).use { streamResponse ->
                val iterator = streamResponse.stream().iterator()
                while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                    val event = runInterruptible { iterator.next() }
                    val shouldStop = processStreamEvent(event, state, emitEvent)
                    if (shouldStop) break
                }

                if (!state.finishedEmitted) {
                    loggingPort.debug(
                        tag,
                        "Chat stream ended without finishReason model=$modelId outputTextDeltas=${state.outputTextDeltaCount}",
                    )
                }
            }
        } catch (e: OpenAIServiceException) {
            val detail = describeException(e)
            loggingPort.error(tag, "Chat stream failed model=$modelId $detail", e)
            throw RuntimeException(detail, e)
        }

        val functionCalls = mutableListOf<ToolCallRequest>()
        val providerToolCallIds = mutableListOf<String>()
        val providerToolItemIds = mutableListOf<String>()

        state.trackedToolCalls.entries.sortedBy { it.key }.forEach { (_, tracked) ->
            if (tracked.name != null) {
                val argsJson =
                    if (tracked.argsTruncated) {
                        OpenAiSdkStreamCaps.TOOL_ARGS_TRUNCATED_PAYLOAD
                    } else {
                        tracked.arguments.toString()
                    }
                functionCalls.add(
                    ToolCallRequest(
                        toolName = tracked.name!!,
                        argumentsJson = argsJson,
                        provider = provider,
                        modelType = modelType,
                        chatId = chatId,
                        userMessageId = userMessageId,
                        assistantMessageId = assistantMessageId,
                    ),
                )
                providerToolCallIds.add(tracked.id ?: "")
                providerToolItemIds.add("fc_${tracked.id ?: ""}")
            }
        }

        return StreamedOpenAiResponse(
            emittedAny = state.accumulatedText.isNotEmpty() || functionCalls.isNotEmpty(),
            functionCalls = functionCalls,
            responseId = state.responseId,
            providerToolCallIds = providerToolCallIds,
            providerToolItemIds = providerToolItemIds,
            assistantMessageText = state.accumulatedText.toString(),
            finishReason = state.finishReason,
        )
    }

    /**
     * Processes a single SSE event from the chat completion stream. Returns `true` when the caller
     * should break the read loop (cap hit; finish reason already set on `state`).
     */
    private suspend fun processStreamEvent(
        event: com.openai.models.chat.completions.ChatCompletionChunk,
        state: StreamAccumulationState,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): Boolean {
        if (state.responseId == null) {
            state.responseId = event.id()
        }
        val choices = event.choices()
        if (choices.isEmpty()) {
            loggingPort.debug(tag, "Chat stream chunk had no choices model=$modelId")
            return false
        }
        val choice = choices[0]
        val delta = choice.delta()

        if (processTextDelta(delta, state, emitEvent)) {
            state.finishReason = "length"
            state.finishedEmitted = true
            return true
        }

        processToolCallDeltas(delta, state)

        val reasoningText = openAiChatDeltaReasoningText(delta._additionalProperties())
        if (reasoningText != null) {
            emitEvent(InferenceEvent.Thinking(reasoningText, modelType))
        }

        if (choice.finishReason().isPresent) {
            state.finishReason = choice.finishReason().get().toString()
            loggingPort.debug(
                tag,
                @Suppress("MaxLineLength")
                "Chat stream finished model=$modelId finishReason=${state.finishReason} outputTextDeltas=${state.outputTextDeltaCount}",
            )
            state.finishedEmitted = true
        }
        return false
    }

    /**
     * Appends any text content from this delta to `state.accumulatedText` and emits a partial
     * response event. Returns `true` if the accumulator just hit the assistant-text cap and the
     * stream loop should stop.
     */
    private suspend fun processTextDelta(
        delta: com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta,
        state: StreamAccumulationState,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): Boolean {
        if (!delta.content().isPresent) return false
        val text = delta.content().get()
        if (text.isEmpty()) return false
        if (OpenAiSdkStreamCaps.isDeltaChunkOversize(text.length)) {
            loggingPort.warning(
                tag,
                @Suppress("MaxLineLength")
                "Dropping oversized text delta model=$modelId chunkChars=${text.length} max=${OpenAiSdkStreamCaps.MAX_DELTA_CHUNK_CHARS}",
            )
            return false
        }
        state.outputTextDeltaCount++
        state.accumulatedText.append(text)
        emitEvent(InferenceEvent.PartialResponse(text, modelType))
        return OpenAiSdkStreamCaps.applyAssistantTextCap(
            state.accumulatedText,
            loggingPort,
            tag,
            modelId,
        )
    }

    private fun processToolCallDeltas(
        delta: com.openai.models.chat.completions.ChatCompletionChunk.Choice.Delta,
        state: StreamAccumulationState,
    ) {
        if (!delta.toolCalls().isPresent) return
        delta.toolCalls().get().forEach { toolCallDelta ->
            val index = toolCallDelta.index()
            val tracked = state.trackedToolCalls.getOrPut(index) { TrackedToolCall() }
            toolCallDelta.id().ifPresent { tracked.id = it }
            toolCallDelta.function().ifPresent { functionDelta ->
                functionDelta.name().ifPresent { tracked.name = it }
                functionDelta.arguments().ifPresent { argsChunk ->
                    appendToolArgsChunk(tracked, argsChunk)
                }
            }
        }
    }

    private fun appendToolArgsChunk(
        tracked: TrackedToolCall,
        argsChunk: String,
    ) {
        if (tracked.argsTruncated) return
        val rejected =
            OpenAiSdkStreamCaps.appendToolArgsChunkOrReject(
                builder = tracked.arguments,
                chunk = argsChunk,
                toolName = tracked.name,
                loggingPort = loggingPort,
                tag = tag,
                modelId = modelId,
            )
        if (rejected) {
            tracked.argsTruncated = true
        }
    }

    private class StreamAccumulationState(
        val accumulatedText: StringBuilder = StringBuilder(),
        var responseId: String? = null,
        var finishReason: String? = null,
        var finishedEmitted: Boolean = false,
        var outputTextDeltaCount: Int = 0,
        val trackedToolCalls: MutableMap<Long, TrackedToolCall> = mutableMapOf(),
    )

    private data class TrackedToolCall(
        var id: String? = null,
        var name: String? = null,
        val arguments: StringBuilder = StringBuilder(),
        var argsTruncated: Boolean = false,
    )

    private fun logChatRequest(params: ChatCompletionCreateParams) {
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
            "Chat Completions request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(
                serializeBody(params._body()),
            )}",
        )
    }

    protected fun describeException(e: Exception): String {
        val baseMessage =
            buildString {
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
                    "Skipping malformed API error field while formatting exception. fieldError=${error.message ?: error::class.java.simpleName}",
                )
                null
            }

    private fun safeSerializeBody(throwable: OpenAIServiceException): String =
        runCatching { serializeBody(throwable.body()) }
            .getOrElse { error ->
                loggingPort.warning(
                    tag,
                    "Skipping API error body while formatting exception. bodyError=${error.message ?: error::class.java.simpleName}",
                )
                ""
            }

    private fun serializeBody(body: Any): String = body.toString()

    protected fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }
}

internal fun openAiChatDeltaReasoningText(additionalProperties: Map<String, JsonValue>): String? {
    val directReasoning =
        listOf("reasoning_content", "reasoning")
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
    val details =
        runCatching {
            value.convert(
                object : TypeReference<List<Map<String, Any?>>>() {},
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
