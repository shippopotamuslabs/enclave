package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentContextReference
import com.browntowndev.enclave.domain.model.inference.AgentLoopAgentConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopContextPolicyTier
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopSessionCapabilities
import com.browntowndev.enclave.domain.model.inference.AgentLoopSessionIoParams
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentSummaryRequest
import com.browntowndev.enclave.domain.model.inference.AgentSummaryResult
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.CommittedInferenceTranscriptPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferencePromptPort
import com.browntowndev.enclave.domain.port.inference.NoopCommittedInferenceTranscriptPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.usecase.chat.InferencePromptContentBuilder
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.chat.ToolSchemaDisclosureResolver
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.feature.inference.telemetry.emitInferenceExecutionOutcome
import com.browntowndev.enclave.domain.util.AppendBlocksToolResultParser
import com.browntowndev.enclave.domain.util.MediaResultParser
import com.browntowndev.enclave.domain.util.WebSearchResultParser
import com.browntowndev.enclave.feature.inference.appendonly.ContextSidecarCompaction
import com.browntowndev.enclave.feature.inference.appendonly.clearAppendOnlyOnRustCompactionBoundary
import com.browntowndev.enclave.feature.inference.appendonly.resetAppendOnlyForNewPrompt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Provider

private const val TAG = "RustLoopInferenceService"
private const val DEFAULT_CONTEXT_WINDOW = 128_000
private const val HIGH_CONTEXT_WINDOW_THRESHOLD = 128_000

/**
 * Wraps a [RustCompatibleInferenceService] (provider model turn) and [AgentLoopPort]
 * (Rust session engine) into a single [LlmInferencePort].
 *
 * History, compaction, tool retry, and KV-cache tracking are all delegated to the Rust
 * engine. Kotlin handles model I/O and tool execution at each callback boundary.
 */
@Suppress("LargeClass")
class RustLoopInferenceService(
    private val inner: RustCompatibleInferenceService,
    private val agentLoopPort: AgentLoopPort,
    private val toolExecutor: ToolExecutorPort,
    private val loggingPort: LoggingPort,
    private val provider: String,
    private val modelType: ModelType,
    private val inferenceFactoryProvider: Provider<InferenceFactoryPort>,
    private val rollingSummarizer: RollingSummarizer,
    private val resolveCompactionSummaryModel: ResolveCompactionSummaryModelUseCase,
    private val committedInferencePrompts: CommittedInferencePromptPort = NoopCommittedInferencePromptPort,
    private val committedInferenceTranscripts: CommittedInferenceTranscriptPort = NoopCommittedInferenceTranscriptPort,
    private val telemetryEventPort: TelemetryEventPort? = null,
) : RustCompatibleInferenceService {
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val disclosureResolver = ToolSchemaDisclosureResolver()
    private var lastSeenRevision = 0L
    private var latestProviderTranscript: List<ChatMessage> = emptyList()

    override fun sendPrompt(
        prompt: String,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> =
        channelFlow {
            val startedAtMs = System.currentTimeMillis()
            try {
                inner.resetAppendOnlyForNewPrompt()
                val history = buildHistory(options.chatId)
                val userContent = buildUserContent(prompt, options)
                val initialMessages = history + listOf(ChatMessage(Role.USER, userContent))
                val config = buildAgentLoopConfig(options)
                val assistantText = StringBuilder()

                var telemetrySequenceIndex = 0
                val host =
                    object : AgentLoopHostCallbacks {
                        override suspend fun completeModelTurn(
                            sessionMessages: List<AgentLoopMessage>,
                            systemPrompt: String,
                            toolsSchemaJson: String,
                            allowToolCall: Boolean,
                            contextReferences: List<AgentContextReference>,
                            stateLedgerJson: String?,
                            toolContextIndexJson: String?,
                            fileContextIndexJson: String?,
                            cacheHints: List<String>,
                            transcriptRevision: Long,
                            onTextToken: suspend (String) -> Unit,
                            onThinkingToken: suspend (String) -> Unit,
                        ): AgentModelTurnResult {
                            val turnOptions =
                                options.copy(
                                    assistantMessageId = options.assistantMessageId,
                                    chatId = options.chatId,
                                    telemetrySequenceIndex = telemetrySequenceIndex,
                                    transcriptRevision = transcriptRevision,
                                )
                            val result =
                                inner.completeSingleTurn(
                                    sessionMessages = sessionMessages,
                                    systemPrompt = systemPrompt,
                                    contextSidecars =
                                        buildContextSidecars(
                                            contextReferences = contextReferences,
                                            stateLedgerJson = stateLedgerJson,
                                            toolContextIndexJson = toolContextIndexJson,
                                            fileContextIndexJson = fileContextIndexJson,
                                            cacheHints = cacheHints,
                                        ),
                                    // Full eligible tool pool on the wire for prefix-cache stability;
                                    // Rust active subset is conveyed via sidecar tool context index.
                                    // Outer allowToolCall still drives provider toolChoice NONE.
                                    availableTools =
                                        wireToolsForRequest(
                                            options = options,
                                            toolContextIndexJson = toolContextIndexJson,
                                        ),
                                    allowToolCall = allowToolCall,
                                    onTextToken = onTextToken,
                                    onThinkingToken = onThinkingToken,
                                    options = turnOptions,
                                )
                            latestProviderTranscript = buildProviderTranscript(sessionMessages, result)
                            result.telemetry?.let { telemetry ->
                                send(InferenceEvent.TelemetryCaptured(telemetry, modelType))
                            }
                            telemetrySequenceIndex++
                            return result
                        }

                        override suspend fun executeTools(calls: List<AgentToolCall>): List<AgentToolResult> =
                            coroutineScope {
                                calls
                                    .map { call ->
                                        async(Dispatchers.IO) { executeOneTool(call, options) }
                                    }.awaitAll()
                            }

                        override suspend fun onToolResult(
                            toolName: String,
                            argumentsJson: String,
                            resultJson: String,
                        ) {
                            emitToolSideEffects(toolName, argumentsJson, resultJson, options) { send(it) }
                        }

                        override suspend fun summarize(request: AgentSummaryRequest): AgentSummaryResult {
                            val messages = request.messagesToSummarize.map { it.toChatMessage() }
                            val summaryModelType =
                                resolveCompactionSummaryModel.invoke(
                                    activeTurnModelType = modelType,
                                    messagesToSummarize = messages,
                                )
                            loggingPort.debug(
                                TAG,
                                "compaction_summary provider=$provider activeTurn=$modelType " +
                                    "selectedModel=$summaryModelType messageCount=${messages.size} " +
                                    "trigger=${request.trigger.name}",
                            )
                            val summaryText =
                                inferenceFactoryProvider.get().withInferenceService(summaryModelType) { summaryService ->
                                    if (summaryService is RustCompatibleInferenceService) {
                                        val prompt =
                                            rollingSummarizer.buildAgentSummaryPrompt(
                                                messages = messages,
                                                instruction = request.instruction,
                                                focusInstruction = request.focusInstruction,
                                                historyRef = request.historyRef,
                                                stateLedgerSnapshotJson = request.stateLedgerSnapshotJson,
                                                trigger = request.trigger,
                                            )
                                        summaryService
                                            .completeSingleTurn(
                                                sessionMessages = listOf(AgentLoopMessage("summary-request", Role.USER, prompt)),
                                                systemPrompt = "",
                                                contextSidecars = null,
                                                availableTools = emptyList(),
                                                allowToolCall = false,
                                                onTextToken = {},
                                                onThinkingToken = {},
                                                options = GenerationOptions(reasoningBudget = 0, maxTokens = 1024),
                                            ).assistantText
                                    } else {
                                        rollingSummarizer.summarizeAgentRequest(
                                            messages = messages,
                                            service = summaryService,
                                            instruction = request.instruction,
                                            focusInstruction = request.focusInstruction,
                                            historyRef = request.historyRef,
                                            stateLedgerSnapshotJson = request.stateLedgerSnapshotJson,
                                            trigger = request.trigger,
                                        )
                                    }
                                }
                            return AgentSummaryResult(
                                summaryText = summaryText ?: "Summary unavailable.",
                                lastMessageId = request.lastMessageId,
                            )
                        }
                    }

                val sessionContext = buildSessionContext(options)
                agentLoopPort
                    .execute(
                        initialMessages = initialMessages,
                        config = config,
                        sessionContext = sessionContext,
                        host = host,
                    ).collect { event ->
                        when (event) {
                            is AgentLoopEvent.TextToken -> {
                                assistantText.append(event.text)
                                send(InferenceEvent.PartialResponse(event.text, modelType))
                            }
                            is AgentLoopEvent.ThinkingToken -> send(InferenceEvent.Thinking(event.text, modelType))
                            is AgentLoopEvent.ToolCallExecuted -> {}
                            is AgentLoopEvent.TurnStarted -> {}
                            is AgentLoopEvent.TurnCompleted -> {}
                            is AgentLoopEvent.CompactionApplied ->
                                send(
                                    InferenceEvent.CompactionApplied(
                                        tier = event.tier,
                                        tokensBefore = event.tokensBefore,
                                        tokensAfter = event.tokensAfter,
                                        modelType = modelType,
                                        trigger = event.trigger,
                                    ),
                                )
                            is AgentLoopEvent.ToolRepaired ->
                                send(InferenceEvent.ToolRepaired(event.originalName, event.resolvedName, event.confidence, modelType))
                            is AgentLoopEvent.KvCacheInvalidated ->
                                loggingPort.info(TAG, "KV cache invalidated reason=${event.reason}")
                            is AgentLoopEvent.PreCompact ->
                                send(InferenceEvent.PreCompact(event.trigger, modelType)).also {
                                    loggingPort.debug(TAG, "pre_compact trigger=${event.trigger}")
                                }
                            is AgentLoopEvent.CompactionBoundaryApplied -> {
                                inner.clearAppendOnlyOnRustCompactionBoundary()
                                lastSeenRevision = event.revisionAfter
                                loggingPort.debug(
                                    TAG,
                                    "append_only cleared on destructive compaction revision=${event.revisionAfter}",
                                )
                            }
                            is AgentLoopEvent.PostCompact -> {
                                if (event.transcriptRevisionAfter > lastSeenRevision) {
                                    lastSeenRevision = event.transcriptRevisionAfter
                                }
                                send(
                                    InferenceEvent.PostCompact(
                                        trigger = event.trigger,
                                        summaryRef = event.summaryRef,
                                        historyRef = event.historyRef,
                                        modelType = modelType,
                                    ),
                                )
                                loggingPort.debug(
                                    TAG,
                                    "post_compact trigger=${event.trigger} historyRef=${event.historyRef} " +
                                        "transcriptRevisionAfter=${event.transcriptRevisionAfter}",
                                )
                            }
                            is AgentLoopEvent.Finished -> {
                                telemetryEventPort.emitInferenceExecutionOutcome(
                                    startedAtMs = startedAtMs,
                                    provider = provider,
                                    modelId = modelType.name,
                                    success = true,
                                )
                                options.userMessageId?.let { userMessageId ->
                                    committedInferencePrompts.commit(
                                        userMessageId = userMessageId,
                                        content = userContent,
                                    )
                                }
                                val completedTranscript =
                                    latestProviderTranscript.takeIf { it.isNotEmpty() }
                                        ?: buildFallbackTranscript(userContent, assistantText.toString())
                                options.chatId?.let { chatId ->
                                    committedInferenceTranscripts.commit(
                                        chatId = chatId,
                                        messages = completedTranscript,
                                    )
                                }
                                conversationHistory.clear()
                                conversationHistory.addAll(completedTranscript)
                                latestProviderTranscript = emptyList()
                                if (closeConversation) closeSession()
                                send(InferenceEvent.Finished(modelType))
                            }
                            is AgentLoopEvent.Failed -> {
                                telemetryEventPort.emitInferenceExecutionOutcome(
                                    startedAtMs = startedAtMs,
                                    provider = provider,
                                    modelId = modelType.name,
                                    success = false,
                                    errorMessage = event.message,
                                )
                                send(InferenceEvent.Error(Exception("Agent loop failed: ${event.message}"), modelType))
                            }
                        }
                    }
            } catch (e: CancellationException) {
                loggingPort.debug(TAG, "sendPrompt cancelled provider=$provider")
                send(InferenceEvent.Finished(modelType))
                throw e
            } catch (e: Exception) {
                loggingPort.error(TAG, "sendPrompt failed provider=$provider message=${e.message}", e)
                send(InferenceEvent.Error(e, modelType))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun completeSingleTurn(
        sessionMessages: List<AgentLoopMessage>,
        systemPrompt: String,
        contextSidecars: String?,
        availableTools: List<ToolDefinition>,
        allowToolCall: Boolean,
        onTextToken: suspend (String) -> Unit,
        onThinkingToken: suspend (String) -> Unit,
        options: GenerationOptions,
    ): AgentModelTurnResult =
        inner.completeSingleTurn(
            sessionMessages = sessionMessages,
            systemPrompt = systemPrompt,
            contextSidecars = contextSidecars,
            availableTools = availableTools,
            allowToolCall = allowToolCall,
            onTextToken = onTextToken,
            onThinkingToken = onThinkingToken,
            options = options,
        )

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
        latestProviderTranscript = emptyList()
        lastSeenRevision = 0L
        inner.setHistory(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
        latestProviderTranscript = emptyList()
        lastSeenRevision = 0L
        inner.closeSession()
    }

    private fun buildHistory(chatId: ChatId?): List<ChatMessage> =
        chatId
            ?.let(committedInferenceTranscripts::get)
            ?.takeIf { it.isNotEmpty() }
            ?: conversationHistory.toList()

    private fun buildProviderTranscript(
        sessionMessages: List<AgentLoopMessage>,
        result: AgentModelTurnResult,
    ): List<ChatMessage> =
        buildList {
            sessionMessages.forEach { message ->
                if (!message.isEngineInternalSidecar()) {
                    add(message.toChatMessage())
                }
            }
            result.toAssistantChatMessageOrNull()?.let(::add)
        }

    private fun AgentLoopMessage.isEngineInternalSidecar(): Boolean {
        val metadata = runCatching {
            if (metadataJson != "null" && metadataJson.isNotBlank()) {
                Json.parseToJsonElement(metadataJson).jsonObject
            } else {
                null
            }
        }.getOrNull()

        if (metadata != null) {
            if (metadata.containsKey("agent_tool_budget") ||
                metadata.containsKey("tools_blocked_nudge") ||
                metadata.containsKey("envelope_parse_failure")) {
                return true
            }
        }

        val contentObj = runCatching {
            Json.parseToJsonElement(content).jsonObject
        }.getOrNull()
        if (contentObj != null && contentObj.size == 1 && contentObj.containsKey("_agent")) {
            return true
        }

        return false
    }

    private fun buildFallbackTranscript(
        userContent: String,
        assistantContent: String,
    ): List<ChatMessage> =
        buildList {
            add(ChatMessage(Role.USER, userContent))
            assistantContent.takeIf { it.isNotBlank() }?.let { text ->
                add(ChatMessage(Role.ASSISTANT, text))
            }
        }

    private fun AgentModelTurnResult.toAssistantChatMessageOrNull(): ChatMessage? {
        val assistantContent = assistantText.orEmpty()
        if (assistantContent.isBlank() && toolCalls.isEmpty() && providerReasoningContent.isNullOrBlank()) {
            return null
        }
        return ChatMessage(
            role = Role.ASSISTANT,
            content = assistantContent,
            metadataJson = buildAssistantMetadataJson(this),
        )
    }

    private fun buildAssistantMetadataJson(result: AgentModelTurnResult): String {
        val hasProviderReasoning = !result.providerReasoningContent.isNullOrBlank()
        if (!hasProviderReasoning && result.toolCalls.isEmpty()) {
            return "null"
        }
        return buildJsonObject {
            result.providerReasoningContent?.takeIf { it.isNotBlank() }?.let { reasoningContent ->
                put(
                    "provider_reasoning",
                    buildJsonObject {
                        put("reasoning_content", reasoningContent)
                    },
                )
            }
            if (result.toolCalls.isNotEmpty()) {
                put(
                    "raw_tool_calls",
                    buildJsonArray {
                        result.toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    call.id?.let { put("id", it) }
                                    put("name", call.name)
                                    put("arguments_json", call.argumentsJson)
                                },
                            )
                        }
                    },
                )
            }
        }.toString()
    }

    private fun buildAgentLoopConfig(options: GenerationOptions): AgentLoopConfig =
        AgentLoopConfig(
            agent =
                AgentLoopAgentConfig(
                    maxModelRequestsPerTurn = 2u,
                    enableLoopHooks = true,
                    enablePendingWorkGuard = true,
                ),
            capabilities =
                when (provider) {
                    "LLAMA" ->
                        AgentLoopSessionCapabilities(
                            summarizationAllowed = false,
                            kvFixed = true,
                            contextPolicyTier = AgentLoopContextPolicyTier.FIXED_KV,
                            historyRewriteSupported = true,
                            lifecycleHooksSupported = true,
                        )
                    else ->
                        AgentLoopSessionCapabilities(
                            contextPolicyTier =
                                if ((options.contextWindow ?: DEFAULT_CONTEXT_WINDOW) >= HIGH_CONTEXT_WINDOW_THRESHOLD) {
                                    AgentLoopContextPolicyTier.HIGH_CONTEXT
                                } else {
                                    AgentLoopContextPolicyTier.HOSTED_MUTABLE
                                },
                            toolSchemaDeferralSupported = true,
                            fileBackedContextSupported = true,
                            promptCacheSupported = true,
                            lifecycleHooksSupported = true,
                        )
                },
            io =
                AgentLoopSessionIoParams(
                    provider = provider,
                    contextWindowTokens = (options.contextWindow ?: DEFAULT_CONTEXT_WINDOW).toUInt(),
                    toolsSchemaJson = serializeToolsSchema(options.activeTools),
                    toolCatalogJson =
                        com.browntowndev.enclave.domain.model.inference.AgentLoopToolCatalogJson
                            .jsonForCatalog(options.toolCatalog),
                    toolSchemaRegistryJson = serializeFullRegistry(options.toolCatalog),
                    systemPrompt = options.systemPrompt ?: "",
                ),
        )

    private fun serializeFullRegistry(catalog: List<com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry>): String {
        if (catalog.isEmpty()) return "[]"
        val array =
            buildJsonArray {
                catalog.forEach { entry ->
                    val def = ToolDefinition.fromName(entry.name)
                    add(
                        buildJsonObject {
                            put("name", entry.name)
                            put("description", entry.description)
                            if (def != null) {
                                put("parameters", def.schema.toJsonObject())
                            }
                        },
                    )
                }
            }
        return Json.encodeToString(
            kotlinx.serialization.json.JsonArray
                .serializer(),
            array,
        )
    }

    private fun serializeToolsSchema(tools: List<ToolDefinition>): String {
        if (tools.isEmpty()) return "[]"
        val array =
            buildJsonArray {
                tools.forEach { tool ->
                    add(
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                        },
                    )
                }
            }
        return Json.encodeToString(
            kotlinx.serialization.json.JsonArray
                .serializer(),
            array,
        )
    }

    /** Full eligible tool set for mapping Rust-emitted active names back to [ToolDefinition]. */
    private fun eligibleToolPool(options: GenerationOptions): List<ToolDefinition> {
        val byName = LinkedHashMap<String, ToolDefinition>()
        options.toolCatalog.forEach { entry ->
            ToolDefinition.fromName(entry.name)?.let { byName[it.name] = it }
        }
        options.activeTools.forEach { byName[it.name] = it }
        return byName.values.toList()
    }

    /**
     * Stable superset of tools for the provider wire payload. Rust may shrink the active
     * schema in [toolsSchemaJson]; deferral and activation are conveyed via sidecars instead
     * of mutating the tools array (prompt-cache prefix stability).
     */
    private fun wireToolsForRequest(
        options: GenerationOptions,
        toolContextIndexJson: String?,
    ): List<ToolDefinition> {
        if (toolContextIndexJson != null && toolNamesFromContextIndex(toolContextIndexJson) == null) {
            return emptyList()
        }
        return eligibleToolPool(options)
    }

    private fun toolNamesFromContextIndex(toolContextIndexJson: String): List<String>? =
        runCatching {
            Json
                .parseToJsonElement(toolContextIndexJson)
                .jsonObject["advertised_tools"]
                ?.jsonArray
                ?.mapNotNull { element -> element.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrNull()

    private suspend fun executeOneTool(
        call: AgentToolCall,
        options: GenerationOptions,
    ): AgentToolResult {
        if (call.name == ToolDefinition.DISCLOSE_TOOL_SCHEMAS.name) {
            return handleDisclosureCall(call, options)
        }
        if (call.name == ToolDefinition.READ_CONTEXT_REFERENCE.name) {
            return handleReadContextReference(call)
        }
        return try {
            val request =
                ToolCallRequest(
                    toolName = call.name,
                    argumentsJson = call.argumentsJson,
                    provider = provider,
                    modelType = modelType,
                    chatId = options.chatId,
                    userMessageId = options.userMessageId,
                    assistantMessageId = options.assistantMessageId,
                )
            loggingPort.info(TAG, "Executing tool call: name=${call.name} callId=${call.callId} args=${call.argumentsJson}")
            val result = toolExecutor.execute(request)
            loggingPort.info(TAG, "Tool call completed: name=${call.name} callId=${call.callId} result=${result.resultJson}")
            AgentToolResult(
                callId = call.callId,
                resultJson = result.resultJson,
                truncated = false,
                softFailure = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            loggingPort.error(TAG, "tool execution threw tool=${call.name} callId=${call.callId}", t)
            AgentToolResult(
                callId = call.callId,
                resultJson = errorJson(call, t),
                truncated = false,
                softFailure = true,
            )
        }
    }

    private fun handleDisclosureCall(
        call: AgentToolCall,
        options: GenerationOptions,
    ): AgentToolResult {
        val requestedNames =
            runCatching {
                val args = Json.parseToJsonElement(call.argumentsJson).jsonObject
                args["tool_names"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content } ?: emptyList()
            }.getOrDefault(emptyList())
        val effectiveActive = options.activeTools
        val activeNames = effectiveActive.map { it.name }.toSet()
        val deferredCatalog =
            options.toolCatalog.filter { entry ->
                entry.name !in activeNames
            }
        val resolution =
            disclosureResolver.resolve(
                toolNames = requestedNames,
                toolCatalog = deferredCatalog,
                activeTools = effectiveActive,
                strategy = options.toolCallStrategy,
            )
        return AgentToolResult(
            callId = call.callId,
            resultJson = resolution.resultJson,
            truncated = false,
            softFailure = false,
            activatedToolNames = resolution.activatedToolNames,
        )
    }

    private suspend fun handleReadContextReference(call: AgentToolCall): AgentToolResult {
        val referenceId =
            runCatching {
                Json
                    .parseToJsonElement(call.argumentsJson)
                    .jsonObject["reference_id"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()
                ?: return AgentToolResult(
                    callId = call.callId,
                    resultJson = """{"error":"missing_reference_id"}""",
                    truncated = false,
                    softFailure = true,
                )
        return try {
            val content = agentLoopPort.readContextReference(referenceId)
            AgentToolResult(
                callId = call.callId,
                resultJson = content,
                truncated = false,
                softFailure = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            loggingPort.error(TAG, "read_context_reference failed id=$referenceId", t)
            AgentToolResult(
                callId = call.callId,
                resultJson = errorJson(call, t),
                truncated = false,
                softFailure = true,
            )
        }
    }

    private fun buildSessionContext(options: GenerationOptions): Map<String, String> =
        buildMap {
            options.chatId?.value?.let { put("chat_id", it) }
            options.userMessageId?.value?.let { put("user_message_id", it) }
            options.assistantMessageId?.value?.let { put("assistant_message_id", it) }
            if (options.includedFiles.isNotEmpty()) {
                put(
                    "included_files",
                    buildJsonArray {
                        options.includedFiles.forEach { file ->
                            add(
                                buildJsonObject {
                                    put("path", file.path)
                                    put("token_estimate", file.tokenEstimate.toInt())
                                    put("content", file.content)
                                },
                            )
                        }
                    }.toString(),
                )
            }
        }

    private fun buildContextSidecars(
        contextReferences: List<AgentContextReference>,
        stateLedgerJson: String?,
        toolContextIndexJson: String?,
        fileContextIndexJson: String?,
        cacheHints: List<String>,
    ): String? {
        val compactLedger = stateLedgerJson?.let(ContextSidecarCompaction::compactStateLedgerJson)
        val compactToolIndex =
            toolContextIndexJson?.let(ContextSidecarCompaction::compactToolContextIndexJson)
        val compactFileIndex =
            fileContextIndexJson?.let(ContextSidecarCompaction::compactFileContextIndexJson)
        val jsonSidecars = listOfNotNull(compactLedger, compactToolIndex, compactFileIndex)
        if (contextReferences.isEmpty() && jsonSidecars.isEmpty() && cacheHints.isEmpty()) {
            return null
        }

        return buildString {
            appendLine("Context management sidecars:")
            if (contextReferences.isNotEmpty()) {
                appendLine("References:")
                contextReferences.forEach { reference ->
                    appendLine(
                        "- ${reference.kind} ${reference.id}: " +
                            "${reference.title}. ${reference.retrievalHint}",
                    )
                }
            }
            compactLedger?.let { appendLine("State ledger JSON: $it") }
            compactToolIndex?.let { appendLine("Tool context index JSON: $it") }
            compactFileIndex?.let { appendLine("File context index JSON: $it") }
            if (cacheHints.isNotEmpty()) appendLine("Cache hints: ${cacheHints.joinToString()}")
        }.trimEnd()
    }

    private fun buildUserContent(
        prompt: String,
        options: GenerationOptions,
    ): String =
        InferencePromptContentBuilder.buildUserContent(
            prompt = prompt,
            volatileTurnContext =
                if (deliverPreRetrievalAsOpenAiSidecar()) {
                    null
                } else {
                    options.volatileTurnContext
                },
        )

    private fun deliverPreRetrievalAsOpenAiSidecar(): Boolean =
        when (provider.uppercase()) {
            ApiProvider.OPENAI.name,
            ApiProvider.XAI.name,
            "OPENROUTER",
            -> true
            else -> false
        }

    private fun AgentLoopMessage.toChatMessage(): ChatMessage =
        ChatMessage(
            role = role,
            content = content,
            metadataJson = metadataJson,
        )

    private fun errorJson(
        call: AgentToolCall,
        throwable: Throwable,
    ): String =
        buildJsonObject {
            put("error", "tool_execution_failed")
            put("tool", call.name)
            put("exception", throwable::class.java.simpleName)
            put("message", throwable.message ?: "Unknown error")
        }.toString()

    private suspend fun emitToolSideEffects(
        toolName: String,
        argumentsJson: String,
        resultJson: String,
        options: GenerationOptions,
        emit: suspend (InferenceEvent) -> Unit,
    ) {
        if (toolName == ToolDefinition.WEB_SEARCH.name || toolName == ToolDefinition.X_SEARCH.name) {
            val assistantMessageId = options.assistantMessageId ?: return
            val sources = WebSearchResultParser.parse(assistantMessageId, resultJson)
            if (sources.isNotEmpty()) {
                emit(InferenceEvent.WebSearchResults(sources, modelType))
            }
        }

        if (toolName == ToolDefinition.APPEND_BLOCKS.name) {
            AppendBlocksToolResultParser.parseCompleteArtifact(resultJson)?.let { complete ->
                val artifactId =
                    runCatching {
                        val args = Json.parseToJsonElement(argumentsJson).jsonObject
                        args["artifactId"]?.jsonPrimitive?.content
                            ?: args["artifact_id"]?.jsonPrimitive?.content
                    }.getOrNull() ?: return@let
                emit(
                    InferenceEvent.Artifacts(
                        artifacts =
                            listOf(
                                ArtifactGenerationRequest(
                                    artifactId = artifactId,
                                    title = complete.title,
                                    content = complete.documentJson,
                                ),
                            ),
                        modelType = modelType,
                    ),
                )
            }
        }

        if (toolName == ToolDefinition.GENERATE_IMAGE.name ||
            toolName == ToolDefinition.GENERATE_VIDEO.name ||
            toolName == ToolDefinition.GENERATE_MUSIC.name
        ) {
            val mediaResult = MediaResultParser.parse(resultJson)
            if (mediaResult.uris.isNotEmpty()) {
                emit(
                    InferenceEvent.Media(
                        mediaUris = mediaResult.uris,
                        mediaIds = mediaResult.ids,
                        mediaTypes = mediaResult.types,
                        prompts = mediaResult.prompts,
                        modelType = modelType,
                    ),
                )
            }
        }
    }
}
