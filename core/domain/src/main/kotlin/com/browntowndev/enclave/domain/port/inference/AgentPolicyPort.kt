package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.AgentLoopToolCatalogJson
import com.browntowndev.enclave.domain.model.inference.AgentLoopToolSemanticsResources

data class AgentPolicyRepairResult(
    val resolvedName: String,
    val argumentsJson: String,
    val confidence: Float,
)

interface AgentPolicyPort {
    fun repairTool(
        name: String,
        argsJson: String,
        toolSemanticsJson: String = AgentLoopToolSemanticsResources.enclaveDefaultJson,
        toolCatalogJson: String = AgentLoopToolCatalogJson.fullCatalogFileJson,
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
