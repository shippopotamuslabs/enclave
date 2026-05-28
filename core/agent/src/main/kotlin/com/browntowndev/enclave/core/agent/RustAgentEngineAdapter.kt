@file:Suppress("MaxLineLength")

package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentContextReference
import com.browntowndev.enclave.domain.model.inference.AgentFinishReason
import com.browntowndev.enclave.domain.model.inference.AgentLoopCompactionTrigger
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopContextPolicyTier
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.AgentRawToolCall
import com.browntowndev.enclave.domain.model.inference.AgentSummaryRequest
import com.browntowndev.enclave.domain.model.inference.AgentSummaryResult
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import com.browntowndev.enclave.domain.model.inference.toAgentLoopMessages
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.AgentManualCompactRequest
import com.browntowndev.enclave.domain.port.inference.AgentManualCompactResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the UniFFI [FfiAgentEngine] using domain [AgentLoopHostCallbacks].
 *
 * The Rust engine uses a **submit-style** loop: it returns [FfiAgentAction] values and never
 * invokes the UniFFI transport or tool-executor callbacks (those exist for alternate embeddings).
 * Model and tool work run in [Dispatchers.IO] through suspending host callbacks.
 */
@Singleton
class RustAgentEngineAdapter
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val toolStreamGatePort: ToolStreamGatePort,
    ) : AgentLoopPort {
        companion object {
            private const val TAG = "RustAgentEngineAdapter"
        }

        private var activeEngine: FfiAgentEngine? = null
        private var activeSession: FfiSession? = null
        private var activeHost: AgentLoopHostCallbacks? = null

        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            channelFlow {
                val unusedTransport =
                    object : AgentTransportCallback {
                        override fun completeModelTurn(request: FfiModelRequest): FfiModelResponse {
                            error(
                                "UniFFI transport callback must not be used in submit-style mode " +
                                    "(got request with ${request.sessionMessages.size} messages)",
                            )
                        }
                    }
                val unusedExecutor =
                    object : AgentToolExecutorCallback {
                        override fun executeTools(calls: List<FfiToolCallRecord>): List<FfiToolResultRecord> {
                            error(
                                "UniFFI executor callback must not be used in submit-style mode " +
                                    "(got ${calls.size} calls)",
                            )
                        }
                    }

                val engine = createAgentEngine(unusedTransport, unusedExecutor, summarizer = null)
                val messages = initialMessages.toAgentLoopMessages()
                val session =
                    engine.createSession(
                        initialMessages = messages.map { it.toFfi() },
                        config = config.agent.toFfi(),
                        capabilities = config.capabilities.toFfi(),
                        sessionContextJson = sessionContextJson(sessionContext),
                        io = config.io.toFfi(),
                    )
                activeEngine = engine
                activeSession = session
                activeHost = host

                try {
                    while (true) {
                        for (ev in engine.drainEvents(session)) {
                            AgentEventMapper.log(ev, loggingPort)
                            AgentEventMapper.toDomain(ev)?.let { send(it) }
                        }
                        when (val action = engine.nextAction(session)) {
                            is FfiAgentAction.CallModel -> {
                                val textGate =
                                    ModelTurnStreamGate(
                                        toolStreamGatePort,
                                        action.request.allowToolCall,
                                    )
                                val resp =
                                    try {
                                        withContext(Dispatchers.IO) {
                                            host.completeModelTurn(
                                                sessionMessages =
                                                    action.request.sessionMessages.map { it.toDomain() },
                                                systemPrompt = action.request.systemPrompt,
                                                toolsSchemaJson = action.request.toolsSchemaJson,
                                                allowToolCall = action.request.allowToolCall,
                                                contextReferences = action.request.contextReferences.map { it.toDomain() },
                                                stateLedgerJson = action.request.stateLedgerJson,
                                                toolContextIndexJson = action.request.toolContextIndexJson,
                                                fileContextIndexJson = action.request.fileContextIndexJson,
                                                cacheHints = action.request.cacheHints,
                                                transcriptRevision =
                                                    action.request.providerContext.transcriptRevision
                                                        .toLong(),
                                                onTextToken = { text ->
                                                    textGate.onProviderTextChunk(text) { emitted ->
                                                        channel.send(AgentLoopEvent.TextToken(emitted))
                                                    }
                                                },
                                                onThinkingToken = { text ->
                                                    channel.send(AgentLoopEvent.ThinkingToken(text))
                                                },
                                            )
                                        }
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Throwable) {
                                        loggingPort.error(TAG, "model turn failed: ${e.message}", e)
                                        send(AgentLoopEvent.Failed("model turn failed: ${e.message}"))
                                        return@channelFlow
                                    }
                                val bufferedText =
                                    textGate.finish(resp.toolCalls.isNotEmpty()) { trailing ->
                                        if (trailing.isNotEmpty()) {
                                            channel.send(AgentLoopEvent.TextToken(trailing))
                                        }
                                    }
                                engine.submitModelResponse(
                                    session,
                                    resp.copy(assistantText = bufferedText.takeIf { it.isNotBlank() }).toFfi(loggingPort),
                                )
                            }
                            is FfiAgentAction.ExecuteTools -> {
                                val domainCalls =
                                    action.calls.map { c ->
                                        AgentToolCall(
                                            callId = c.callId,
                                            name = c.name,
                                            argumentsJson = c.argumentsJson,
                                        )
                                    }
                                val results =
                                    withContext(Dispatchers.IO) {
                                        host.executeTools(domainCalls)
                                    }
                                domainCalls.zip(results).forEach { (call, result) ->
                                    host.onToolResult(call.name, call.argumentsJson, result.resultJson)
                                    send(
                                        AgentLoopEvent.ToolCallExecuted(
                                            toolName = call.name,
                                            resultJson = result.resultJson,
                                        ),
                                    )
                                }
                                engine.submitToolResults(session, results.map { it.toFfi() })
                            }
                            is FfiAgentAction.RequestSummary -> {
                                val summary =
                                    withContext(Dispatchers.IO) {
                                        host.summarize(
                                            AgentSummaryRequest(
                                                messagesToSummarize =
                                                    action.request.messagesToSummarize.map { it.toDomain() },
                                                instruction = action.request.instruction,
                                                lastMessageId = action.request.lastMessageId,
                                                trigger = action.request.trigger.toDomain(),
                                                focusInstruction = action.request.focusInstruction,
                                                historyRef = action.request.historyRef,
                                                stateLedgerSnapshotJson = action.request.stateLedgerSnapshotJson,
                                            ),
                                        )
                                    }
                                engine.submitSummary(session, summary.toFfi())
                            }
                            is FfiAgentAction.Finished -> {
                                drainEvents(engine, session)
                                send(AgentLoopEvent.Finished)
                                return@channelFlow
                            }
                            is FfiAgentAction.Failed -> {
                                drainEvents(engine, session)
                                send(AgentLoopEvent.Failed(action.error.message ?: "agent failed"))
                                return@channelFlow
                            }
                            is FfiAgentAction.ManualCompactCompleted -> {
                                drainEvents(engine, session)
                            }
                        }
                    }
                } finally {
                    activeEngine = null
                    activeSession = null
                    activeHost = null
                }
            }

        override suspend fun requestManualCompact(request: AgentManualCompactRequest): AgentManualCompactResult {
            val engine = requireNotNull(activeEngine) { "No active Rust agent session" }
            val session = requireNotNull(activeSession) { "No active Rust agent session" }
            val action =
                engine.requestManualCompact(
                    session,
                    FfiManualCompactRequest(
                        focusInstruction = request.focusInstruction,
                        forceSummary = request.forceSummary,
                    ),
                )
            val requestEvents = collectDomainEvents(engine, session)
            return when (action) {
                is FfiAgentAction.ManualCompactCompleted -> action.result.toDomain(requestEvents)
                is FfiAgentAction.RequestSummary -> {
                    val host = requireNotNull(activeHost) { "No active Rust agent host" }
                    val summary =
                        host.summarize(
                            AgentSummaryRequest(
                                messagesToSummarize = action.request.messagesToSummarize.map { it.toDomain() },
                                instruction = action.request.instruction,
                                lastMessageId = action.request.lastMessageId,
                                trigger = action.request.trigger.toDomain(),
                                focusInstruction = action.request.focusInstruction,
                                historyRef = action.request.historyRef,
                                stateLedgerSnapshotJson = action.request.stateLedgerSnapshotJson,
                            ),
                        )
                    engine.submitSummary(session, summary.toFfi())
                    val summaryEvents = collectDomainEvents(engine, session)
                    val events = requestEvents + summaryEvents
                    val compaction = events.filterIsInstance<AgentLoopEvent.CompactionApplied>().lastOrNull()
                    val postCompact = events.filterIsInstance<AgentLoopEvent.PostCompact>().lastOrNull()
                    requireNotNull(compaction) {
                        "Manual compact summary completed without compaction metadata"
                    }.let { applied ->
                        AgentManualCompactResult(
                            summaryRef = postCompact?.summaryRef,
                            historyRef = postCompact?.historyRef ?: action.request.historyRef,
                            tokensBefore = applied.tokensBefore,
                            tokensAfter = applied.tokensAfter,
                            events = events,
                        )
                    }
                }
                else -> error("Unexpected manual compact action: ${action::class.simpleName}")
            }
        }

        override suspend fun readContextReference(referenceId: String): String {
            val engine = requireNotNull(activeEngine) { "No active Rust agent session" }
            val session = requireNotNull(activeSession) { "No active Rust agent session" }
            return engine.readContextReference(session, referenceId)
        }

        override suspend fun searchContextReferences(
            query: String,
            limit: Int,
        ): List<AgentContextReference> {
            val engine = requireNotNull(activeEngine) { "No active Rust agent session" }
            val session = requireNotNull(activeSession) { "No active Rust agent session" }
            return engine.searchContextReferences(session, query, limit.toUInt()).map { it.toDomain() }
        }

        private suspend fun kotlinx.coroutines.channels.ProducerScope<AgentLoopEvent>.drainEvents(
            engine: FfiAgentEngine,
            session: FfiSession,
        ) {
            collectDomainEvents(engine, session).forEach { send(it) }
        }

        private fun collectDomainEvents(
            engine: FfiAgentEngine,
            session: FfiSession,
        ): List<AgentLoopEvent> =
            engine.drainEvents(session).mapNotNull { event ->
                AgentEventMapper.log(event, loggingPort)
                AgentEventMapper.toDomain(event)
            }

        private fun sessionContextJson(map: Map<String, String>): String {
            val obj =
                buildJsonObject {
                    map.forEach { (key, value) ->
                        if (key == "included_files") {
                            runCatching { Json.parseToJsonElement(value) }
                                .getOrNull()
                                ?.let { put(key, it) }
                                ?: put(key, value)
                        } else {
                            put(key, value)
                        }
                    }
                }
            return obj.toString()
        }
    }

private fun Role.toFfi(): FfiAgentRole =
    when (this) {
        Role.USER -> FfiAgentRole.USER
        Role.ASSISTANT -> FfiAgentRole.ASSISTANT
        Role.SYSTEM -> FfiAgentRole.SYSTEM
    }

private fun FfiAgentRole.toDomain(): Role =
    when (this) {
        FfiAgentRole.USER -> Role.USER
        FfiAgentRole.ASSISTANT -> Role.ASSISTANT
        FfiAgentRole.SYSTEM -> Role.SYSTEM
        FfiAgentRole.TOOL,
        FfiAgentRole.COMPACT_SUMMARY,
        -> Role.ASSISTANT
    }

private fun AgentLoopMessage.toFfi(): FfiAgentMessage =
    FfiAgentMessage(
        id = id,
        role = role.toFfi(),
        content = content,
        metadataJson = metadataJson,
    )

private fun FfiAgentMessage.toDomain(): AgentLoopMessage =
    AgentLoopMessage(
        id = id,
        role = role.toDomain(),
        content = content,
        metadataJson = metadataJson,
    )

private fun FfiContextReference.toDomain(): AgentContextReference =
    AgentContextReference(
        id = id,
        kind = kind.name,
        title = title,
        byteLen = byteLen,
        tokenEstimate = tokenEstimate,
        preview = preview,
        retrievalHint = retrievalHint,
    )

private fun FfiManualCompactResult.toDomain(events: List<AgentLoopEvent> = emptyList()): AgentManualCompactResult =
    AgentManualCompactResult(
        summaryRef = summaryRef,
        historyRef = historyRef,
        tokensBefore = tokensBefore.toInt(),
        tokensAfter = tokensAfter.toInt(),
        events = events,
    )

internal fun com.browntowndev.enclave.domain.model.inference.AgentLoopAgentConfig.toFfi(): FfiAgentConfig =
    FfiAgentConfig(
        maxTurns = maxTurns,
        thresholdRatio = thresholdRatio,
        microKeepRecent = microKeepRecent,
        toolRetryLimit = toolRetryLimit,
        toolSemanticsJson = toolSemanticsJson,
        toolResultBudgetCloudBytes = toolResultBudgetCloudBytes,
        toolResultBudgetRelaxedBytes = toolResultBudgetRelaxedBytes,
        toolResultTruncationPressureRatio = toolResultTruncationPressureRatio,
        midTurnThresholdRatio = midTurnThresholdRatio,
        postToolResultThresholdRatio = postToolResultThresholdRatio,
        manualCompactThresholdRatio = manualCompactThresholdRatio,
        allowFifoOnPreflight = allowFifoOnPreflight,
        allowFifoOnMidTurn = allowFifoOnMidTurn,
        allowFifoOnPostToolResult = allowFifoOnPostToolResult,
        requireSnapshotBeforeFifo = requireSnapshotBeforeFifo,
        enableContextStore = enableContextStore,
        enableStateLedger = enableStateLedger,
        enableToolSchemaDeferral = enableToolSchemaDeferral,
        enableFileCondensation = enableFileCondensation,
        enableManualCompact = enableManualCompact,
        toolSpillThresholdBytes = toolSpillThresholdBytes,
        terminalSpillThresholdBytes = terminalSpillThresholdBytes,
        historySnapshotThresholdTokens = historySnapshotThresholdTokens,
        fileOutlineThresholdTokens = fileOutlineThresholdTokens,
        promptCacheAnchorThresholdTokens = promptCacheAnchorThresholdTokens,
        outputReserveRatio = outputReserveRatio,
        outputReserveFloorTokens = outputReserveFloorTokens,
        outputReserveMaxTokens = outputReserveMaxTokens,
        preflightTokenThreshold = preflightTokenThreshold,
        midTurnTokenThreshold = midTurnTokenThreshold,
        postToolResultTokenThreshold = postToolResultTokenThreshold,
        messageThreshold = messageThreshold,
        retentionWindowTokens = retentionWindowTokens,
        maxModelRequestsPerTurn = maxModelRequestsPerTurn,
        enableLoopHooks = enableLoopHooks,
        enablePendingWorkGuard = enablePendingWorkGuard,
        longLoop = longLoop.toFfi(),
    )

private fun com.browntowndev.enclave.domain.model.inference.AgentLoopLongLoopPolicy.toFfi(): FfiLongLoopPolicy =
    FfiLongLoopPolicy(
        maxToolTurns = maxToolTurns,
        sidecarByteBudgetPerCall = sidecarByteBudgetPerCall,
        sidecarTotalByteBudget = sidecarTotalByteBudget,
        summarizeAfterToolTurns = summarizeAfterToolTurns,
        compactEveryNToolTurns = compactEveryNToolTurns,
    )

private fun com.browntowndev.enclave.domain.model.inference.AgentLoopSessionCapabilities.toFfi(): FfiSessionCapabilities =
    FfiSessionCapabilities(
        summarizationAllowed = summarizationAllowed,
        kvFixed = kvFixed,
        toolResultBudgetTokens = toolResultBudgetTokens,
        contextPolicyTier = contextPolicyTier.toFfi(),
        toolSchemaDeferralSupported = toolSchemaDeferralSupported,
        fileBackedContextSupported = fileBackedContextSupported,
        promptCacheSupported = promptCacheSupported,
        historyRewriteSupported = historyRewriteSupported,
        lifecycleHooksSupported = lifecycleHooksSupported,
    )

private fun AgentLoopContextPolicyTier.toFfi(): FfiContextPolicyTier =
    when (this) {
        AgentLoopContextPolicyTier.HOSTED_MUTABLE -> FfiContextPolicyTier.HOSTED_MUTABLE
        AgentLoopContextPolicyTier.HOSTED_PROXY -> FfiContextPolicyTier.HOSTED_PROXY
        AgentLoopContextPolicyTier.LOCAL_MUTABLE -> FfiContextPolicyTier.LOCAL_MUTABLE
        AgentLoopContextPolicyTier.FIXED_KV -> FfiContextPolicyTier.FIXED_KV
        AgentLoopContextPolicyTier.HIGH_CONTEXT -> FfiContextPolicyTier.HIGH_CONTEXT
        AgentLoopContextPolicyTier.SMALL_CONTEXT -> FfiContextPolicyTier.SMALL_CONTEXT
    }

private fun FfiCompactionTrigger.toDomain(): AgentLoopCompactionTrigger =
    when (this) {
        FfiCompactionTrigger.PREFLIGHT -> AgentLoopCompactionTrigger.PREFLIGHT
        FfiCompactionTrigger.MID_TURN -> AgentLoopCompactionTrigger.MID_TURN
        FfiCompactionTrigger.POST_TOOL_RESULT -> AgentLoopCompactionTrigger.POST_TOOL_RESULT
        FfiCompactionTrigger.MANUAL -> AgentLoopCompactionTrigger.MANUAL
    }

private fun com.browntowndev.enclave.domain.model.inference.AgentLoopSessionIoParams.toFfi(): FfiSessionIoParams =
    FfiSessionIoParams(
        provider = provider,
        contextWindowTokens = contextWindowTokens,
        toolCatalogJson = toolCatalogJson,
        toolSchemaRegistryJson = toolSchemaRegistryJson,
        toolsSchemaJson = toolsSchemaJson,
        systemPrompt = systemPrompt,
    )

private fun AgentModelTurnResult.toFfi(loggingPort: LoggingPort? = null): FfiModelResponse =
    FfiModelResponse(
        assistantText = FfiPayloadGuard.capAssistantText(assistantText, loggingPort),
        toolCalls = toolCalls.map { it.toFfi(loggingPort) },
        finishReason = finishReason.toFfi(),
        providerReasoningContent = providerReasoningContent,
    )

private fun AgentRawToolCall.toFfi(loggingPort: LoggingPort? = null): FfiRawToolCall =
    FfiRawToolCall(
        id = id,
        name = name,
        argumentsJson = FfiPayloadGuard.capToolArgs(argumentsJson, name, loggingPort),
    )

private fun AgentFinishReason.toFfi(): FfiFinishReason =
    when (this) {
        AgentFinishReason.ToolUse -> FfiFinishReason.ToolUse
        AgentFinishReason.Stop -> FfiFinishReason.Stop
        AgentFinishReason.MaxTokens -> FfiFinishReason.MaxTokens
        is AgentFinishReason.Other -> FfiFinishReason.Other(reason)
    }

private fun AgentToolResult.toFfi(): FfiToolResultRecord =
    FfiToolResultRecord(
        callId = callId,
        resultJson = resultJson,
        truncated = truncated,
        softFailure = softFailure,
        activatedToolNames = activatedToolNames,
    )

private fun AgentSummaryResult.toFfi(): FfiSummaryResult =
    FfiSummaryResult(
        summaryText = summaryText,
        lastMessageId = lastMessageId,
    )
