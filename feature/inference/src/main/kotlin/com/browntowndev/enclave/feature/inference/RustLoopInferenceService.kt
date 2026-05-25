package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
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
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.InferenceFactoryPort
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.usecase.chat.ToolSchemaDisclosureResolver
import com.browntowndev.enclave.domain.usecase.inference.ResolveCompactionSummaryModelUseCase
import com.browntowndev.enclave.domain.util.AppendBlocksToolResultParser
import com.browntowndev.enclave.domain.util.MediaResultParser
import com.browntowndev.enclave.domain.util.WebSearchResultParser
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
) : RustCompatibleInferenceService {
    private val conversationHistory = mutableListOf<ChatMessage>()
    private val disclosureResolver = ToolSchemaDisclosureResolver()

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
            try {
                val history = buildHistory()
                val initialMessages = history + listOf(ChatMessage(Role.USER, prompt))
                val config = buildAgentLoopConfig(options)
                val assistantText = StringBuilder()

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
                            onTextToken: suspend (String) -> Unit,
                            onThinkingToken: suspend (String) -> Unit,
                        ): AgentModelTurnResult =
                            inner.completeSingleTurn(
                                sessionMessages = sessionMessages,
                                systemPrompt = systemPrompt,
                                contextSidecars = buildContextSidecars(
                                    contextReferences = contextReferences,
                                    stateLedgerJson = stateLedgerJson,
                                    toolContextIndexJson = toolContextIndexJson,
                                    fileContextIndexJson = fileContextIndexJson,
                                    cacheHints = cacheHints,
                                ),
                                // Keep the full tool list in the request payload for cache stability;
                                // outer allowToolCall still drives provider toolChoice/toolConfig NONE.
                                availableTools =
                                    toolsForRequest(
                                        toolsSchemaJson = toolsSchemaJson,
                                        toolContextIndexJson = toolContextIndexJson,
                                        eligibleTools = eligibleToolPool(options),
                                        allowToolCall = true,
                                    ),
                                allowToolCall = allowToolCall,
                                onTextToken = onTextToken,
                                onThinkingToken = onThinkingToken,
                                options = options,
                            )

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
                            is AgentLoopEvent.PostCompact ->
                                send(
                                    InferenceEvent.PostCompact(
                                        trigger = event.trigger,
                                        summaryRef = event.summaryRef,
                                        historyRef = event.historyRef,
                                        modelType = modelType,
                                    ),
                                ).also {
                                    loggingPort.debug(TAG, "post_compact trigger=${event.trigger} historyRef=${event.historyRef}")
                                }
                            is AgentLoopEvent.Finished -> {
                                conversationHistory.add(ChatMessage(Role.USER, prompt))
                                assistantText.toString().takeIf { it.isNotBlank() }?.let { text ->
                                    conversationHistory.add(ChatMessage(Role.ASSISTANT, text))
                                }
                                if (closeConversation) closeSession()
                                send(InferenceEvent.Finished(modelType))
                            }
                            is AgentLoopEvent.Failed ->
                                send(InferenceEvent.Error(Exception("Agent loop failed: ${event.message}"), modelType))
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
        inner.setHistory(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
        inner.closeSession()
    }

    private fun buildHistory(): List<ChatMessage> = conversationHistory

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
                    toolCatalogJson = serializeToolCatalog(options.toolCatalog),
                    toolSchemaRegistryJson = serializeFullRegistry(options.toolCatalog),
                    systemPrompt = options.systemPrompt ?: "",
                ),
        )

    private fun serializeToolCatalog(
        catalog: List<com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry>,
    ): String {
        if (catalog.isEmpty()) return "[]"
        val array =
            buildJsonArray {
                catalog.forEach { entry ->
                    add(
                        buildJsonObject {
                            put("name", entry.name)
                            put("description", entry.description)
                            put("disclosureMode", entry.disclosureMode.name)
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

    private fun serializeFullRegistry(
        catalog: List<com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry>,
    ): String {
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

    private fun toolsForRequest(
        toolsSchemaJson: String,
        toolContextIndexJson: String?,
        eligibleTools: List<ToolDefinition>,
        allowToolCall: Boolean,
    ): List<ToolDefinition> {
        if (!allowToolCall) return emptyList()
        val allowedNames =
            toolContextIndexJson?.let(::toolNamesFromContextIndex)
                ?: toolNamesFromSchema(toolsSchemaJson)
        return when {
            allowedNames == null -> eligibleTools
            allowedNames.isEmpty() -> emptyList()
            else -> {
                // Preserve the order in which Rust supplied the active tool names.
                val byName = eligibleTools.associateBy { it.name }
                val unknownNames = allowedNames.filter { name -> name !in byName }
                if (unknownNames.isNotEmpty()) {
                    loggingPort.warning(
                        TAG,
                        "Rust active tool names not in eligible pool: $unknownNames",
                    )
                }
                allowedNames.mapNotNull { name -> byName[name] }
            }
        }
    }

    private fun toolNamesFromSchema(toolsSchemaJson: String): List<String>? {
        if (toolsSchemaJson.isBlank()) return null
        return runCatching {
            Json
                .parseToJsonElement(toolsSchemaJson)
                .jsonArray
                .mapNotNull { element -> element.jsonObject["name"]?.jsonPrimitive?.content }
        }.getOrNull()
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
            val result = toolExecutor.execute(request)
            AgentToolResult(
                callId = call.callId,
                resultJson = result.resultJson,
                truncated = false,
                softFailure = false,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            loggingPort.error(TAG, "tool execution threw tool=${call.name}", t)
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
        val jsonSidecars = listOfNotNull(stateLedgerJson, toolContextIndexJson, fileContextIndexJson)
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
            stateLedgerJson?.let { appendLine("State ledger JSON: $it") }
            toolContextIndexJson?.let { appendLine("Tool context index JSON: $it") }
            fileContextIndexJson?.let { appendLine("File context index JSON: $it") }
            if (cacheHints.isNotEmpty()) appendLine("Cache hints: ${cacheHints.joinToString()}")
        }.trimEnd()
    }


    private fun AgentLoopMessage.toChatMessage(): ChatMessage = ChatMessage(role = role, content = content)

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
