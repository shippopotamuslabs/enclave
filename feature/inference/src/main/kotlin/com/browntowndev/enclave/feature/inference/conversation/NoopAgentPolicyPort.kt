package com.browntowndev.enclave.feature.inference.conversation

import com.browntowndev.enclave.domain.port.inference.AgentPolicyPort
import com.browntowndev.enclave.domain.port.inference.AgentPolicyRepairResult
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.Json as KJson

internal object NoopAgentPolicyPort : AgentPolicyPort {
    override fun repairTool(
        name: String,
        argsJson: String,
        toolSemanticsJson: String,
        toolCatalogJson: String,
    ): AgentPolicyRepairResult = AgentPolicyRepairResult(resolvedName = name, argumentsJson = argsJson, confidence = 0.0f)

    override fun truncateResult(
        json: String,
        budgetBytes: ULong,
    ): String = json.take(budgetBytes.toInt())

    override fun shouldAllowTool(
        softFailureStreak: UInt,
        maxStreak: UInt,
    ): Boolean = softFailureStreak < maxStreak

    override fun resultJsonLooksSoftFailure(json: String): Boolean =
        runCatching {
            val obj = KJson.parseToJsonElement(json).jsonObject
            obj.containsKey("error") ||
                obj["status"]?.jsonPrimitive?.content?.equals("error", ignoreCase = true) == true
        }.getOrDefault(false)
}
