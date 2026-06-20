package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.ApiModelParameterSupport
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiProviderModelPolicy
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.appendonly.OpenAiCompatibleVolatilePlacement
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContext
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContextFactory
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarkerFactory
import com.browntowndev.enclave.feature.inference.appendonly.toAppendOnlySyncMessages
import com.browntowndev.enclave.feature.inference.appendonly.toToolsFingerprint
import com.browntowndev.enclave.feature.inference.wire.AgentLoopRowKind
import com.browntowndev.enclave.feature.inference.wire.OpenAiAssistantReplayPolicy
import com.browntowndev.enclave.feature.inference.wire.OpenAiWireMessageMapper
import com.browntowndev.enclave.feature.inference.wire.classify
import com.browntowndev.enclave.feature.inference.wire.lastNonToolResultUserIndex
import com.browntowndev.enclave.feature.inference.wire.providerReasoningContent
import com.browntowndev.enclave.feature.inference.wire.toMetadataJsonObject
import com.browntowndev.enclave.feature.inference.wire.truncateForLogs as sharedTruncateForLogs
import com.browntowndev.enclave.feature.inference.openai.OpenAiResponseStreamHandler
import com.browntowndev.enclave.feature.inference.openai.OpenAiStreamUsageAccumulator
import com.browntowndev.enclave.feature.inference.openai.OpenAiWireJsonSerializer
import com.browntowndev.enclave.feature.inference.openai.PreRetrievalContextSidecar
import com.browntowndev.enclave.feature.inference.openai.StreamState
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.browntowndev.enclave.feature.inference.openai.accumulateOpenAiStreamUsage
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTurnTiming
import com.browntowndev.enclave.feature.inference.telemetry.PromptCacheDiagnosticsContext
import com.browntowndev.enclave.feature.inference.telemetry.PromptCachePrefixDiagnostics
import com.browntowndev.enclave.feature.inference.telemetry.PromptCacheSidecarDiagnostics
import com.browntowndev.enclave.feature.inference.telemetry.emitInferenceExecutionOutcome
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.math.BigDecimal
import java.util.Optional
import java.util.concurrent.CopyOnWriteArrayList

@Suppress("LargeClass", "TooManyFunctions")
abstract class BaseOpenAiSdkInferenceService(
    protected val client: OpenAIClient,
    protected val modelId: String,
    protected val provider: String,
    protected val modelType: ModelType,
    protected val openRouterRouting: OpenRouterRoutingConfiguration? = null,
    override val baseUrl: String? = null,
    protected val loggingPort: LoggingPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
    private val telemetrySupport: InferenceTelemetrySupport? = null,
    private val telemetryEventPort: TelemetryEventPort? = null,
) : RustCompatibleInferenceService {
    companion object {
        internal const val TOOL_REPLAY_PLACEHOLDER_CONTENT = "."
    }

    protected abstract val tag: String

    /**
     * OpenAI Chat Completions wire mapper, wired with this service's assistant-replay policy so
     * subclass overrides of [normalizeAssistantContentForReplay], [applyAssistantContent], and
     * [shouldAttachProviderReasoningContent] flow through the shared [OpenAiWireMessageMapper].
     */
    private val wireMapper: OpenAiWireMessageMapper by lazy {
        OpenAiWireMessageMapper(
            tag = tag,
            modelId = modelId,
            loggingPort = loggingPort,
            assistantPolicy = baseAssistantReplayPolicy,
        )
    }

    private val baseAssistantReplayPolicy =
        object : OpenAiAssistantReplayPolicy {
            override fun normalizeContent(
                requestModelId: String,
                content: String,
                options: GenerationOptions,
                hasToolCalls: Boolean,
            ): String = normalizeAssistantContentForReplay(requestModelId, content, options, hasToolCalls)

            override fun applyContent(
                builder: ChatCompletionAssistantMessageParam.Builder,
                normalizedContent: String,
            ) = applyAssistantContent(builder, normalizedContent)

            override fun shouldAttachReasoning(
                requestModelId: String,
                options: GenerationOptions,
                reasoningContent: String?,
            ): Boolean {
                if (reasoningContent.isNullOrBlank()) return false
                // Reconstruct the metadata shape the base hook expects so subclass overrides
                // (DeepSeek strips on replay) keep working against the same signature.
                val metadata = buildJsonObject {
                    putJsonObject("provider_reasoning") {
                        put("reasoning_content", reasoningContent)
                    }
                }
                return shouldAttachProviderReasoningContent(requestModelId, options, metadata)
            }
        }

    protected open val maxImages: Int = 20

    protected open val useStrictToolSchemas: Boolean = false

    protected open fun toApiTemperature(value: Float): Double = value.toDouble()

    protected open fun toApiTopP(value: Float): Double = value.toDouble()

    protected open fun applyMaxOutputTokens(
        builder: ChatCompletionCreateParams.Builder,
        tokenLimit: Int,
        requestModelId: String,
    ) {
        builder.maxCompletionTokens(tokenLimit.toLong())
    }

    protected open fun shouldIncludeToolChoice(
        requestModelId: String,
        toolsAvailable: Boolean,
        options: GenerationOptions,
    ): Boolean = toolsAvailable

    protected open fun applyProviderChatExtras(
        builder: ChatCompletionCreateParams.Builder,
        requestModelId: String,
        options: GenerationOptions,
    ): Unit = Unit

    protected open fun shouldAttachProviderReasoningContent(
        requestModelId: String,
        options: GenerationOptions,
        metadata: JsonObject?,
    ): Boolean =
        options.includeReasoningContentOnRequest &&
            metadata.providerReasoningContent() != null

    protected open fun supportsClientToolsForModel(requestModelId: String): Boolean = true

    protected open fun supportsExplicitCacheControl(requestModelId: String): Boolean = false

    protected open fun applyPromptCacheRouting(
        builder: ChatCompletionCreateParams.Builder,
        options: GenerationOptions,
    ) {
        val conversationId = options.chatId?.value?.takeIf { it.isNotBlank() } ?: return
        val requestProvider = options.apiProvider ?: provider.toApiProviderOrNull()
        if (requestProvider == ApiProvider.OPENAI) {
            builder.promptCacheKey(conversationId)
        }
    }

    protected open fun shouldAllowChatCompletionsFallback(requestModelId: String): Boolean = true

    internal open fun buildResponseParams(
        responseWireSegments: List<ResponseWireSegment>,
        requestModelId: String,
        availableTools: List<ToolDefinition>,
        options: GenerationOptions,
    ): ResponseCreateParams? {
        val requestProvider = options.apiProvider ?: provider.toApiProviderOrNull()
        if (requestProvider == ApiProvider.OPENAI) {
            val responseOptions = options.copy(
                apiProvider = ApiProvider.OPENAI,
                modelId = requestModelId,
            )
            val wire = ResponseWireSegmentSupport.resolveForOpenAi(responseWireSegments)
            return OpenAiRequestMapper.mapSessionToResponseParams(
                modelId = requestModelId,
                wire = wire,
                options = responseOptions,
                availableTools = availableTools,
            )
        }
        return null
    }

    protected open fun enablePromptCacheDiagnostics(): Boolean = false

    protected open fun logPromptCacheProviderExtras(context: PromptCacheDiagnosticsContext) {}

    protected open fun logTurnTelemetry(
        row: TurnTelemetry,
        messages: List<ChatCompletionMessageParam>,
        allowToolCall: Boolean,
        toolCalls: List<AgentRawToolCall>,
        appendOnlyClears: Int,
    ) {}

    private fun logPromptCacheDiagnostics(
        sequenceIndex: Int,
        inputTokens: Int?,
        cacheReadInputTokens: Int?,
        messages: List<ChatCompletionMessageParam>,
        allowToolCall: Boolean,
        toolCalls: List<AgentRawToolCall>,
        appendOnlyClears: Int,
        appendOnlyLastClearReason: String?,
        diagnosticsContext: PromptCacheDiagnosticsContext,
        wireJsonBody: String,
        sidecarCapture: PromptCacheSidecarDiagnostics.SidecarWireCapture,
    ) {
        if (!enablePromptCacheDiagnostics()) {
            return
        }
        val stateKey = diagnosticsContext.chatId?.value?.takeIf { it.isNotBlank() } ?: "__no_chat__"
        val currentShape =
            PromptCachePrefixDiagnostics.captureShape(
                systemPrompt = diagnosticsContext.systemPrompt,
                toolNames = diagnosticsContext.toolNames,
                messages = messages,
                frozenSidecarCount = diagnosticsContext.frozenSidecarCount,
                liveSidecarPresent = diagnosticsContext.liveSidecarPresent,
            )
        val currentBody =
            PromptCachePrefixDiagnostics.renderWireBody(
                systemPrompt = diagnosticsContext.systemPrompt,
                toolNames = diagnosticsContext.toolNames,
                messages = messages,
            )
        val previous = promptCacheDiagnosticsState[stateKey]
        val sidecarManifest =
            PromptCacheSidecarDiagnostics.captureFrom(
                frozenOnWire = sidecarCapture.frozenOnWire,
                liveSidecarText = sidecarCapture.liveSidecarText,
                preRetrievalSidecarText = sidecarCapture.preRetrievalSidecarText,
                tailInsertIndex = sidecarCapture.tailInsertIndex,
                mappedBodySize = sidecarCapture.mappedBodySize,
                frozenAfterFreezeCount = sidecarCapture.frozenAfterFreezeCount,
                stableSystemPrompt = diagnosticsContext.systemPrompt,
                messages = messages,
            )
        val sidecarDelta = PromptCacheSidecarDiagnostics.diff(previous?.sidecarManifest, sidecarManifest)
        val systemJsonSpans = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(wireJsonBody)
        loggingPort.debug(
            tag,
            "PROMPT_CACHE_SIDECAR seq=$sequenceIndex chat=$stateKey " +
                PromptCacheSidecarDiagnostics.formatManifest(sidecarManifest),
        )
        sidecarDelta?.let { delta ->
            loggingPort.debug(
                tag,
                "PROMPT_CACHE_SIDECAR_DELTA seq=$sequenceIndex chat=$stateKey " +
                    PromptCacheSidecarDiagnostics.formatDelta(delta),
            )
        }
        loggingPort.debug(
            tag,
            "PROMPT_CACHE_SIDECAR_JSON seq=$sequenceIndex chat=$stateKey " +
                PromptCacheSidecarDiagnostics.formatBlock0(wireJsonBody) +
                " sysSpans=" +
                systemJsonSpans.joinToString("|") { span ->
                    "@${span.roleCharOffset}:${span.contentHash.take(8)}(len=${span.contentLength})"
                },
        )
        previous?.wireJsonBody?.let { previousJson ->
            loggingPort.debug(
                tag,
                "PROMPT_CACHE_SIDECAR_JSON_DELTA seq=$sequenceIndex chat=$stateKey " +
                    PromptCacheSidecarDiagnostics.formatEarlyJsonBreak(previousJson, wireJsonBody) +
                    " sysJsonDelta=" +
                    PromptCacheSidecarDiagnostics.diffSystemJsonSpans(
                        previous = PromptCacheSidecarDiagnostics.extractSystemJsonSpans(previousJson),
                        current = systemJsonSpans,
                    ),
            )
        }
        val comparison =
            PromptCachePrefixDiagnostics.compare(
                previous = previous?.shape,
                current = currentShape,
                previousInputTokens = previous?.inputTokens,
                currentInputTokens = inputTokens,
            )
        previous?.body?.let { previousBody ->
            val breakReport =
                PromptCachePrefixDiagnostics.computePrefixBreak(
                    previousBody = previousBody,
                    newBody = currentBody,
                    windowChars = PromptCachePrefixDiagnostics.PREFIX_BREAK_WINDOW_CHARS,
                )
            loggingPort.debug(
                tag,
                "PROMPT_CACHE_BREAK seq=$sequenceIndex chat=$stateKey " +
                    "match=${breakReport.matchChars}/${breakReport.newChars} (${breakReport.matchPct}%) " +
                    "prevLen=${previousBody.length} " +
                    "before=${PromptCachePrefixDiagnostics.sanitizeForLog(breakReport.contextBefore)} " +
                    "old=${PromptCachePrefixDiagnostics.sanitizeForLog(breakReport.oldSnippet)} " +
                    "new=${PromptCachePrefixDiagnostics.sanitizeForLog(breakReport.newSnippet)}",
            )
        }
        previous?.wireJsonBody?.let { previousJson ->
            val jsonBreakReport =
                PromptCachePrefixDiagnostics.computePrefixBreak(
                    previousBody = previousJson,
                    newBody = wireJsonBody,
                    windowChars = PromptCachePrefixDiagnostics.PREFIX_BREAK_WINDOW_CHARS,
                )
            loggingPort.debug(
                tag,
                "PROMPT_CACHE_JSON_BREAK seq=$sequenceIndex chat=$stateKey " +
                    "match=${jsonBreakReport.matchChars}/${jsonBreakReport.newChars} (${jsonBreakReport.matchPct}%) " +
                    "prevLen=${previousJson.length} " +
                    "before=${PromptCachePrefixDiagnostics.sanitizeForLog(jsonBreakReport.contextBefore)} " +
                    "old=${PromptCachePrefixDiagnostics.sanitizeForLog(jsonBreakReport.oldSnippet)} " +
                    "new=${PromptCachePrefixDiagnostics.sanitizeForLog(jsonBreakReport.newSnippet)}",
            )
        }
        promptCacheDiagnosticsState[stateKey] =
            PromptCacheDiagnosticsState(
                shape = currentShape,
                inputTokens = inputTokens,
                body = currentBody,
                wireJsonBody = wireJsonBody,
                sidecarManifest = sidecarManifest,
            )
        val cacheRead = cacheReadInputTokens ?: 0
        val input = inputTokens ?: 0
        val hitPct = if (input > 0) (cacheRead * 100) / input else 0
        val wireHead = PromptCachePrefixDiagnostics.formatWireHead(messages)
        val reasons =
            if (comparison.changeReasons.isEmpty()) {
                "none"
            } else {
                comparison.changeReasons.joinToString("+")
            }
        loggingPort.debug(
            tag,
            "PROMPT_CACHE seq=$sequenceIndex chat=$stateKey model=${diagnosticsContext.requestModelId} " +
                "allowToolCall=$allowToolCall toolCalls=${toolCalls.size} " +
                "sessionMsgs=${diagnosticsContext.sessionMessageCount} wireMsgs=${messages.size} " +
                "tools=${currentShape.toolCount} frozenOnWire=${sidecarCapture.frozenOnWire.size} " +
                "frozenAfterFreeze=${sidecarCapture.frozenAfterFreezeCount} " +
                "liveSidecar=${currentShape.liveSidecarPresent} appendOnlyClears=$appendOnlyClears " +
                "appendOnlyLastClear=${appendOnlyLastClearReason ?: "none"} " +
                "input=$input cacheRead=$cacheRead hitPct=$hitPct " +
                "inputDelta=${comparison.inputTokenDelta ?: "n/a"} prefixChanged=${comparison.prefixChanged} " +
                "reasons=$reasons wireHash=${currentShape.wireHash} " +
                "prevWireHash=${comparison.previousWireHash ?: "n/a"} " +
                "systemHash=${currentShape.systemHash} toolsHash=${currentShape.toolsHash} " +
                "wireHead=[$wireHead]",
        )
        logPromptCacheProviderExtras(diagnosticsContext)
    }

    protected open fun normalizeAssistantContentForReplay(
        requestModelId: String,
        content: String,
        options: GenerationOptions,
        hasToolCalls: Boolean,
    ): String {
        if (!options.includeReasoningContentOnRequest || !hasToolCalls) {
            return content
        }
        if (content.isNotBlank()) {
            return content
        }
        return TOOL_REPLAY_PLACEHOLDER_CONTENT
    }

    protected open fun applyAssistantContent(
        assistantBuilder: ChatCompletionAssistantMessageParam.Builder,
        normalizedContent: String,
    ) {
        if (normalizedContent.isBlank()) {
            return
        }
        assistantBuilder.content(normalizedContent)
    }

    private val appendOnlyContexts = mutableMapOf<String, ProviderAppendOnlyContext>()
    private var activeAppendOnlyKey: String? = null

    private data class PromptCacheDiagnosticsState(
        val shape: PromptCachePrefixDiagnostics.PrefixShape,
        val inputTokens: Int?,
        val body: String,
        val wireJsonBody: String,
        val sidecarManifest: PromptCacheSidecarDiagnostics.SidecarManifest,
    )

    private val promptCacheDiagnosticsState = mutableMapOf<String, PromptCacheDiagnosticsState>()

    internal fun appendOnlyContextForTests(): ProviderAppendOnlyContext? = activeAppendOnlyKey?.let(appendOnlyContexts::get)

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
        resetAppendOnlyForNewPrompt()
    }

    internal fun clearAppendOnlyOnRustCompactionBoundary(revisionAfter: Long? = null) {
        appendOnlyContexts.values.forEach { it.clearOnRustCompactionBoundary(revisionAfter) }
    }

    internal fun resetAppendOnlyForNewPrompt(chatId: ChatId? = null) {
        val key = chatId?.value?.takeIf { it.isNotBlank() } ?: activeAppendOnlyKey
        key?.let { appendOnlyContexts[it]?.markTurnBoundaryForReanchor() }
    }

    override suspend fun closeSession() {
        appendOnlyContexts.values.forEach { it.invalidate() }
        appendOnlyContexts.clear()
        activeAppendOnlyKey = null
        promptCacheDiagnosticsState.clear()
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

    private fun captureSidecarWireState(
        appendOnly: ProviderAppendOnlyContext,
        wireSidecar: ResolvedSidecar?,
        livePreRetrievalSidecarText: String?,
        tailInsertIndex: Int,
        mappedBodySize: Int,
    ): PromptCacheSidecarDiagnostics.SidecarWireCapture {
        val frozenOnWire =
            appendOnly.frozenSidecarsForWireReplay().map { snapshot ->
                PromptCacheSidecarDiagnostics.slotFrom(
                    mappedInsertIndex = snapshot.mappedInsertIndex,
                    text = snapshot.text,
                )
            }
        return PromptCacheSidecarDiagnostics.SidecarWireCapture(
            frozenOnWire = frozenOnWire,
            liveSidecarText = wireSidecar?.text,
            preRetrievalSidecarText = livePreRetrievalSidecarText,
            tailInsertIndex = tailInsertIndex,
            mappedBodySize = mappedBodySize,
            frozenAfterFreezeCount = appendOnly.frozenSidecarsForWireReplay().size,
        )
    }

    private fun finalizeSidecarFreeze(
        appendOnly: ProviderAppendOnlyContext,
        wireSidecar: ResolvedSidecar?,
        tailInsertIndex: Int,
        sidecarWireCapture: PromptCacheSidecarDiagnostics.SidecarWireCapture,
    ): PromptCacheSidecarDiagnostics.SidecarWireCapture {
        if (wireSidecar != null) {
            appendOnly.endTurnFreezeSidecar(
                sidecarText = wireSidecar.text,
                markerId = wireSidecar.markerId,
                mappedInsertIndex = tailInsertIndex,
            )
        }
        return sidecarWireCapture.copy(
            frozenAfterFreezeCount = appendOnly.frozenSidecarsForWireReplay().size,
        )
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        contextSidecars: String?,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
        onLifecycleEvent: suspend (InferenceEvent) -> Unit,
    ): AgentModelTurnResult {
        val startedAtMs = System.currentTimeMillis()
        val resolvedModelId = options.modelId ?: modelId
        try {
            val timing = telemetrySupport?.let { InferenceTurnTiming() }
            val captureContext = telemetrySupport?.captureContext(options)
            val lastUserIndex = sessionMessages.lastNonToolResultUserIndex()
            val imageUris = options.imageUris.take(maxImages)
            val requestModelId = options.modelId ?: modelId
            val useExplicitCacheControl = supportsExplicitCacheControl(requestModelId)
            val cacheControlIndex =
                if (useExplicitCacheControl) {
                    sessionMessages.dropLast(1).indexOfLast { it.role == Role.USER }.takeIf { it >= 0 }
                } else {
                    null
                }
            val mappedSession =
                sessionMessages.toMappedSessionBody(
                    imageUris = imageUris,
                    imageTargetIndex = lastUserIndex,
                    cacheControlIndex = cacheControlIndex,
                    useExplicitCacheControl = useExplicitCacheControl,
                    requestModelId = requestModelId,
                    options = options,
                )
            val mappedResponseSession =
                AgentLoopOpenAiResponseMapper.mapSession(
                    sessionMessages = sessionMessages,
                    imageUris = imageUris,
                    imageTargetIndex = lastUserIndex,
                    loggingPort = loggingPort,
                    tag = tag,
                    modelId = requestModelId,
                )
            val mappedBody = mappedSession.params

            val appendOnly = ensureAppendOnlyContext(options.chatId)
            appendOnly.ensureStablePrefix(
                systemPrompt = systemPrompt,
                toolsFingerprint = availableTools.toToolsFingerprint(),
            )
            appendOnly.syncMessages(
                sessionMessages.toAppendOnlySyncMessages(),
                transcriptRevision = options.transcriptRevision,
            )
            val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)

            val systemPromptPrepended =
                systemPrompt.isNotBlank() &&
                    mappedBody.none { it.isStableSessionSystemMessage() }
            val stableSystemMessage =
                if (systemPromptPrepended) {
                    ChatCompletionMessageParam.ofSystem(
                        buildSystemPromptMessage(
                            content = systemPrompt,
                            useCacheControl = useExplicitCacheControl,
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
            val responseTailInsertIndex =
                resolveTailMappedInsertIndex(
                    sessionMessages = sessionMessages,
                    sessionIndexToMappedIndex = mappedResponseSession.sessionIndexToMappedIndex,
                )
            val preRetrievalInsertIndex =
                resolvePreRetrievalMappedInsertIndex(
                    sessionMessages = sessionMessages,
                    sessionIndexToMappedIndex = mappedSession.sessionIndexToMappedIndex,
                )
            val responsePreRetrievalInsertIndex =
                resolvePreRetrievalMappedInsertIndex(
                    sessionMessages = sessionMessages,
                    sessionIndexToMappedIndex = mappedResponseSession.sessionIndexToMappedIndex,
                )
            val preRetrievalSidecarText =
                options.volatileTurnContext
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(PreRetrievalContextSidecar::format)
            val preRetrievalSidecarMarker =
                preRetrievalSidecarText?.let { SidecarMarkerFactory.newMarker(it) }
            if (
                preRetrievalSidecarMarker != null &&
                preRetrievalInsertIndex != null &&
                !appendOnly.hasFrozenPreRetrievalSidecar()
            ) {
                appendOnly.endTurnFreezeSidecar(
                    sidecarText = preRetrievalSidecarText,
                    markerId = preRetrievalSidecarMarker.markerId,
                    mappedInsertIndex = preRetrievalInsertIndex,
                )
            }
            val livePreRetrievalSidecar: ResolvedSidecar? =
                if (
                    appendOnly.hasFrozenPreRetrievalSidecar() ||
                    preRetrievalSidecarMarker == null ||
                    preRetrievalSidecarText == null
                ) {
                    null
                } else {
                    ResolvedSidecar(
                        text = preRetrievalSidecarText,
                        markerId = preRetrievalSidecarMarker.markerId,
                    )
                }
            val livePreRetrievalSidecarText = livePreRetrievalSidecar?.text
            val messages =
                buildList {
                    stableSystemMessage?.let(::add)
                    addAll(
                        OpenAiCompatibleVolatilePlacement.embedSidecarsIntoUserMessages(
                            mappedBody = mappedBody,
                            frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                            preRetrievalSidecar = livePreRetrievalSidecar,
                            preRetrievalInsertIndex = preRetrievalInsertIndex,
                            liveSidecar = wireSidecar,
                            tailInsertIndex = tailInsertIndex,
                        ),
                    )
                }
            val responseWireSegments =
                OpenAiCompatibleResponseSidecarPlacement.buildSegmentsWithSidecars(
                    mappedSegments = mappedResponseSession.segments,
                    frozenSidecars =
                        OpenAiCompatibleResponseSidecarPlacement.remapFrozenSidecarsForResponses(
                            frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                            chatSessionIndexToMappedIndex = mappedSession.sessionIndexToMappedIndex,
                            responseSessionIndexToMappedIndex = mappedResponseSession.sessionIndexToMappedIndex,
                        ),
                    preRetrievalSidecar = livePreRetrievalSidecar,
                    preRetrievalInsertIndex = responsePreRetrievalInsertIndex,
                    currentSidecar = wireSidecar,
                    tailInsertIndex = responseTailInsertIndex,
                    prependedSystemPrompt = systemPrompt.takeIf { systemPromptPrepended },
                )
            val sidecarWireCapture =
                captureSidecarWireState(
                    appendOnly = appendOnly,
                    wireSidecar = wireSidecar,
                    livePreRetrievalSidecarText = livePreRetrievalSidecarText,
                    tailInsertIndex = tailInsertIndex,
                    mappedBodySize = mappedBody.size,
                )
            val sidecarCaptureForDiagnostics =
                finalizeSidecarFreeze(
                    appendOnly = appendOnly,
                    wireSidecar = wireSidecar,
                    tailInsertIndex = tailInsertIndex,
                    sidecarWireCapture = sidecarWireCapture,
                )

            val parameterSupport = parameterSupport(options = options, requestModelId = requestModelId)

            val builder =
                ChatCompletionCreateParams
                    .builder()
                    .model(requestModelId)
                    .messages(messages)

            val toolsAvailable = availableTools.isNotEmpty() && supportsClientToolsForModel(requestModelId)
            if (toolsAvailable) {
                availableTools.forEach { builder.addTool(it.toChatCompletionTool(strict = useStrictToolSchemas)) }
                builder.parallelToolCalls(false)
                if (shouldIncludeToolChoice(requestModelId, toolsAvailable, options)) {
                    val toolChoice =
                        if (allowToolCall) {
                            ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.AUTO)
                        } else {
                            ChatCompletionToolChoiceOption.ofAuto(ChatCompletionToolChoiceOption.Auto.NONE)
                        }
                    builder.toolChoice(toolChoice)
                }
            }

            if (parameterSupport?.supportsReasoningEffort != false) {
                options.reasoningEffort?.let { builder.reasoningEffort(ReasoningMapper.toSdkEffort(it)) }
            }
            if (!options.temperatureDisabled && parameterSupport?.supportsTemperature != false) {
                options.temperature?.let { builder.temperature(toApiTemperature(it)) }
            }
            if (!options.topPDisabled && parameterSupport?.supportsTopP != false) {
                options.topP?.let { builder.topP(toApiTopP(it)) }
            }
            if (!options.maxTokensDisabled && parameterSupport?.supportsMaxTokens != false) {
                options.safeMaxCompletionTokens()?.let { tokenLimit ->
                    applyMaxOutputTokens(
                        builder = builder,
                        tokenLimit = tokenLimit,
                        requestModelId = requestModelId,
                    )
                }
            }
            applyProviderChatExtras(
                builder = builder,
                requestModelId = requestModelId,
                options = options,
            )
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
                    is InferenceEvent.Thinking -> {
                        timing?.markFirstOutput()
                        onThinkingToken(event.chunk)
                    }
                    else -> {}
                }
            }
            val chatParams = builder.build()
            val response =
                streamResponsesFirst(
                    responseWireSegments = responseWireSegments,
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

            val diagnosticsContext =
                PromptCacheDiagnosticsContext(
                    requestModelId = requestModelId,
                    chatId = options.chatId,
                    systemPrompt = systemPrompt,
                    toolNames = availableTools.map { it.name },
                    frozenSidecarCount = sidecarCaptureForDiagnostics.frozenOnWire.size,
                    liveSidecarPresent = !contextSidecars.isNullOrBlank(),
                    sessionMessageCount = sessionMessages.size,
                )
            logPromptCacheDiagnostics(
                sequenceIndex = options.telemetrySequenceIndex,
                inputTokens = response.inputTokens,
                cacheReadInputTokens = response.cacheReadInputTokens,
                messages = messages,
                allowToolCall = allowToolCall,
                toolCalls = toolCalls,
                appendOnlyClears = appendOnly.clearCount,
                appendOnlyLastClearReason = appendOnly.lastClearReason,
                diagnosticsContext = diagnosticsContext,
                wireJsonBody = OpenAiWireJsonSerializer.serializeBody(chatParams._body()),
                sidecarCapture = sidecarCaptureForDiagnostics,
            )

            val telemetry =
                if (telemetrySupport != null && timing != null && captureContext != null) {
                    telemetrySupport
                        .openAiTurnTelemetry(
                            context = captureContext,
                            providerLabel = telemetrySupport.providerLabel(provider),
                            modelId = resolvedModelId,
                            modelLabel = resolvedModelId,
                            timing = timing,
                            inputTokens = response.inputTokens,
                            outputTokens = response.outputTokens,
                            reasoningTokens = response.reasoningTokens,
                            cacheReadInputTokens = response.cacheReadInputTokens,
                            cacheWriteInputTokens = response.cacheWriteInputTokens,
                            costUsd = response.costUsd,
                            savedPricing = options.apiModelPricing,
                        ).also { row ->
                            logTurnTelemetry(
                                row = row,
                                messages = messages,
                                allowToolCall = allowToolCall,
                                toolCalls = toolCalls,
                                appendOnlyClears = appendOnly.clearCount,
                            )
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
            ).also {
                telemetryEventPort.emitInferenceExecutionOutcome(
                    startedAtMs = startedAtMs,
                    provider = provider,
                    modelId = resolvedModelId,
                    success = true,
                    tokenCount = response.outputTokens,
                    costUsd = response.costUsd,
                    inputTokens = response.inputTokens,
                    cacheReadInputTokens = response.cacheReadInputTokens,
                    baseUrl = baseUrl,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InferenceNetworkException) {
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = provider,
                modelId = resolvedModelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw e
        } catch (e: java.io.IOException) {
            loggingPort.error(tag, "completeSingleTurn network failure: ${e.message}", e)
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = provider,
                modelId = resolvedModelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw InferenceNetworkException(
                provider = provider,
                message = e.message ?: "Connection error",
                cause = e,
            )
        } catch (e: Exception) {
            loggingPort.error(tag, "completeSingleTurn failed: ${e.message}", e)
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = provider,
                modelId = resolvedModelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw e
        }
    }

    private fun parameterSupport(
        options: GenerationOptions,
        requestModelId: String,
    ): ApiModelParameterSupport? {
        val requestProvider = options.apiProvider ?: provider.toApiProviderOrNull()
        return requestProvider?.let {
            ApiProviderModelPolicy.parameterSupport(
                provider = it,
                modelId = requestModelId,
                reasoningBudget = options.reasoningBudget,
            )
        }
    }

    private suspend fun streamResponsesFirst(
        responseWireSegments: List<ResponseWireSegment>,
        requestModelId: String,
        availableTools: List<ToolDefinition>,
        options: GenerationOptions,
        chatParams: ChatCompletionCreateParams,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedOpenAiResponse {
        val responseParams =
            buildResponseParams(
                responseWireSegments = responseWireSegments,
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
            if (!shouldAllowChatCompletionsFallback(requestModelId)) {
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

    // buildResponseParams and chatFallbackAllowed have been refactored into open methods.

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

    private data class MappedSessionBody(
        val params: List<ChatCompletionMessageParam>,
        val sessionIndexToMappedIndex: IntArray,
    )

    private fun List<AgentLoopMessage>.toMappedSessionBody(
        imageUris: List<String>,
        imageTargetIndex: Int,
        cacheControlIndex: Int?,
        useExplicitCacheControl: Boolean,
        requestModelId: String,
        options: GenerationOptions,
    ): MappedSessionBody {
        val sessionIndexToMappedIndex = IntArray(size) { -1 }
        val pendingToolCallIds = mutableSetOf<String>()
        val params = mutableListOf<ChatCompletionMessageParam>()
        forEachIndexed { index, message ->
            val attachImages = index == imageTargetIndex && imageUris.isNotEmpty()
            val useCacheControl = (index == cacheControlIndex && !attachImages) ||
                (useExplicitCacheControl && message.role == Role.SYSTEM && !message.isVolatileOrEngineSystemMessage())
            val imageUrisForRow = if (attachImages) imageUris else emptyList()
            val kind = message.classify(attachImages = attachImages, pendingToolCallIds = pendingToolCallIds)
            var keptAssistantToolCallIds: List<String> = emptyList()
            val mappedParam: ChatCompletionMessageParam? =
                when (kind) {
                    is AgentLoopRowKind.SystemPrompt ->
                        wireMapper.systemPrompt(kind.content, useCacheControl)
                    is AgentLoopRowKind.UserText ->
                        wireMapper.userText(kind.content, imageUrisForRow, useCacheControl)
                    is AgentLoopRowKind.EngineInternal ->
                        kind.wireText.takeIf { it.isNotBlank() }?.let(wireMapper::engineInternal)
                    is AgentLoopRowKind.CompactSummary ->
                        wireMapper.compactSummary(kind.content, useCacheControl)
                    is AgentLoopRowKind.ToolResult ->
                        wireMapper.toolResult(kind.toolCallId, kind.content, kind.isOrphan, useCacheControl)
                    is AgentLoopRowKind.AssistantText ->
                        wireMapper.assistantText(
                            content = kind.content,
                            toolCalls = kind.toolCalls,
                            reasoningContent = kind.reasoningContent,
                            useCacheControl = useCacheControl,
                            requestModelId = requestModelId,
                            options = options,
                            messageId = message.id,
                        )?.also { keptAssistantToolCallIds = it.toolCallIds }?.param
                    null -> null
                }
            if (mappedParam != null) {
                sessionIndexToMappedIndex[index] = params.size
                pendingToolCallIds.addAll(keptAssistantToolCallIds)
                params.add(mappedParam)
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
                !EngineInternalMessageMapper.isEngineInternalMessage(sessionMessages[index].metadataJson)
            } ?: 0
        val mappedIndex = sessionIndexToMappedIndex.getOrNull(anchorSessionIndex) ?: -1
        if (mappedIndex >= 0) {
            val isToolResult = sessionMessages.getOrNull(anchorSessionIndex)?.metadataJson?.isToolCallMetadata() == true
            return OpenAiCompatibleVolatilePlacement.tailMappedInsertIndex(
                mappedBodySize = mappedIndex + 1,
                isToolResult = isToolResult,
            )
        }
        val lastMappedIndex = sessionIndexToMappedIndex.lastOrNull { it >= 0 } ?: -1
        val lastMappedSessionIndex = sessionIndexToMappedIndex.indexOfLast { it == lastMappedIndex }
        val isToolResult = if (lastMappedSessionIndex >= 0) {
            sessionMessages.getOrNull(lastMappedSessionIndex)?.metadataJson?.isToolCallMetadata() == true
        } else {
            false
        }
        return OpenAiCompatibleVolatilePlacement.tailMappedInsertIndex(
            mappedBodySize = (lastMappedIndex + 1).coerceAtLeast(0),
            isToolResult = isToolResult,
        )
    }


    /**
     * True for real transcript system rows, not volatile sidecars or engine-internal system rows
     * (budget, compaction summary) that must not suppress the stable system prompt prepend.
     */
    private fun ChatCompletionMessageParam.isStableSessionSystemMessage(): Boolean = isSystem() && !isVolatileOrEngineSystemMessage()

    private fun ChatCompletionMessageParam.isVolatileOrEngineSystemMessage(): Boolean {
        if (!isSystem()) return false
        val text = asSystem().content().asText()
        // formatForProviderWire collapses _agent payloads to ""; a real stable session
        // system message is never blank. Treat blank as volatile so it cannot suppress
        // the stable system-prompt prepend (which would drop the system message off the
        // wire and break prefix-based prompt caching on tool-result sub-turns).
        return text.isBlank() ||
            PreRetrievalContextSidecar.isSidecarText(text) ||
            text.startsWith("State ledger JSON:") ||
            text.startsWith("Tool context index JSON:") ||
            text.startsWith("File context index JSON:") ||
            EngineInternalMessageMapper.isEngineInternalWireText(text) ||
            text.startsWith("[Conversation summary]")
    }

    private fun AgentLoopMessage.isVolatileOrEngineSystemMessage(): Boolean {
        if (role != Role.SYSTEM) return false
        // formatForProviderWire collapses _agent payloads to ""; a real stable session
        // system message is never blank. Treat blank as volatile so it cannot suppress
        // the stable system-prompt prepend.
        return content.isBlank() ||
            PreRetrievalContextSidecar.isSidecarText(content) ||
            content.startsWith("State ledger JSON:") ||
            content.startsWith("Tool context index JSON:") ||
            content.startsWith("File context index JSON:") ||
            EngineInternalMessageMapper.isEngineInternalWireText(content) ||
            content.startsWith("[Conversation summary]")
    }

    private fun String.isToolCallMetadata(): Boolean =
        toMetadataJsonObject()?.get("call_id")?.jsonPrimitive?.contentOrNull != null

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

                if (!state.finishedEmitted && currentCoroutineContext().isActive) {
                    loggingPort.warning(
                        tag,
                        "Chat stream ended without finishReason model=$modelId " +
                            "outputTextDeltas=${state.outputTextDeltaCount} " +
                            "reasoningChars=${state.accumulatedReasoning.length} " +
                            "toolCallDeltas=${state.trackedToolCalls.size}",
                    )
                    // Throw for every truncated stream — including reasoning-only (CoT) and
                    // mid-tool-call truncation — so the agent loop retries instead of treating
                    // a dropped connection as a completed turn. The message must contain
                    // "stream ended unexpectedly" so retry classification treats it as transient.
                    throw RuntimeException(
                        "Model stream ended unexpectedly without finish signal " +
                            "(provider=$provider model=$modelId)",
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
                        modelId = params.model().toString() ?: modelId,
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
                modelId = params.model().map { it.toString() }.orElse(modelId),
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
                if (!state.receivedTerminalEvent && currentCoroutineContext().isActive) {
                    loggingPort.warning(
                        tag,
                        "Responses stream ended without terminal event model=$modelId " +
                            "outputTextDeltas=${state.outputTextDeltaCount} " +
                            "reasoningTextDeltas=${state.reasoningTextDeltaCount} " +
                            "toolCalls=${state.toolCallRequests.size}",
                    )
                    throw RuntimeException(
                        "Model stream ended unexpectedly without finish signal " +
                            "(provider=$provider model=$modelId)",
                    )
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
    ) {
        val inputTokens: Int? get() = usageAccumulator.inputTokens
        val outputTokens: Int? get() = usageAccumulator.outputTokens
        val reasoningTokens: Int? get() = usageAccumulator.reasoningTokens
        val cacheReadInputTokens: Int? get() = usageAccumulator.cacheReadInputTokens
        val cacheWriteInputTokens: Int? get() = usageAccumulator.cacheWriteInputTokens
        val costUsd: BigDecimal? get() = usageAccumulator.costUsd
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

    private fun serializeBody(body: Any): String = OpenAiWireJsonSerializer.serializeBody(body)

    protected fun truncateForLogs(value: String): String = sharedTruncateForLogs(value)

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
