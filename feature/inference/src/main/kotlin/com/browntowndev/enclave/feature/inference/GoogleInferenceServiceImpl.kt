package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolCatalogJson
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
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
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContextFactory
import com.browntowndev.enclave.feature.inference.appendonly.SessionIndexVolatilePlacement
import com.browntowndev.enclave.feature.inference.appendonly.ensureAppendOnlyContext
import com.browntowndev.enclave.feature.inference.appendonly.toAppendOnlySyncMessages
import com.browntowndev.enclave.feature.inference.appendonly.toToolsFingerprint
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.emitInferenceExecutionOutcome
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
class GoogleInferenceServiceImpl(
    private val sdkClient: GoogleGenAiSdkClient,
    private val modelId: String,
    private val modelType: ModelType,
    override val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
    private val toolEnvelopeParserPort: ToolEnvelopeParserPort,
    @Suppress("UnusedPrivateProperty") // Injected for mutation tests; streaming holdback lives in core/agent.
    private val toolStreamGatePort: ToolStreamGatePort,
    private val telemetrySupport: InferenceTelemetrySupport? = null,
    private val telemetryEventPort: TelemetryEventPort? = null,
) : RustCompatibleInferenceService {
    companion object {

        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
        private val TOOL_ARGUMENT_JSON_MAPPER = ObjectMapper()
    }

    private var appendOnlyContext: ProviderAppendOnlyContext? = null

    internal fun appendOnlyContextForTests(): ProviderAppendOnlyContext? = appendOnlyContext

    @Volatile
    private var activeToolCatalogJson: String = AgentLoopToolCatalogJson.EMPTY_ARRAY

    private val promptCacheSession =
        GooglePromptCacheSession(
            modelId = modelId,
            sdkClient = sdkClient,
            loggingPort = loggingPort,
            tag = TAG,
        )

    override fun sendPrompt(
        prompt: String,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = error("sendPrompt not supported; all Google services are wrapped by RustLoopInferenceService")

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = error("sendPrompt not supported; all Google services are wrapped by RustLoopInferenceService")

    override suspend fun setHistory(messages: List<ChatMessage>) {
        resetAppendOnlyForNewPrompt()
    }

    internal fun clearAppendOnlyOnRustCompactionBoundary(revisionAfter: Long? = null) {
        appendOnlyContext?.clearOnRustCompactionBoundary(revisionAfter)
    }

    @Suppress("UnusedParameter")
    internal fun resetAppendOnlyForNewPrompt(chatId: ChatId? = null) {
        appendOnlyContext?.invalidate()
        appendOnlyContext = null
    }

    override suspend fun closeSession() {
        promptCacheSession.close()
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
        onLifecycleEvent: suspend (InferenceEvent) -> Unit,
    ): AgentModelTurnResult {
        val startedAtMs = System.currentTimeMillis()
        try {
            loggingPort.debug(TAG, "completeSingleTurn provider=$PROVIDER model=$modelId modelType=$modelType")
            activeToolCatalogJson = AgentLoopToolCatalogJson.jsonForCatalog(options.toolCatalog)
            val timing =
                telemetrySupport?.let {
                    com.browntowndev.enclave.feature.inference.telemetry
                        .InferenceTurnTiming()
                }
            val captureContext = telemetrySupport?.captureContext(options)
            val stableHistory = sessionMessages.dropLast(1)

            appendOnlyContext =
                ProviderAppendOnlyContextFactory.ensureAppendOnlyContext(
                    current = appendOnlyContext,
                    onLogCleared = { reason ->
                        loggingPort.warning(TAG, "Append-only message log cleared: $reason")
                    },
                )
            val appendOnly = requireNotNull(appendOnlyContext)
            appendOnly.ensureStablePrefix(
                systemPrompt = systemPrompt,
                toolsFingerprint = availableTools.toToolsFingerprint(),
            )
            appendOnly.syncMessages(
                sessionMessages.toAppendOnlySyncMessages(),
                transcriptRevision = options.transcriptRevision,
            )
            val wireSidecar = appendOnly.resolveContextSidecarsForWire(contextSidecars)
            val lastUserIndex = GoogleAgentLoopContentMapper.lastNonToolResultUserIndex(sessionMessages)

            val activeCacheName =
                promptCacheSession.ensureCacheForStablePrefix(
                    systemPrompt = systemPrompt,
                    stableHistory = stableHistory,
                    availableTools = availableTools,
                    mapToContent = { msg ->
                        val index = sessionMessages.indexOfFirst { it.id == msg.id }
                        GoogleAgentLoopContentMapper.mapMessage(
                            message = msg,
                            index = index,
                            lastUserIndex = -1,
                            imageUris = emptyList(),
                            sidecarMarkers = SessionIndexVolatilePlacement.sidecarMarkersForSessionIndex(
                                sessionIndex = index,
                                frozenSidecars = appendOnly.frozenSidecarsForWireReplay(),
                                preRetrievalSidecar = null,
                                preRetrievalInsertIndex = null,
                                liveSidecar = wireSidecar,
                                tailInsertIndex = lastUserIndex.coerceAtLeast(0),
                            ),
                        )
                    },
                )

            val deltaHistory = promptCacheSession.deltaHistory(stableHistory)
            val request = GoogleRequestMapper.mapSessionToGenerateContent(
                sessionMessages = sessionMessages,
                systemPrompt = systemPrompt,
                availableTools = availableTools,
                allowToolCall = allowToolCall,
                options = options,
                appendOnly = appendOnly,
                wireSidecar = wireSidecar,
                activeCacheName = activeCacheName,
                deltaHistory = deltaHistory,
            )

            val response =
                sdkClient.generateContentStream(
                    modelId = modelId,
                    contents = request.contents,
                    config = request.config,
                    allowToolCall = allowToolCall,
                    emitEvent = { event ->
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
                    },
                )

            val toolCalls =
                response.functionCall?.let { fc ->
                    val name = fc.name().orElse(null) ?: return@let null
                    val argsJson =
                        toolEnvelopeParserPort
                            .coerceProviderArguments(
                                toolName = name,
                                argsJson = TOOL_ARGUMENT_JSON_MAPPER.writeValueAsString(fc.args().orElse(emptyMap())),
                                toolCatalogJson = activeToolCatalogJson,
                            ).argumentsJson
                    listOf(AgentRawToolCall(id = "", name = name, argumentsJson = argsJson))
                } ?: emptyList()

            val assistantTextParts = mutableListOf<String>()
            response.assistantContent?.parts()?.orElse(null)?.forEach { part ->
                if (!part.thought().orElse(false)) {
                    part.text().ifPresent { assistantTextParts.add(it) }
                }
            }
            val assistantText = assistantTextParts.joinToString("").takeIf { it.isNotBlank() }

            val outputTokenEstimate =
                assistantText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { text -> (text.length / 4).coerceAtLeast(1) }
            val telemetry =
                if (telemetrySupport != null && timing != null && captureContext != null) {
                    telemetrySupport.openAiTurnTelemetry(
                        context = captureContext,
                        providerLabel = "Google",
                        modelId = modelId,
                        modelLabel = modelId,
                        timing = timing,
                        inputTokens = response.promptTokens,
                        outputTokens = response.completionTokens,
                        reasoningTokens = response.thoughtsTokens,
                        cacheReadInputTokens = response.cachedContentTokens,
                        costUsd = null,
                        savedPricing = options.apiModelPricing,
                    )
                } else {
                    null
                }

            if (wireSidecar != null) {
                appendOnly.endTurnFreezeSidecar(
                    sidecarText = wireSidecar.text,
                    markerId = wireSidecar.markerId,
                    mappedInsertIndex = lastUserIndex.coerceAtLeast(0),
                )
            }

            return AgentModelTurnResult(
                assistantText = assistantText,
                toolCalls = toolCalls,
                finishReason =
                    googleFinishReason(
                        toolCalls = toolCalls,
                        finishReason = response.finishReason,
                        assistantText = assistantText,
                    ),
                telemetry = telemetry,
            ).also {
                telemetryEventPort.emitInferenceExecutionOutcome(
                    startedAtMs = startedAtMs,
                    provider = "Google",
                    modelId = modelId,
                    success = true,
                    tokenCount = response.completionTokens ?: outputTokenEstimate,
                    baseUrl = baseUrl,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: InferenceNetworkException) {
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = "Google",
                modelId = modelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw e
        } catch (e: java.io.IOException) {
            loggingPort.error(TAG, "completeSingleTurn network failure: ${e.message}", e)
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = "Google",
                modelId = modelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw InferenceNetworkException(
                provider = PROVIDER,
                message = e.message ?: "Connection error",
                cause = e,
            )
        } catch (e: Exception) {
            loggingPort.error(TAG, "completeSingleTurn failed: ${e.message}", e)
            telemetryEventPort.emitInferenceExecutionOutcome(
                startedAtMs = startedAtMs,
                provider = "Google",
                modelId = modelId,
                success = false,
                errorMessage = e.message,
                baseUrl = baseUrl,
            )
            throw e
        }
    }

    private fun googleFinishReason(
        toolCalls: List<AgentRawToolCall>,
        finishReason: String?,
        assistantText: String?,
    ): AgentFinishReason {
        if (toolCalls.isNotEmpty()) {
            return AgentFinishReason.ToolUse
        }
        return finishReason
            ?.let { ProviderFinishReasonMapper.fromGoogle(it, assistantText.orEmpty()).finishReason }
            ?: AgentFinishReason.Stop
    }

}
