package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.feature.inference.appendonly.OpenAiCompatibleVolatilePlacement
import com.browntowndev.enclave.feature.inference.openai.PreRetrievalContextSidecar
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContext
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContextFactory
import com.browntowndev.enclave.feature.inference.appendonly.toProviderNormalizedMessages
import com.browntowndev.enclave.feature.inference.appendonly.toToolsFingerprint
import com.browntowndev.enclave.feature.inference.openai.OpenAiResponseStreamHandler
import com.browntowndev.enclave.feature.inference.openai.OpenAiStreamUsageAccumulator
import com.browntowndev.enclave.feature.inference.openai.StreamState
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.browntowndev.enclave.feature.inference.openai.accumulateOpenAiStreamUsage
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTurnTiming
import com.fasterxml.jackson.core.type.TypeReference
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam
import com.openai.models.chat.completions.ChatCompletionContentPart
import com.openai.models.chat.completions.ChatCompletionContentPartImage
import com.openai.models.chat.completions.ChatCompletionContentPartText
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall
import com.openai.models.chat.completions.ChatCompletionMessageParam
import com.openai.models.chat.completions.ChatCompletionStreamOptions
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam
import com.openai.models.chat.completions.ChatCompletionToolChoiceOption
import com.openai.models.chat.completions.ChatCompletionToolMessageParam
import com.openai.models.chat.completions.ChatCompletionUserMessageParam
import com.openai.models.responses.ResponseCreateParams
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("LargeClass", "TooManyFunctions")
abstract class BaseOpenAiSdkInferenceService(
    protected val client: OpenAIClient,
    protected val modelId: String,
    protected val provider: String,
    protected val modelType: ModelType,
    protected val baseUrl: String? = null,
    protected val loggingPort: LoggingPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
    private val telemetrySupport: InferenceTelemetrySupport? = null,
) : RustCompatibleInferenceService {
    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val XAI_CONVERSATION_ID_HEADER = "x-grok-conv-id"
        private const val XAI_WIRE_LOG_HEAD_MESSAGE_COUNT = 3
        private const val XAI_WIRE_LOG_SYSTEM_PREVIEW_CHARS = 48
        private const val XAI_WIRE_LOG_USER_PREVIEW_CHARS = 24
    }

    protected abstract val tag: String

    protected open val maxImages: Int = 20

    protected open val useStrictToolSchemas: Boolean = false

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

    private val appendOnlyContexts = mutableMapOf<String, ProviderAppendOnlyContext>()
    private var activeAppendOnlyKey: String? = null

    internal fun appendOnlyContextForTests(): ProviderAppendOnlyContext? =
        activeAppendOnlyKey?.let(appendOnlyContexts::get)

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
        resetAppendOnlyForNewPrompt()
    }

    internal fun clearAppendOnlyOnRustCompactionBoundary() {
        appendOnlyContexts.values.forEach { it.clearOnRustCompactionBoundary() }
    }

    internal fun resetAppendOnlyForNewPrompt() = Unit

    override suspend fun closeSession() {
        conversationHistory.clear()
        appendOnlyContexts.values.forEach { it.invalidate() }
        appendOnlyContexts.clear()
        activeAppendOnlyKey = null
    }

    private fun ensureAppendOnlyContext(chatId: ChatId?): ProviderAppendOnlyContext {
        val key = appendOnlyKey(chatId)
        activeAppendOnlyKey = key
        appendOnlyContexts[key]?.let { return it }
        return ProviderAppendOnlyContextFactory
            .create(
                onLogCleared = { reason ->
                    loggingPort.warning(
                        tag,
                        "Append-only message log cleared: $reason",
                    )
                },
            ).also { appendOnlyContexts[key] = it }
    }

    private fun appendOnlyKey(chatId: ChatId?): String = chatId?.value ?: "__no_chat__"

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
        try {
            val timing = telemetrySupport?.let { InferenceTurnTiming() }
            val captureContext =
                telemetrySupport?.captureContext(
                    options = options,
                    sequenceIndex = options.telemetrySequenceIndex,
                )
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
            val imageUris = options.imageUris.take(maxImages)
            val requestModelId = options.modelId ?: modelId
            val requestProvider = provider.toApiProviderOrNull()
            val useOpenRouterCacheControl =
                if (requestProvider == ApiProvider.OPENROUTER) {
                    OpenRouterPromptCacheSupport.supportsExplicitCacheControl(requestModelId)
                } else {
                    false
                }
            val cacheControlIndex =
                if (useOpenRouterCacheControl) {
                    sessionMessages.dropLast(1).indexOfLast { it.role == Role.USER }.takeIf { it >= 0 }
                } else {
                    null
                }
            val mappedSession =
                sessionMessages.toMappedSessionBody(
                    imageUris = imageUris,
                    imageTargetIndex = lastUserIndex,
                    cacheControlIndex = cacheControlIndex,
                )
            val mappedBody = mappedSession.params

            val appendOnly = ensureAppendOnlyContext(options.chatId)
            appendOnly.ensureStablePrefix(
                systemPrompt = systemPrompt,
                toolsFingerprint = availableTools.toToolsFingerprint(),
            )
            appendOnly.syncMessages(sessionMessages.toProviderNormalizedMessages())

            val systemPromptPrepended =
                systemPrompt.isNotBlank() &&
                    mappedBody.none { it.isStableSessionSystemMessage() }
            val stableSystemMessage =
                if (systemPromptPrepended) {
                    ChatCompletionMessageParam.ofSystem(
                        buildSystemPromptMessage(
                            content = systemPrompt,
                            useCacheControl = useOpenRouterCacheControl,
                        ),
                    )
                } else {
                    null
                }
            val tailInsertIndex =
                resolveTailMappedInsertIndex(
                    sessionMessages = sessionMessages,
                    sessionIndexToMappedIndex = mappedSession.sessionIndexToMappedIndex,
                )
            val preRetrievalInsertIndex =
                resolvePreRetrievalMappedInsertIndex(
                    sessionMessages = sessionMessages,
                    sessionIndexToMappedIndex = mappedSession.sessionIndexToMappedIndex,
                )
            val preRetrievalSidecarText =
                options.volatileTurnContext
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(PreRetrievalContextSidecar::format)
            if (
                !preRetrievalSidecarText.isNullOrBlank() &&
                preRetrievalInsertIndex != null &&
                !appendOnly.hasFrozenPreRetrievalSidecar()
            ) {
                appendOnly.endTurnFreezeSidecar(
                    sidecarText = preRetrievalSidecarText,
                    mappedInsertIndex = preRetrievalInsertIndex,
                )
            }
            val livePreRetrievalSidecarText =
                if (appendOnly.hasFrozenPreRetrievalSidecar()) {
                    null
                } else {
                    preRetrievalSidecarText
                }
            val messages =
                buildList {
                    stableSystemMessage?.let(::add)
                    addAll(
                        OpenAiCompatibleVolatilePlacement.buildMappedBodyWithSidecars(
                            mappedBody = mappedBody,
                            frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                            preRetrievalSidecarText = livePreRetrievalSidecarText,
                            preRetrievalInsertIndex = preRetrievalInsertIndex,
                            currentSidecarText = contextSidecars,
                            tailInsertIndex = tailInsertIndex,
                        ),
                    )
                }
            if (!contextSidecars.isNullOrBlank()) {
                appendOnly.endTurnFreezeSidecar(
                    sidecarText = contextSidecars,
                    mappedInsertIndex = tailInsertIndex,
                )
            }

            val parameterSupport = parameterSupport(options = options, requestModelId = requestModelId)

            val builder =
                ChatCompletionCreateParams
                    .builder()
                    .model(requestModelId)
                    .messages(messages)

            val toolsAvailable = availableTools.isNotEmpty() && supportsClientTools(requestModelId)
            if (toolsAvailable) {
                availableTools.forEach { builder.addTool(it.toChatCompletionTool(strict = useStrictToolSchemas)) }
                builder.parallelToolCalls(false)
                val toolChoice =
                    if (allowToolCall) {
                        ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO)
                    } else {
                        ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE)
                    }
                builder.toolChoice(toolChoice)
            }

            if (parameterSupport?.supportsReasoningEffort != false) {
                options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
            }
            if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
                options.temperature?.let { builder.temperature(it.toDouble()) }
            }
            if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
                options.topP?.let { builder.topP(it.toDouble()) }
            }
            if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
                options.safeMaxCompletionTokens()?.let { builder.maxCompletionTokens(it.toLong()) }
            }
            applyPromptCacheRouting(builder = builder, options = options)

            builder.streamOptions(
                ChatCompletionStreamOptions.builder().includeUsage(true).build(),
            )

            val emitProviderEvent: suspend (InferenceEvent) -> Unit = { event ->
                when (event) {
                    is InferenceEvent.PartialResponse -> {
                        timing?.markFirstOutput()
                        onTextToken(event.chunk)
                    }
                    is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
                    else -> {}
                }
            }
            val chatParams = builder.build()
            val response =
                streamResponsesFirst(
                    messages = messages,
                    requestProvider = requestProvider,
                    requestModelId = requestModelId,
                    availableTools = availableTools,
                    options = options,
                    chatParams = chatParams,
                    emitEvent = emitProviderEvent,
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

            val telemetry =
                if (telemetrySupport != null && timing != null && captureContext != null) {
                    telemetrySupport
                        .openAiTurnTelemetry(
                            context = captureContext,
                            providerLabel = telemetrySupport.providerLabel(provider),
                            modelId = modelId,
                            modelLabel = modelId,
                            timing = timing,
                            inputTokens = response.inputTokens,
                            outputTokens = response.outputTokens,
                            reasoningTokens = response.reasoningTokens,
                            cacheReadInputTokens = response.cacheReadInputTokens,
                            cacheWriteInputTokens = response.cacheWriteInputTokens,
                            costUsd = response.costUsd,
                            savedPricing = options.apiModelPricing,
                        ).also { row ->
                            if (provider.toApiProviderOrNull() == ApiProvider.XAI) {
                                val wireHead =
                                    messages
                                        .take(XAI_WIRE_LOG_HEAD_MESSAGE_COUNT)
                                        .joinToString(" | ") { param ->
                                        when {
                                            param.isSystem() ->
                                                "sys:${
                                                    param.asSystem().content().asText()
                                                        .take(XAI_WIRE_LOG_SYSTEM_PREVIEW_CHARS)
                                                }"
                                            param.isUser() ->
                                                "user:${
                                                    param.asUser().content().asText()
                                                        .take(XAI_WIRE_LOG_USER_PREVIEW_CHARS)
                                                }"
                                            param.isAssistant() -> "asst"
                                            param.isTool() -> "tool"
                                            else -> "other"
                                        }
                                    }
                                loggingPort.debug(
                                    tag,
                                    "xAI telemetry turn seq=${row.sequenceIndex} allowToolCall=$allowToolCall " +
                                        "toolCalls=${toolCalls.size} wireMsgs=${messages.size} " +
                                        "appendOnlyClears=${appendOnly.clearCount} wireHead=[$wireHead] " +
                                        "input=${row.inputTokens} cacheRead=${row.cacheReadInputTokens} " +
                                        "output=${row.outputTokens}",
                                )
                            }
                        }
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
                        response.finishReason
                            ?.let { ProviderFinishReasonMapper.fromOpenAiChatStream(it, bufferedText).finishReason }
                            ?: AgentFinishReason.Stop
                    },
                providerReasoningContent = response.providerReasoningContent,
                telemetry = telemetry,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loggingPort.error(tag, "completeSingleTurn failed: ${e.message}", e)
            throw e
        }
    }

    private fun parameterSupport(
        options: GenerationOptions,
        requestModelId: String,
    ): ApiModelParameterSupport? {
        val requestProvider = options.apiProvider ?: provider.toApiProviderOrNull()
        return requestProvider?.let {
            ApiProviderModelPolicy.parameterSupport(provider = it, modelId = requestModelId)
        }
    }

    private fun supportsClientTools(requestModelId: String): Boolean {
        val requestProvider = provider.toApiProviderOrNull()
        return requestProvider != ApiProvider.XAI || !ApiProviderModelPolicy.isXaiMultiAgentModel(requestModelId)
    }

    private suspend fun streamResponsesFirst(
        messages: List<ChatCompletionMessageParam>,
        requestProvider: ApiProvider?,
        requestModelId: String,
        availableTools: List<ToolDefinition>,
        options: GenerationOptions,
        chatParams: ChatCompletionCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedOpenAiResponse {
        val responseParams =
            buildResponseParams(
                messages = messages,
                requestProvider = requestProvider,
                requestModelId = requestModelId,
                availableTools = availableTools,
                options = options,
            ) ?: return streamChatCompletions(params = chatParams, emitEvent = emitEvent)

        return try {
            streamResponses(
                params = responseParams,
                chatId = options.chatId,
                userMessageId = options.userMessageId,
                assistantMessageId = options.assistantMessageId,
                emitEvent = emitEvent,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (!chatFallbackAllowed(requestProvider, requestModelId)) {
                throw error
            }
            loggingPort.warning(
                tag,
                "Responses API failed; falling back to Chat Completions provider=$provider model=$requestModelId " +
                    "error=${error.message ?: error::class.java.simpleName}",
            )
            streamChatCompletions(params = chatParams, emitEvent = emitEvent)
        }
    }

    private fun buildResponseParams(
        messages: List<ChatCompletionMessageParam>,
        requestProvider: ApiProvider?,
        requestModelId: String,
        availableTools: List<ToolDefinition>,
        options: GenerationOptions,
    ): ResponseCreateParams? {
        if (requestProvider != ApiProvider.XAI && requestProvider != ApiProvider.OPENAI) {
            return null
        }
        val mapperInput = messages.toResponseMapperInput()
        val responseOptions =
            options.copy(
                apiProvider = requestProvider,
                modelId = requestModelId,
                toolingEnabled = availableTools.isNotEmpty(),
                activeTools = availableTools,
            )
        return if (requestProvider == ApiProvider.XAI) {
            XaiRequestMapper.mapToResponseParams(
                modelId = requestModelId,
                prompt = mapperInput.prompt,
                history = mapperInput.history,
                options = responseOptions,
            )
        } else {
            OpenAiRequestMapper.mapToResponseParams(
                modelId = requestModelId,
                prompt = mapperInput.prompt,
                history = mapperInput.history,
                options = responseOptions,
            )
        }
    }

    private fun chatFallbackAllowed(
        requestProvider: ApiProvider?,
        requestModelId: String,
    ): Boolean =
        when (requestProvider) {
            ApiProvider.XAI -> XaiRequestMapper.shouldAllowChatCompletionsFallback(requestModelId)
            ApiProvider.OPENAI -> true
            else -> true
        }

    private data class ResponseMapperInput(
        val history: List<ChatMessage>,
        val prompt: String,
    )

    private fun List<ChatCompletionMessageParam>.toResponseMapperInput(): ResponseMapperInput {
        val mapped = mapNotNull { it.toChatMessageOrNull() }
        val latestUserIndex = mapped.indexOfLast { it.role == Role.USER }
        if (latestUserIndex < 0) {
            return ResponseMapperInput(history = mapped, prompt = "")
        }
        val latestUser = mapped[latestUserIndex]
        return ResponseMapperInput(
            history = mapped.take(latestUserIndex) + mapped.drop(latestUserIndex + 1),
            prompt = latestUser.content,
        )
    }

    private fun ChatCompletionMessageParam.toChatMessageOrNull(): ChatMessage? =
        when {
            isSystem() -> ChatMessage(Role.SYSTEM, asSystem().content().asText())
            isUser() -> ChatMessage(Role.USER, asUser().content().asPlainText())
            isAssistant() -> ChatMessage(Role.ASSISTANT, asAssistant().content().asPlainTextOrEmpty())
            isTool() -> ChatMessage(Role.USER, asTool().content().asText())
            else -> null
        }

    private fun ChatCompletionUserMessageParam.Content.asPlainText(): String =
        when {
            isText() -> asText()
            isArrayOfContentParts() ->
                asArrayOfContentParts()
                    .mapNotNull { part ->
                        if (part.isText()) {
                            part.asText().text()
                        } else {
                            null
                        }
                    }.joinToString(separator = "\n\n")
            else -> toString()
        }

    private fun Optional<ChatCompletionAssistantMessageParam.Content>.asPlainTextOrEmpty(): String {
        if (!isPresent) return ""
        val content = get()
        return when {
            content.isText() -> content.asText()
            else -> content.toString()
        }
    }

    private fun buildSystemPromptMessage(
        content: String,
        useCacheControl: Boolean,
    ): ChatCompletionSystemMessageParam =
        if (useCacheControl) {
            ChatCompletionSystemMessageParam
                .builder()
                .contentOfArrayOfContentParts(
                    listOf(OpenRouterPromptCacheSupport.cachedSystemTextPart(content)),
                ).build()
        } else {
            ChatCompletionSystemMessageParam.builder().content(content).build()
        }

    private fun applyPromptCacheRouting(
        builder: ChatCompletionCreateParams.Builder,
        options: GenerationOptions,
    ) {
        val conversationId = options.chatId?.value?.takeIf { it.isNotBlank() } ?: return
        when (provider.toApiProviderOrNull()) {
            ApiProvider.XAI -> builder.putAdditionalHeader(XAI_CONVERSATION_ID_HEADER, conversationId)
            ApiProvider.OPENAI -> builder.promptCacheKey(conversationId)
            else -> Unit
        }
    }

    private data class MappedSessionBody(
        val params: List<ChatCompletionMessageParam>,
        val sessionIndexToMappedIndex: IntArray,
    )

    private fun List<AgentLoopMessage>.toMappedSessionBody(
        imageUris: List<String>,
        imageTargetIndex: Int,
        cacheControlIndex: Int?,
    ): MappedSessionBody {
        val sessionIndexToMappedIndex = IntArray(size) { -1 }
        val pendingToolCallIds = mutableSetOf<String>()
        val params = mutableListOf<ChatCompletionMessageParam>()
        forEachIndexed { index, message ->
            val attachImages = index == imageTargetIndex && imageUris.isNotEmpty()
            val wireContent = message.content
            val mappedMessage =
                message.copy(content = wireContent).toOpenAiChatParam(
                    pendingToolCallIds = pendingToolCallIds,
                    imageUris = if (attachImages) imageUris else emptyList(),
                    useCacheControl = index == cacheControlIndex && !attachImages,
                )
            if (mappedMessage != null) {
                sessionIndexToMappedIndex[index] = params.size
                mappedMessage.toolCallIds.let { pendingToolCallIds.addAll(it) }
                params.add(mappedMessage.param)
            }
        }
        return MappedSessionBody(
            params = params,
            sessionIndexToMappedIndex = sessionIndexToMappedIndex,
        )
    }

    /**
     * Live tail anchor: last session message that maps to provider history, excluding engine-internal
     * payloads (tool budget, nudges) that would shift the sidecar away from the tool-result tail.
     */
    private fun resolvePreRetrievalMappedInsertIndex(
        sessionMessages: List<AgentLoopMessage>,
        sessionIndexToMappedIndex: IntArray,
    ): Int? {
        val userSessionIndex =
            sessionMessages.indexOfLast { message ->
                message.role == Role.USER && !message.metadataJson.isToolCallMetadata()
            }
        if (userSessionIndex < 0) return null
        return sessionIndexToMappedIndex.getOrNull(userSessionIndex)?.takeIf { it >= 0 }
    }

    private fun resolveTailMappedInsertIndex(
        sessionMessages: List<AgentLoopMessage>,
        sessionIndexToMappedIndex: IntArray,
    ): Int {
        if (sessionMessages.isEmpty()) return 0
        val anchorSessionIndex =
            sessionMessages.indices.lastOrNull { index ->
                val metadata = sessionMessages[index].metadataJson.toMetadataJsonObject()
                !metadata.isEngineInternalToolSidecar()
            } ?: 0
        val mappedIndex = sessionIndexToMappedIndex.getOrNull(anchorSessionIndex) ?: -1
        if (mappedIndex >= 0) {
            return OpenAiCompatibleVolatilePlacement.tailMappedInsertIndex(
                mappedBodySize = mappedIndex + 1,
            )
        }
        val lastMappedIndex = sessionIndexToMappedIndex.lastOrNull { it >= 0 } ?: -1
        return OpenAiCompatibleVolatilePlacement.tailMappedInsertIndex(
            mappedBodySize = (lastMappedIndex + 1).coerceAtLeast(0),
        )
    }

    private fun AgentLoopMessage.toOpenAiChatParam(
        pendingToolCallIds: MutableSet<String>,
        imageUris: List<String> = emptyList(),
        useCacheControl: Boolean = false,
    ): MappedChatMessage? {
        val metadata = metadataJson.toMetadataJsonObject()
        if (metadata.isEngineInternalToolSidecar()) {
            return MappedChatMessage(
                param =
                    ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam
                            .builder()
                            .content("[Agent]\n${content.ifBlank { "{}" }}")
                            .build(),
                    ),
            )
        }
        val toolCallId = metadata?.get("call_id")?.jsonPrimitive?.contentOrNull
        if (toolCallId != null) {
            return toToolResultChatParam(toolCallId = toolCallId, pendingToolCallIds = pendingToolCallIds)
        }

        return when (role) {
            Role.SYSTEM ->
                MappedChatMessage(
                    param =
                        ChatCompletionMessageParam.ofSystem(
                            buildSystemPromptMessage(
                                content = content,
                                useCacheControl = useCacheControl,
                            ),
                        ),
                )
            Role.USER ->
                MappedChatMessage(
                    param =
                        if (imageUris.isEmpty()) {
                            ChatCompletionMessageParam.ofUser(
                                buildUserMessage(
                                    content = content,
                                    useCacheControl = useCacheControl,
                                ),
                            )
                        } else {
                            ChatCompletionMessageParam.ofUser(buildUserMessageWithImages(content, imageUris))
                        },
                )
            Role.ASSISTANT -> toAssistantChatParam(metadata)
        }
    }

    private fun buildUserMessageWithImages(
        content: String,
        imageUris: List<String>,
    ): ChatCompletionUserMessageParam {
        val parts =
            buildList {
                add(
                    ChatCompletionContentPart.ofText(
                        ChatCompletionContentPartText.builder().text(content).build(),
                    ),
                )
                ImagePayloads.fromUris(imageUris).forEach { payload ->
                    ImagePayloads.validate(payload)
                    add(
                        ChatCompletionContentPart.ofImageUrl(
                            ChatCompletionContentPartImage
                                .builder()
                                .imageUrl(
                                    ChatCompletionContentPartImage.ImageUrl
                                        .builder()
                                        .url(payload.dataUrl)
                                        .build(),
                                ).build(),
                        ),
                    )
                }
            }
        return ChatCompletionUserMessageParam.builder().contentOfArrayOfContentParts(parts).build()
    }

    private fun buildUserMessage(
        content: String,
        useCacheControl: Boolean,
    ): ChatCompletionUserMessageParam =
        if (useCacheControl) {
            ChatCompletionUserMessageParam
                .builder()
                .contentOfArrayOfContentParts(
                    listOf(OpenRouterPromptCacheSupport.cachedUserTextPart(content)),
                ).build()
        } else {
            ChatCompletionUserMessageParam.builder().content(content).build()
        }

    private fun String.toMetadataJsonObject() =
        if (this != "null" && isNotBlank()) {
            runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()
        } else {
            null
        }

    /**
     * True for real transcript system rows, not volatile sidecars or engine-internal system rows
     * (budget, compaction summary) that must not suppress the stable system prompt prepend.
     */
    private fun ChatCompletionMessageParam.isStableSessionSystemMessage(): Boolean =
        isSystem() && !isVolatileOrEngineSystemMessage()

    private fun ChatCompletionMessageParam.isVolatileOrEngineSystemMessage(): Boolean {
        if (!isSystem()) return false
        val text = asSystem().content().asText()
        return PreRetrievalContextSidecar.isSidecarText(text) ||
            text.startsWith("State ledger JSON:") ||
            text.startsWith("Context management sidecars:") ||
            text.startsWith("Tool context index JSON:") ||
            text.startsWith("File context index JSON:") ||
            text.startsWith("Cache hints:") ||
            text.startsWith("[Agent]") ||
            text.startsWith("[Conversation summary]")
    }

    private fun String.isToolCallMetadata(): Boolean {
        val metadata = toMetadataJsonObject() ?: return false
        return metadata["call_id"]?.jsonPrimitive?.contentOrNull != null
    }

    /** Engine-injected payloads without a provider `tool_call_id` (budget, nudges, parse errors). */
    private fun JsonObject?.isEngineInternalToolSidecar(): Boolean =
        this != null &&
            (
                containsKey("agent_tool_budget") ||
                    containsKey("tools_blocked_nudge") ||
                    containsKey("envelope_parse_failure")
            )

    private fun AgentLoopMessage.toToolResultChatParam(
        toolCallId: String,
        pendingToolCallIds: MutableSet<String>,
    ): MappedChatMessage {
        if (toolCallId.isNotBlank() && pendingToolCallIds.remove(toolCallId)) {
            return MappedChatMessage(
                param =
                    ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam
                            .builder()
                            .content(content.ifBlank { "{}" })
                            .toolCallId(toolCallId)
                            .build(),
                    ),
            )
        }

        loggingPort.warning(
            tag,
            "Converting orphan tool result to user message model=$modelId messageId=$id callId=$toolCallId",
        )
        return MappedChatMessage(
            param =
                ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam
                        .builder()
                        .content("[Tool result]\n${content.ifBlank { "{}" }}")
                        .build(),
                ),
        )
    }

    private fun AgentLoopMessage.toAssistantChatParam(metadata: JsonObject?): MappedChatMessage? {
        if (id.startsWith("compact-summary-")) {
            return MappedChatMessage(
                param =
                    ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam
                            .builder()
                            .content("[Conversation summary]\n$content")
                            .build(),
                    ),
            )
        }

        val assistantBuilder = ChatCompletionAssistantMessageParam.builder()
        if (content.isNotBlank()) {
            assistantBuilder.content(content)
        }
        metadata.providerReasoningContent()?.let { reasoningContent ->
            assistantBuilder.putAdditionalProperty("reasoning_content", JsonValue.from(reasoningContent))
        }
        val toolCallIds = mutableListOf<String>()
        val rawCalls = metadata?.get("raw_tool_calls")
        if (rawCalls is JsonArray) {
            rawCalls.forEach { tc ->
                val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                val callName = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@forEach
                val rawCallId = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (rawCallId == null) {
                    loggingPort.warning(
                        tag,
                        "Dropping assistant tool call without provider id model=$modelId messageId=$id tool=$callName",
                    )
                    return@forEach
                }
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
                toolCallIds.add(rawCallId)
            }
        }
        if (content.isBlank() && toolCallIds.isEmpty()) {
            loggingPort.warning(tag, "Skipping empty assistant chat message model=$modelId messageId=$id")
            return null
        }
        return MappedChatMessage(
            param = ChatCompletionMessageParam.ofAssistant(assistantBuilder.build()),
            toolCallIds = toolCallIds,
        )
    }

    private data class MappedChatMessage(
        val param: ChatCompletionMessageParam,
        val toolCallIds: List<String> = emptyList(),
    )

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
                    if (state.outputTextDeltaCount > 0 && state.trackedToolCalls.isEmpty()) {
                        throw RuntimeException(
                            "Stream ended mid-response without finish signal (provider=$provider model=$modelId)",
                        )
                    }
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
            providerReasoningContent = state.accumulatedReasoning.toString().takeIf { it.isNotBlank() },
            inputTokens = state.inputTokens,
            outputTokens = state.outputTokens,
            reasoningTokens = state.reasoningTokens,
            cacheReadInputTokens = state.cacheReadInputTokens,
            cacheWriteInputTokens = state.cacheWriteInputTokens,
            costUsd = state.costUsd,
        )
    }

    protected open suspend fun streamResponses(
        params: ResponseCreateParams,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        assistantMessageId: MessageId? = null,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedOpenAiResponse {
        logResponsesRequest(params)
        val handler =
            OpenAiResponseStreamHandler(
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
        var state = StreamState()

        try {
            client.responses().createStreaming(params).use { streamResponse ->
                val iterator = streamResponse.stream().iterator()
                while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                    val event = runInterruptible { iterator.next() }
                    state = handler.handleEvent(event, state)
                }
            }
        } catch (e: OpenAIServiceException) {
            val detail = describeException(e)
            loggingPort.error(tag, "Responses stream failed model=$modelId $detail", e)
            throw RuntimeException(detail, e)
        }

        return handler.toStreamedResponse(state)
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
        accumulateOpenAiStreamUsage(event, state.usageAccumulator)
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
            state.accumulatedReasoning.append(reasoningText)
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
        val accumulatedReasoning: StringBuilder = StringBuilder(),
        val trackedToolCalls: MutableMap<Long, TrackedToolCall> = mutableMapOf(),
        val usageAccumulator: OpenAiStreamUsageAccumulator = OpenAiStreamUsageAccumulator(),
        var costUsd: BigDecimal? = null,
    ) {
        val inputTokens: Int? get() = usageAccumulator.inputTokens
        val outputTokens: Int? get() = usageAccumulator.outputTokens
        val reasoningTokens: Int? get() = usageAccumulator.reasoningTokens
        val cacheReadInputTokens: Int? get() = usageAccumulator.cacheReadInputTokens
        val cacheWriteInputTokens: Int? get() = usageAccumulator.cacheWriteInputTokens
    }

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

    private fun logResponsesRequest(params: ResponseCreateParams) {
        loggingPort.debug(
            tag,
            @Suppress("MaxLineLength")
            "Responses request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(
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

    private fun String.toApiProviderOrNull(): ApiProvider? =
        ApiProvider.entries.firstOrNull { apiProvider -> apiProvider.name.equals(this, ignoreCase = true) }

    private fun GenerationOptions.safeMaxCompletionTokens(): Int? {
        val configuredMaxTokens = maxTokens ?: return null
        val configuredContextWindow = contextWindow
        if (configuredContextWindow != null && configuredMaxTokens >= configuredContextWindow) {
            return null
        }
        return configuredMaxTokens
    }
}

private fun JsonObject?.providerReasoningContent(): String? =
    this
        ?.get("provider_reasoning")
        ?.jsonObject
        ?.get("reasoning_content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }

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
