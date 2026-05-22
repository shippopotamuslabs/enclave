package com.browntowndev.enclave.domain.port.inference

data class AgentPolicyRepairResult(
    val resolvedName: String,
    val argumentsJson: String,
    val confidence: Float,
)

interface AgentPolicyPort {
    fun repairTool(
        name: String,
        argsJson: String,
    ): AgentPolicyRepairResult

    fun truncateResult(
        json: String,
        budgetBytes: ULong,
    ): String

    fun shouldAllowTool(
        softFailureStreak: UInt,
        maxStreak: UInt,
    ): Boolean

    fun resultJsonLooksSoftFailure(json: String): Boolean
}
