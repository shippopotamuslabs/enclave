package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.ImageBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigEnabled
import com.anthropic.models.messages.ToolChoice
import com.anthropic.models.messages.ToolChoiceNone
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlockParam
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolCatalogJson
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContext
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.emitInferenceExecutionOutcome
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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

@Suppress("MaxLineLength", "MagicNumber", "LargeClass")
class AnthropicInferenceServiceImpl(
    private val client: AnthropicClient,
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
    private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
    private val telemetrySupport: InferenceTelemetrySupport? = null,
    private val telemetryEventPort: TelemetryEventPort? = null,
) : RustCompatibleInferenceService {
    private data class StreamedAnthropicResponse(
        val emittedAny: Boolean,
        val toolUse: CapturedToolUse?,
        val stopReason: String?,
        val inputTokens: Int? = null,
        val outputTokens: Int? = null,
        val reasoningTokens: Int? = null,
        val cacheCreationInputTokens: Int? = null,
        val cacheReadInputTokens: Int? = null,
    )

    private data class CapturedToolUse(
        val id: String,
        val toolName: String,
        val argumentsJson: String,
    )

    private data class PendingToolUse(
        val id: String,
        val toolName: String,
        val initialInput: JsonValue,
        val streamedInputJson: StringBuilder = StringBuilder(),
    )

    companion object {
        private const val DEFAULT_MAX_TOKENS = 4096L
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "AnthropicInferenceService"
        private const val PROVIDER = "ANTHROPIC"
        private const val THINKING_BUDGET_MEDIUM = 1536L
        private const val THINKING_BUDGET_XHIGH = 3072L
        private val TOOL_INPUT_JSON_MAPPER = ObjectMapper()
    }

    private val conversationHistory = mutableListOf<ChatMessage>()

    private var appendOnlyContext: ProviderAppendOnlyContext? = null

    @Volatile
    private var activeToolCatalogJson: String = AgentLoopToolCatalogJson.EMPTY_ARRAY

    override fun sendPrompt(
        prompt: String,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> =
        flow {
            try {
                require(options.imageUris.size <= 20) {
                    "Anthropic API supports a maximum of 20 images per request."
                }
                val requestHistory = buildRequestHistory(options)
                loggingPort.debug(
                    TAG,
                    @Suppress("MaxLineLength")
                    "sendPrompt start provider=$PROVIDER model=$modelId modelType=$modelType closeConversation=$closeConversation reasoningBudget=${options.reasoningBudget} reasoningEffort=${options.reasoningEffort} maxTokens=${options.maxTokens}",
                )
                executePrompt(prompt, options, requestHistory) { emit(it) }

                conversationHistory.add(ChatMessage(Role.USER, prompt))
                if (closeConversation) {
                    closeSession()
                }
            } catch (e: CancellationException) {
                loggingPort.debug(TAG, "sendPrompt cancelled provider=$PROVIDER model=$modelId")
                emit(InferenceEvent.Finished(modelType))
                throw e
            } catch (e: IllegalArgumentException) {
                loggingPort.error(TAG, "Tool request failed. ${describeException(e)}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: IllegalStateException) {
                loggingPort.error(TAG, "Tool request failed. ${describeException(e)}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                loggingPort.error(TAG, "API request failed. ${describeException(e)}", e)
                emit(InferenceEvent.Error(Exception("API Error ($PROVIDER): $errorMsg", e), modelType))
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        activeToolCatalogJson = AgentLoopToolCatalogJson.jsonForCatalog(options.toolCatalog)
        logImagePayloads(options)

        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options,
            )

        loggingPort.info(
            TAG,
            @Suppress("MaxLineLength")
            "Using Anthropic Messages API. model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget} maxTokens=${options.maxTokens}",
        )
        logRequest(params)
        streamMessages(
            params = params,
            emitEvent = emitEvent,
        )
        emitEvent(InferenceEvent.Finished(modelType))
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
        resetAppendOnlyForNewPrompt()
    }

    internal fun clearAppendOnlyOnRustCompactionBoundary() {
        appendOnlyContext?.clearOnRustCompactionBoundary()
    }

    internal fun resetAppendOnlyForNewPrompt() {
        appendOnlyContext?.invalidate()
        appendOnlyContext = null
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
        appendOnlyContext?.invalidate()
        appendOnlyContext = null
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        contextSidecars: String?,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult {
        val startedAtMs = System.currentTimeMillis()
        val resolvedModelId = options.modelId ?: modelId
        try {
            activeToolCatalogJson = AgentLoopToolCatalogJson.jsonForCatalog(options.toolCatalog)
            val timing =
                telemetrySupport?.let {
                    com.browntowndev.enclave.feature.inference.telemetry
                        .InferenceTurnTiming()
                }
            val captureContext = telemetrySupport?.captureContext(options)
            val lastUserIndex =
                sessionMessages.indexOfLast { msg ->
                    msg.role == Role.USER &&
                        run {
                            val meta = msg.metadataJson
                            if (meta == "null" || meta.isBlank()) return@run true
                            val obj = runCatching { Json.parseToJsonElement(meta).jsonObject }.getOrNull()
                            obj?.get("call_id")?.jsonPrimitive?.contentOrNull == null
                        }
                }
            val imageUris = options.imageUris.take(20)
            val builder =
                MessageCreateParams
                    .builder()
                    .model(options.modelId ?: modelId)

            if (!options.maxTokensDisabled) {
                options.maxTokens?.let { builder.maxTokens(it.toLong()) } ?: builder.maxTokens(DEFAULT_MAX_TOKENS)
            } else {
                builder.maxTokens(DEFAULT_MAX_TOKENS)
            }

            resolveThinkingBudget(options)?.let {
                builder.thinking(
                    ThinkingConfigEnabled
                        .builder()
                        .budgetTokens(it)
                        .build(),
                )
            }

            val useCaching = AnthropicPromptCacheSupport.supportsPromptCaching(options.modelId ?: modelId)
            val stableTailIndex = AnthropicPromptCacheSupport.stableHistoryTailIndex(sessionMessages)
            loggingPort.debug(
                TAG,
                @Suppress("MaxLineLength")
                "Anthropic prompt cache config provider=$PROVIDER model=${options.modelId ?: modelId} useCaching=$useCaching stableTailIndex=$stableTailIndex sessionMessageCount=${sessionMessages.size} availableToolCount=${availableTools.size} hasSystemPrompt=${systemPrompt.isNotBlank()}",
            )

            if (systemPrompt.isNotBlank()) {
                val systemBlockBuilder = TextBlockParam.builder().text(systemPrompt)
                if (useCaching) {
                    systemBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                }
                builder.systemOfTextBlockParams(listOf(systemBlockBuilder.build()))
            }

            sessionMessages.forEachIndexed { index, msg ->
                val metadata =
                    if (msg.metadataJson != "null" && msg.metadataJson.isNotBlank()) {
                        runCatching { Json.parseToJsonElement(msg.metadataJson).jsonObject }.getOrNull()
                    } else {
                        null
                    }
                val attachCacheBreakpoint =
                    useCaching &&
                        AnthropicPromptCacheSupport.shouldAttachMessageCacheBreakpoint(index, stableTailIndex)

                val messageContent =
                    if (index == lastUserIndex) {
                        VolatileTurnContextWireSupport
                            .combinedLatestUserPrepends(
                                volatileTurnContext = null, // already in msg.content via Rust history
                                contextSidecars = contextSidecars,
                            )?.let { prepended -> "$prepended\n\n${msg.content}" }
                            ?: msg.content
                    } else {
                        msg.content
                    }

                when (msg.role) {
                    Role.SYSTEM -> {} // systemPrompt mapped above
                    Role.USER -> {
                        val attachImages = index == lastUserIndex && imageUris.isNotEmpty()
                        if (!attachImages) {
                            val textBlockBuilder = TextBlockParam.builder().text(messageContent)
                            if (attachCacheBreakpoint) {
                                textBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                            }
                            builder.addMessage(
                                MessageParam
                                    .builder()
                                    .role(MessageParam.Role.USER)
                                    .contentOfBlockParams(listOf(ContentBlockParam.ofText(textBlockBuilder.build())))
                                    .build(),
                            )
                        } else {
                            val contentBlocks = mutableListOf<ContentBlockParam>()
                            ImagePayloads.fromUris(imageUris).forEach { payload ->
                                val mediaType =
                                    when (payload.mimeType) {
                                        "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG
                                        "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF
                                        "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP
                                        else -> Base64ImageSource.MediaType.IMAGE_JPEG
                                    }
                                contentBlocks.add(
                                    ContentBlockParam.ofImage(
                                        ImageBlockParam
                                            .builder()
                                            .source(
                                                Base64ImageSource
                                                    .builder()
                                                    .data(payload.base64)
                                                    .mediaType(mediaType)
                                                    .build(),
                                            ).build(),
                                    ),
                                )
                            }
                            val textBlockBuilder = TextBlockParam.builder().text(messageContent)
                            if (attachCacheBreakpoint) {
                                textBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                            }
                            contentBlocks.add(
                                ContentBlockParam.ofText(textBlockBuilder.build()),
                            )
                            builder.addMessage(
                                MessageParam
                                    .builder()
                                    .role(MessageParam.Role.USER)
                                    .contentOfBlockParams(contentBlocks)
                                    .build(),
                            )
                        }
                    }
                    Role.ASSISTANT -> {
                        if (msg.id.startsWith("compact-summary-")) {
                            val textBlockBuilder = TextBlockParam.builder().text("[Conversation summary]\n$messageContent")
                            if (attachCacheBreakpoint) {
                                textBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                            }
                            builder.addMessage(
                                MessageParam
                                    .builder()
                                    .role(MessageParam.Role.USER)
                                    .contentOfBlockParams(listOf(ContentBlockParam.ofText(textBlockBuilder.build())))
                                    .build(),
                            )
                            return@forEachIndexed
                        }
                        val callId = metadata?.get("call_id")?.jsonPrimitive?.content
                        if (callId != null) {
                            val toolResultBuilder = ToolResultBlockParam.builder().toolUseId(callId).content(messageContent)
                            if (attachCacheBreakpoint) {
                                toolResultBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                            }
                            builder.addMessage(
                                MessageParam
                                    .builder()
                                    .role(MessageParam.Role.USER)
                                    .contentOfBlockParams(
                                        listOf(
                                            ContentBlockParam.ofToolResult(toolResultBuilder.build()),
                                        ),
                                    ).build(),
                            )
                            return@forEachIndexed
                        }
                        val rawCalls = metadata?.get("raw_tool_calls")
                        val contentBlocks = mutableListOf<ContentBlockParam>()
                        if (messageContent.isNotBlank()) {
                            val textBlockBuilder = TextBlockParam.builder().text(messageContent)
                            val isTextLastBlock =
                                attachCacheBreakpoint &&
                                    (rawCalls == null || rawCalls !is JsonArray || rawCalls.isEmpty())
                            if (isTextLastBlock) {
                                textBlockBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                            }
                            contentBlocks.add(ContentBlockParam.ofText(textBlockBuilder.build()))
                        }
                        if (rawCalls is JsonArray) {
                            rawCalls.forEachIndexed { tcIndex, tc ->
                                val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEachIndexed
                                val callName = obj["name"]?.jsonPrimitive?.content ?: return@forEachIndexed
                                val rawCallId = obj["id"]?.jsonPrimitive?.contentOrNull ?: ""
                                val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"

                                val toolUseBuilder =
                                    ToolUseBlockParam
                                        .builder()
                                        .id(rawCallId)
                                        .name(callName)
                                        .input(
                                            ToolUseBlockParam.Input
                                                .builder()
                                                .putAllAdditionalProperties(toolUseInputProperties(argsJson))
                                                .build(),
                                        )
                                val isToolUseLastBlock = attachCacheBreakpoint && (tcIndex == rawCalls.size - 1)
                                if (isToolUseLastBlock) {
                                    toolUseBuilder.cacheControl(AnthropicPromptCacheSupport.ephemeralCacheControl())
                                }
                                contentBlocks.add(
                                    ContentBlockParam.ofToolUse(toolUseBuilder.build()),
                                )
                            }
                        }
                        if (contentBlocks.isNotEmpty()) {
                            builder.addMessage(
                                MessageParam
                                    .builder()
                                    .role(MessageParam.Role.ASSISTANT)
                                    .contentOfBlockParams(contentBlocks)
                                    .build(),
                            )
                        }
                    }
                }
            }

            if (availableTools.isNotEmpty()) {
                availableTools.forEachIndexed { idx, toolDef ->
                    val isLast = idx == availableTools.size - 1
                    val cacheControl =
                        if (useCaching && isLast) {
                            AnthropicPromptCacheSupport.ephemeralCacheControl()
                        } else {
                            null
                        }
                    builder.addTool(toolDef.toAnthropicTool(cacheControl))
                }
                if (!allowToolCall) {
                    builder.toolChoice(ToolChoice.ofNone(ToolChoiceNone.builder().build()))
                }
            }

            val accumulatedText = StringBuilder()
            val response =
                streamMessages(builder.build()) { event ->
                    when (event) {
                        is InferenceEvent.PartialResponse -> {
                            timing?.markFirstOutput()
                            accumulatedText.append(event.chunk)
                            onTextToken(event.chunk)
                        }
                        is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                        else -> {}
                    }
                }
            loggingPort.debug(
                TAG,
                @Suppress("MaxLineLength")
                "Anthropic cache outcome model=$modelId useCaching=$useCaching cacheCreationInputTokens=${response.cacheCreationInputTokens} cacheReadInputTokens=${response.cacheReadInputTokens} inputTokens=${response.inputTokens} stopReason=${response.stopReason}",
            )

            val toolCalls =
                response.toolUse?.let { tu ->
                    listOf(AgentRawToolCall(id = tu.id, name = tu.toolName, argumentsJson = tu.argumentsJson))
                } ?: emptyList()
            val bufferedText = accumulatedText.toString()

            val telemetry =
                if (telemetrySupport != null && timing != null && captureContext != null) {
                    telemetrySupport.anthropicTurnTelemetry(
                        context = captureContext,
                        modelId = modelId,
                        modelLabel = modelId,
                        timing = timing,
                        inputTokens = response.inputTokens,
                        outputTokens = response.outputTokens,
                        reasoningTokens = response.reasoningTokens,
                        cacheCreationInputTokens = response.cacheCreationInputTokens,
                        cacheReadInputTokens = response.cacheReadInputTokens,
                        savedPricing = options.apiModelPricing,
                    )
                } else {
                    null
                }

            return AgentModelTurnResult(
                assistantText = bufferedText.takeIf { it.isNotBlank() },
                toolCalls = toolCalls,
                finishReason =
                    if (toolCalls.isNotEmpty()) {
                        AgentFinishReason.ToolUse
                    } else {
                        response.stopReason
                            ?.let { ProviderFinishReasonMapper.fromAnthropic(it, bufferedText).finishReason }
                            ?: AgentFinishReason.Stop
                    },
                telemetry = telemetry,
            ).also {
                telemetryEventPort.emitInferenceExecutionOutcome(
                    startedAtMs = startedAtMs,
                    provider = "Anthropic",
                    modelId = resolvedModelId,
                    success = true,
                    tokenCount = response.outputTokens,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InferenceNetworkException) {
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = "Anthropic",
                modelId = resolvedModelId,
                success = false,
                errorMessage = e.message,
            )
            throw e
        } catch (e: java.io.IOException) {
            loggingPort.error(TAG, "completeSingleTurn network failure: ${describeException(e)}", e)
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = "Anthropic",
                modelId = resolvedModelId,
                success = false,
                errorMessage = e.message,
            )
            throw InferenceNetworkException(
                provider = PROVIDER,
                message = e.message ?: "Connection error",
                cause = e,
            )
        } catch (e: Exception) {
            loggingPort.error(TAG, "completeSingleTurn failed: ${describeException(e)}", e)
            throw e
        }
    }

    private fun resolveThinkingBudget(options: GenerationOptions): Long? =
        when {
            options.reasoningBudget > 0 -> options.reasoningBudget.toLong().coerceAtLeast(1024L)
            else ->
                when (options.reasoningEffort) {
                    null -> null
                    ApiReasoningEffort.NONE -> null
                    ApiReasoningEffort.LOW -> 1024L
                    ApiReasoningEffort.MEDIUM -> THINKING_BUDGET_MEDIUM
                    ApiReasoningEffort.HIGH -> 2048L
                    ApiReasoningEffort.XHIGH -> THINKING_BUDGET_XHIGH
                }
        }

    private fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            TAG,
            @Suppress("MaxLineLength")
            "Prepared request history provider=$PROVIDER model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}",
        )
        return historyWithSystemPrompt
    }

    private fun mergeSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }

    private fun describeException(throwable: Throwable): String =
        buildString {
            append("exception=")
            append(throwable::class.java.simpleName)
            throwable.message?.takeIf { it.isNotBlank() }?.let {
                append(" message=")
                append(it)
            }
        }

    private fun logRequest(params: com.anthropic.models.messages.MessageCreateParams) {
        loggingPort.debug(
            TAG,
            "Messages API request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${
                truncateForLogs(
                    params.toString(),
                )
            }",
        )
    }

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

    private fun logImagePayloads(options: GenerationOptions) {
        if (options.imageUris.isEmpty()) {
            return
        }
        val payloads =
            runCatching {
                ImagePayloads.fromUris(options.imageUris)
            }.getOrElse { error ->
                loggingPort.error(
                    TAG,
                    "Failed to load image payloads provider=$PROVIDER model=$modelId message=${error.message}",
                    error,
                )
                return
            }

        val details =
            payloads.joinToString(separator = "; ") { payload ->
                "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${
                    payload.sha256.take(
                        8,
                    )
                }"
            }
        loggingPort.info(
            TAG,
            "Image payloads provider=$PROVIDER model=$modelId count=${payloads.size} $details",
        )
    }

    private fun describeStreamEvent(event: RawMessageStreamEvent): String =
        when {
            event.messageStart().isPresent -> "message_start"
            event.messageDelta().isPresent -> "message_delta"
            event.messageStop().isPresent -> "message_stop"
            event.contentBlockStart().isPresent -> "content_block_start"
            event.contentBlockDelta().isPresent -> "content_block_delta"
            event.contentBlockStop().isPresent -> "content_block_stop"
            else -> "other"
        }

    private suspend fun streamMessages(
        params: MessageCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedAnthropicResponse {
        logRequest(params)
        client.messages().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var emittedAny = false
            var outputTextDeltaCount = 0
            var thinkingTextDeltaCount = 0
            var stopReason: String? = null
            var toolUseIndex: Long? = null
            var inputTokens: Int? = null
            var outputTokens: Int? = null
            var reasoningTokens: Int? = null
            var cacheCreationInputTokens: Int? = null
            var cacheReadInputTokens: Int? = null
            val pendingToolUses = mutableMapOf<Long, PendingToolUse>()

            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                if (event.messageStart().isPresent) {
                    val usage =
                        event
                            .messageStart()
                            .get()
                            .message()
                            .usage()
                    inputTokens = usage.inputTokens().toInt()
                    outputTokens = usage.outputTokens().toInt()
                    reasoningTokens = null
                    val rawCacheCreationStart = usage.cacheCreationInputTokens().orElse(-1L)
                    val rawCacheReadStart = usage.cacheReadInputTokens().orElse(-1L)
                    loggingPort.debug(
                        TAG,
                        @Suppress("MaxLineLength")
                        "Anthropic messageStart usage model=$modelId inputTokens=$inputTokens outputTokens=$outputTokens rawCacheCreation=$rawCacheCreationStart rawCacheRead=$rawCacheReadStart (-1=absent, 0=present-zero)",
                    )
                    cacheCreationInputTokens = rawCacheCreationStart.toInt().takeIf { it > 0 }
                    cacheReadInputTokens = rawCacheReadStart.toInt().takeIf { it > 0 }
                }
                val contentBlockStart = event.contentBlockStart()
                if (contentBlockStart.isPresent) {
                    val startEvent = contentBlockStart.get()
                    val block = contentBlockStart.get().contentBlock()
                    val startText = block.text().map { it.text() }.orElse("")
                    if (startText.isNotEmpty()) {
                        outputTextDeltaCount++
                        emittedAny = true
                        emitEvent(InferenceEvent.PartialResponse(startText, modelType))
                    }

                    val startThinking = block.thinking().map { it.thinking() }.orElse("")
                    if (startThinking.isNotEmpty()) {
                        thinkingTextDeltaCount++
                        emittedAny = true
                        emitEvent(InferenceEvent.Thinking(startThinking, modelType))
                    }
                    if (block.isToolUse()) {
                        val startedToolUse = block.asToolUse()
                        toolUseIndex = startEvent.index()
                        pendingToolUses[startEvent.index()] =
                            PendingToolUse(
                                id = startedToolUse.id(),
                                toolName = startedToolUse.name(),
                                initialInput = startedToolUse._input(),
                            )
                    }
                }
                val contentBlockDelta = event.contentBlockDelta()
                if (contentBlockDelta.isPresent) {
                    val deltaEvent = contentBlockDelta.get()
                    val delta = deltaEvent.delta()
                    val textDelta = delta.text()
                    if (textDelta.isPresent) {
                        val text = textDelta.get().text()
                        if (text.isNotEmpty()) {
                            outputTextDeltaCount++
                            emittedAny = true
                            emitEvent(InferenceEvent.PartialResponse(text, modelType))
                        }
                    }
                    val thinkingDelta = delta.thinking()
                    if (thinkingDelta.isPresent) {
                        val text = thinkingDelta.get().thinking()
                        if (text.isNotEmpty()) {
                            thinkingTextDeltaCount++
                            emittedAny = true
                            emitEvent(InferenceEvent.Thinking(text, modelType))
                        }
                    }
                    val inputJsonDelta = delta.inputJson()
                    if (inputJsonDelta.isPresent) {
                        pendingToolUses[deltaEvent.index()]
                            ?.streamedInputJson
                            ?.append(inputJsonDelta.get().partialJson())
                    }
                }
                if (event.messageStop().isPresent) {
                    loggingPort.debug(
                        TAG,
                        @Suppress("MaxLineLength")
                        "Anthropic stream completed model=$modelId outputTextDeltas=$outputTextDeltaCount thinkingTextDeltas=$thinkingTextDeltaCount",
                    )
                }
                if (event.messageDelta().isPresent) {
                    val deltaEvent = event.messageDelta().get()
                    stopReason =
                        deltaEvent
                            .delta()
                            .stopReason()
                            .map { it.toString() }
                            .orElse(stopReason)
                    val usage = deltaEvent.usage()
                    outputTokens = usage.outputTokens().toInt()
                    usage.inputTokens().ifPresent { inputTokens = it.toInt() }
                    val rawCacheCreationDelta = usage.cacheCreationInputTokens().orElse(-1L)
                    val rawCacheReadDelta = usage.cacheReadInputTokens().orElse(-1L)
                    loggingPort.debug(
                        TAG,
                        @Suppress("MaxLineLength")
                        "Anthropic messageDelta usage model=$modelId rawCacheCreation=$rawCacheCreationDelta rawCacheRead=$rawCacheReadDelta priorCacheCreation=$cacheCreationInputTokens priorCacheRead=$cacheReadInputTokens (-1=absent, 0=present-zero)",
                    )
                    cacheCreationInputTokens =
                        usage
                            .cacheCreationInputTokens()
                            .orElse(cacheCreationInputTokens?.toLong() ?: 0L)
                            .toInt()
                            .takeIf { it > 0 }
                    cacheReadInputTokens =
                        usage
                            .cacheReadInputTokens()
                            .orElse(cacheReadInputTokens?.toLong() ?: 0L)
                            .toInt()
                            .takeIf { it > 0 }
                    loggingPort.debug(TAG, "Anthropic stream message delta model=$modelId")
                }
                if (event.contentBlockStart().isPresent) {
                    loggingPort.debug(
                        TAG,
                        "Anthropic stream content block start model=$modelId type=${
                            describeStreamEvent(event)
                        }",
                    )
                }
                if (event.contentBlockStop().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream content block stop model=$modelId")
                }
                if (event.messageStart().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream message start model=$modelId")
                }
            }

            val capturedToolUse =
                toolUseIndex
                    ?.let { pendingToolUses[it] }
                    ?.toCapturedToolUse()
            return StreamedAnthropicResponse(
                emittedAny = emittedAny,
                toolUse = capturedToolUse,
                stopReason = stopReason,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cacheCreationInputTokens = cacheCreationInputTokens,
                cacheReadInputTokens = cacheReadInputTokens,
            )
        }
    }

    private fun PendingToolUse.toCapturedToolUse(): CapturedToolUse =
        CapturedToolUse(
            id = id,
            toolName = toolName,
            argumentsJson =
                if (streamedInputJson.isNotEmpty()) {
                    toolUseArgumentsJson(toolName, streamedInputJson.toString())
                } else {
                    toolUseArgumentsJson(toolName, initialInput)
                },
        )

    private fun toolUseArgumentsJson(
        toolName: String,
        input: JsonValue,
    ): String {
        val properties: Map<*, *> = input.convert(Map::class.java) ?: emptyMap<Any?, Any?>()
        return canonicalToolArgumentsJson(toolName, properties)
    }

    private fun toolUseArgumentsJson(
        toolName: String,
        inputJson: String,
    ): String = canonicalToolArgumentsJson(toolName, parseToolInputJson(inputJson))

    private fun canonicalToolArgumentsJson(
        toolName: String,
        properties: Map<*, *>,
    ): String =
        toolEnvelopeParserPort
            .coerceProviderArguments(
                toolName = toolName,
                argsJson = TOOL_INPUT_JSON_MAPPER.writeValueAsString(properties),
                toolCatalogJson = activeToolCatalogJson,
            ).argumentsJson

    private fun toolUseInputProperties(argumentsJson: String): Map<String, JsonValue> =
        parseToolInputJson(argumentsJson).entries.associate { (key, value) ->
            key to JsonValue.from(value)
        }

    private fun parseToolInputJson(inputJson: String): Map<String, Any?> =
        runCatching {
            TOOL_INPUT_JSON_MAPPER.readValue(
                inputJson,
                object : TypeReference<Map<String, Any?>>() {},
            )
        }.getOrElse { error ->
            throw IllegalArgumentException("Tool input JSON is invalid", error)
        }
}
