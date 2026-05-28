package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
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
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.browntowndev.enclave.feature.inference.telemetry.emitInferenceExecutionOutcome
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.genai.types.Content
import com.google.genai.types.FunctionCallingConfig
import com.google.genai.types.FunctionCallingConfigMode
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ToolConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.CopyOnWriteArrayList

class GoogleInferenceServiceImpl(
    private val sdkClient: GoogleGenAiSdkClient,
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
    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
        private val TOOL_ARGUMENT_JSON_MAPPER = ObjectMapper()
    }

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

    private var appendOnlyContext: ProviderAppendOnlyContext? = null

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
    ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> =
        flow {
            try {
                require(options.imageUris.size <= 3600) {
                    "Google API supports a maximum of 3600 images per request."
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
                loggingPort.error(TAG, "Tool request failed. exception=${e::class.java.simpleName} message=${e.message}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: IllegalStateException) {
                loggingPort.error(TAG, "Tool request failed. exception=${e::class.java.simpleName} message=${e.message}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Unknown error"
                loggingPort.error(TAG, "API request failed. exception=${e::class.java.simpleName} message=$errorMsg", e)
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

        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = prompt,
                history = requestHistory,
                options = options,
            )

        loggingPort.info(
            TAG,
            "Using Google GenAI SDK. model=$modelId reasoningBudget=${options.reasoningBudget} maxTokens=${options.maxTokens}",
        )
        loggingPort.debug(
            TAG,
            @Suppress("MaxLineLength")
            "GenerateContent request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(
                request.config.toString(),
            )}",
        )

        sdkClient.generateContentStream(
            modelId = modelId,
            contents = request.contents,
            config = request.config,
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
    ): AgentModelTurnResult {
        val startedAtMs = System.currentTimeMillis()
        try {
            activeToolCatalogJson = AgentLoopToolCatalogJson.jsonForCatalog(options.toolCatalog)
            val timing =
                telemetrySupport?.let {
                    com.browntowndev.enclave.feature.inference.telemetry
                        .InferenceTurnTiming()
                }
            val captureContext = telemetrySupport?.captureContext(options)
            val lastUserIndex = GoogleAgentLoopContentMapper.lastNonToolResultUserIndex(sessionMessages)
            val imageUris = options.imageUris.take(3600)

            val stableHistory = sessionMessages.dropLast(1)
            val latestMessage =
                sessionMessages.lastOrNull()
                    ?: throw IllegalArgumentException("sessionMessages cannot be empty")
            val latestIndex = sessionMessages.lastIndex

            val latestUserPrepends = contextSidecars?.trim()?.takeIf { it.isNotEmpty() }

            fun mapAtIndex(
                message: AgentLoopMessage,
                index: Int,
                prependVolatile: Boolean,
                attachImages: Boolean,
            ): Content? =
                GoogleAgentLoopContentMapper.mapMessage(
                    message = message,
                    index = index,
                    lastUserIndex = if (attachImages) lastUserIndex else -1,
                    imageUris = if (attachImages) imageUris else emptyList(),
                    prependedText = if (prependVolatile) latestUserPrepends else null,
                )

            val activeCacheName =
                promptCacheSession.ensureCacheForStablePrefix(
                    systemPrompt = systemPrompt,
                    stableHistory = stableHistory,
                    availableTools = availableTools,
                    mapToContent = { msg ->
                        val index = sessionMessages.indexOfFirst { it.id == msg.id }
                        mapAtIndex(msg, index, prependVolatile = false, attachImages = false)
                    },
                )

            val configBuilder = GenerateContentConfig.builder()
            options.temperature?.let { configBuilder.temperature(it) }
            options.topP?.let { configBuilder.topP(it) }
            options.topK?.let { configBuilder.topK(it.toFloat()) }
            options.maxTokens?.let { configBuilder.maxOutputTokens(it) }
            resolveThinkingConfig(options)?.let { configBuilder.thinkingConfig(it) }

            if (availableTools.isNotEmpty()) {
                configBuilder.tools(availableTools.map { it.toGoogleTool() })
                val mode =
                    if (allowToolCall) {
                        FunctionCallingConfigMode.Known.AUTO
                    } else {
                        FunctionCallingConfigMode.Known.NONE
                    }
                configBuilder.toolConfig(
                    ToolConfig
                        .builder()
                        .functionCallingConfig(
                            FunctionCallingConfig
                                .builder()
                                .mode(mode)
                                .build(),
                        ).build(),
                )
            }

            if (activeCacheName != null) {
                configBuilder.cachedContent(activeCacheName)
            } else if (systemPrompt.isNotBlank()) {
                configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)))
            }

            val deltaHistory = promptCacheSession.deltaHistory(stableHistory)
            val contentsToSend =
                buildList {
                    deltaHistory.forEach { msg ->
                        val index = sessionMessages.indexOfFirst { it.id == msg.id }
                        mapAtIndex(msg, index, prependVolatile = false, attachImages = false)?.let(::add)
                    }
                    mapAtIndex(
                        latestMessage,
                        latestIndex,
                        prependVolatile = !latestUserPrepends.isNullOrBlank(),
                        attachImages = true,
                    )?.let(::add)
                }

            val response =
                sdkClient.generateContentStream(
                    modelId = modelId,
                    contents = contentsToSend,
                    config = configBuilder.build(),
                    allowToolCall = allowToolCall,
                    emitEvent = { event ->
                        when (event) {
                            is InferenceEvent.PartialResponse -> {
                                timing?.markFirstOutput()
                                onTextToken(event.chunk)
                            }
                            is InferenceEvent.Thinking -> onThinkingToken(event.chunk)
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
                    telemetrySupport.localTurnTelemetry(
                        context = captureContext,
                        providerLabel = "Google",
                        modelLabel = modelId,
                        timing = timing,
                        outputTokens = outputTokenEstimate,
                    )
                } else {
                    null
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
                    tokenCount = outputTokenEstimate,
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
            )
            throw InferenceNetworkException(
                provider = PROVIDER,
                message = e.message ?: "Connection error",
                cause = e,
            )
        } catch (e: Exception) {
            loggingPort.error(TAG, "completeSingleTurn failed: ${e.message}", e)
            throw e
        }
    }

    private fun resolveThinkingConfig(options: GenerationOptions): ThinkingConfig? {
        if (options.reasoningBudget <= 0) {
            return null
        }
        return ThinkingConfig
            .builder()
            .thinkingBudget(options.reasoningBudget)
            .includeThoughts(true)
            .build()
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
                "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${payload.sha256.take(8)}"
            }
        loggingPort.info(
            TAG,
            "Image payloads provider=$PROVIDER model=$modelId count=${payloads.size} $details",
        )
    }
}
