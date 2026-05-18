@file:Suppress("MaxLineLength")

package com.browntowndev.pocketcrew.core.agent

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentContextReference
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopConfig
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopCompactionTrigger
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopContextPolicyTier
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentModelTurnResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import com.browntowndev.pocketcrew.domain.model.inference.AgentSummaryRequest
import com.browntowndev.pocketcrew.domain.model.inference.AgentSummaryResult
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolCall
import com.browntowndev.pocketcrew.domain.model.inference.AgentToolResult
import com.browntowndev.pocketcrew.domain.port.inference.AgentManualCompactRequest
import com.browntowndev.pocketcrew.domain.port.inference.AgentManualCompactResult
import com.browntowndev.pocketcrew.domain.model.inference.toAgentLoopMessages
import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopEvent
import com.browntowndev.pocketcrew.domain.port.inference.AgentLoopPort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    constructor(private val loggingPort: LoggingPort) : AgentLoopPort {
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

                while (true) {
                    for (ev in engine.drainEvents(session)) {
                        AgentEventMapper.log(ev, loggingPort)
                        AgentEventMapper.toDomain(ev)?.let { send(it) }
                    }
                    when (val action = engine.nextAction(session)) {
                        is FfiAgentAction.CallModel -> {
                            val resp =
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
                                        onTextToken = { text -> channel.send(AgentLoopEvent.TextToken(text)) },
                                        onThinkingToken = { text -> channel.send(AgentLoopEvent.ThinkingToken(text)) },
                                    )
                                }
                            engine.submitModelResponse(session, resp.toFfi())
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
                            activeEngine = null
                            activeSession = null
                            activeHost = null
                            send(AgentLoopEvent.Finished)
                            return@channelFlow
                        }
                        is FfiAgentAction.Failed -> {
                            drainEvents(engine, session)
                            activeEngine = null
                            activeSession = null
                            activeHost = null
                            send(AgentLoopEvent.Failed(action.error.message ?: "agent failed"))
                            return@channelFlow
                        }
                        is FfiAgentAction.ManualCompactCompleted -> {
                            drainEvents(engine, session)
                        }
                    }
                }
            }

        override suspend fun requestManualCompact(request: AgentManualCompactRequest): AgentManualCompactResult {
            val engine = requireNotNull(activeEngine) { "No active Rust agent session" }
            val session = requireNotNull(activeSession) { "No active Rust agent session" }
            val action = engine.requestManualCompact(
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
                    val summary = host.summarize(
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

        override suspend fun searchContextReferences(query: String, limit: Int): List<AgentContextReference> {
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
            val obj = buildJsonObject {
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

private fun com.browntowndev.pocketcrew.domain.model.inference.AgentLoopAgentConfig.toFfi(): FfiAgentConfig =
    FfiAgentConfig(
        maxTurns = maxTurns,
        thresholdRatio = thresholdRatio,
        microKeepRecent = microKeepRecent,
        toolRetryLimit = toolRetryLimit,
        greedyAppendBlocks = greedyAppendBlocks,
        toolResultBudgetCloudBytes = toolResultBudgetCloudBytes,
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
    )

private fun com.browntowndev.pocketcrew.domain.model.inference.AgentLoopSessionCapabilities.toFfi(): FfiSessionCapabilities =
    FfiSessionCapabilities(
        summarizationAllowed = summarizationAllowed,
        kvFixed = kvFixed,
        toolResultBudgetTokens = toolResultBudgetTokens,
        contextPolicyTier = contextPolicyTier.toFfi(),
        toolSchemaDeferralSupported = toolSchemaDeferralSupported,
        fileBackedContextSupported = fileBackedContextSupported,
        promptCacheSupported = promptCacheSupported,
        historyRewriteSupported = historyRewriteSupported,
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

private fun com.browntowndev.pocketcrew.domain.model.inference.AgentLoopSessionIoParams.toFfi(): FfiSessionIoParams =
    FfiSessionIoParams(
        provider = provider,
        contextWindowTokens = contextWindowTokens,
        toolsSchemaJson = toolsSchemaJson,
        systemPrompt = systemPrompt,
    )

private fun AgentModelTurnResult.toFfi(): FfiModelResponse =
    FfiModelResponse(
        assistantText = assistantText,
        toolCalls = toolCalls.map { it.toFfi() },
        finishReason = finishReason.toFfi(),
    )

private fun AgentRawToolCall.toFfi(): FfiRawToolCall =
    FfiRawToolCall(
        id = id,
        name = name,
        argumentsJson = argumentsJson,
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
    )

private fun AgentSummaryResult.toFfi(): FfiSummaryResult =
    FfiSummaryResult(
        summaryText = summaryText,
        lastMessageId = lastMessageId,
    )
