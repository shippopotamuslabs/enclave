package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopAgentConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentToolCall
import com.browntowndev.enclave.domain.model.inference.toAgentLoopMessages
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

data class AgentEngineRuntimeConfig(
    val toolResultBudgetRelaxedBytes: ULong,
    val preflightTokenThreshold: UInt?,
    val retentionWindowTokens: UInt,
    val maxModelRequestsPerTurn: UInt,
    val enableLoopHooks: Boolean,
    val enablePendingWorkGuard: Boolean,
)

class AgentEngineActionDriver {
    fun mapConfigToFfi(config: AgentLoopAgentConfig): AgentEngineRuntimeConfig =
        AgentEngineRuntimeConfig(
            toolResultBudgetRelaxedBytes = config.toolResultBudgetRelaxedBytes,
            preflightTokenThreshold = config.preflightTokenThreshold,
            retentionWindowTokens = config.retentionWindowTokens,
            maxModelRequestsPerTurn = config.maxModelRequestsPerTurn,
            enableLoopHooks = config.enableLoopHooks,
            enablePendingWorkGuard = config.enablePendingWorkGuard,
        )

    fun execute(
        initialMessages: List<ChatMessage>,
        actions: List<FfiAgentAction>,
        host: AgentLoopHostCallbacks,
    ): Flow<AgentLoopEvent> =
        flow {
            val loopMessages = initialMessages.toAgentLoopMessages()
            for (action in actions) {
                when (action) {
                    is FfiAgentAction.CallModel -> {
                        host.completeModelTurn(
                            sessionMessages =
                                action.request.sessionMessages
                                    .map { it.toDomainMessage() }
                                    .ifEmpty { loopMessages },
                            systemPrompt = action.request.systemPrompt,
                            toolsSchemaJson = action.request.toolsSchemaJson,
                            allowToolCall = action.request.allowToolCall,
                            contextReferences = emptyList(),
                            stateLedgerJson = action.request.stateLedgerJson,
                            toolContextIndexJson = action.request.toolContextIndexJson,
                            fileContextIndexJson = action.request.fileContextIndexJson,
                            cacheHints = action.request.cacheHints,
                            onTextToken = { emit(AgentLoopEvent.TextToken(it)) },
                            onThinkingToken = { emit(AgentLoopEvent.ThinkingToken(it)) },
                        )
                    }
                    is FfiAgentAction.ExecuteTools -> {
                        val calls =
                            action.calls.map {
                                AgentToolCall(
                                    callId = it.callId,
                                    name = it.name,
                                    argumentsJson = it.argumentsJson,
                                )
                            }
                        val results = host.executeTools(calls)
                        calls.zip(results).forEach { (call, result) ->
                            emit(AgentLoopEvent.ToolCallExecuted(call.name, result.resultJson))
                        }
                    }
                    is FfiAgentAction.Finished -> {
                        emit(AgentLoopEvent.Finished)
                        return@flow
                    }
                    is FfiAgentAction.Failed -> {
                        emit(AgentLoopEvent.Failed(action.error.toDomainMessage()))
                        return@flow
                    }
                    is FfiAgentAction.ManualCompactCompleted,
                    is FfiAgentAction.RequestSummary,
                    -> Unit
                }
            }
        }
}

private fun FfiAgentMessage.toDomainMessage() =
    com.browntowndev.enclave.domain.model.inference.AgentLoopMessage(
        id = id,
        role =
            when (role) {
                FfiAgentRole.USER -> com.browntowndev.enclave.domain.model.chat.Role.USER
                FfiAgentRole.SYSTEM -> com.browntowndev.enclave.domain.model.chat.Role.SYSTEM
                FfiAgentRole.ASSISTANT,
                FfiAgentRole.TOOL,
                FfiAgentRole.COMPACT_SUMMARY,
                -> com.browntowndev.enclave.domain.model.chat.Role.ASSISTANT
            },
        content = content,
        metadataJson = metadataJson,
    )

private fun AgentException.toDomainMessage(): String =
    when (this) {
        is AgentException.Internal -> detail
        is AgentException.GuardrailExceeded -> "Guardrail exceeded: $kind"
        is AgentException.ModelStopped -> "Model stopped before completion: $reason"
        is AgentException.RepairFailed -> "Tool repair failed for '$name': $reason"
        is AgentException.CompactionFailed -> "Compaction failed (tier=$tier): $reason"
        is AgentException.InvalidTransition -> "Invalid state transition from '$from' to '$to'"
        is AgentException.ContextReferenceNotFound -> "Context reference not found: $referenceId"
    }
