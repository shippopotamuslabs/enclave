package com.browntowndev.enclave.core.agent

import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.AgentPolicyRepairResult
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin Kotlin wrapper around the Rust policy bridge FFI exports (AGENT-034).
 *
 * All heavy logic lives in `agent_core`; this object is a stable call site so
 * callers do not import the UniFFI-generated free functions directly.
 */
@Singleton
class LiteRtAgentPolicyBridge
    @Inject
    constructor() : AgentPolicyPort {
        override fun repairTool(
            name: String,
            argsJson: String,
        ): AgentPolicyRepairResult =
            com.browntowndev.enclave.core.agent.repairTool(name, argsJson).let { result ->
                AgentPolicyRepairResult(
                    resolvedName = result.resolvedName,
                    argumentsJson = result.argumentsJson,
                    confidence = if (result.repaired) 1.0f else 0.0f,
                )
            }

        override fun truncateResult(
            json: String,
            budgetBytes: ULong,
        ): String =
            com.browntowndev.enclave.core.agent
                .truncateResult(json, budgetBytes)

        override fun shouldAllowTool(
            softFailureStreak: UInt,
            maxStreak: UInt,
        ): Boolean =
            com.browntowndev.enclave.core.agent
                .shouldAllowTool(softFailureStreak, maxStreak)

        override fun resultJsonLooksSoftFailure(json: String): Boolean =
            com.browntowndev.enclave.core.agent
                .resultJsonLooksSoftFailure(json)

        companion object {
            const val CONTEXT_FULL_RESULT_BUDGET_BYTES = 256UL
        }
    }
