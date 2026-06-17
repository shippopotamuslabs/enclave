package com.browntowndev.enclave.domain.usecase.chat

import com.browntowndev.enclave.domain.model.inference.AgentToolResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Activates tools referenced by context-management `retrieval_hint` strings so the model can
 * follow spill envelopes and sidecar references without a prior `tool_discovery_tool` call.
 */
object RetrievalHintActivation {
    private val readContextReferenceHint =
        Regex("""^read_context_reference\(".*"\)$""")

    /** Mirrors agent_core default [`tool_spill_threshold_bytes`] for hosted tiers. */
    const val DEFAULT_TOOL_SPILL_THRESHOLD_BYTES: Int = 8_192

    fun toolsFromRetrievalHint(retrievalHint: String): List<String> =
        if (readContextReferenceHint.matches(retrievalHint.trim())) {
            listOf("read_context_reference")
        } else {
            emptyList()
        }

    fun toolsFromToolResults(
        results: List<AgentToolResult>,
        spillThresholdBytes: Int = DEFAULT_TOOL_SPILL_THRESHOLD_BYTES,
    ): List<String> =
        results
            .flatMap { result ->
                toolsFromSpilledEnvelope(result.resultJson) +
                    if (result.resultJson.length > spillThresholdBytes) {
                        listOf("read_context_reference")
                    } else {
                        emptyList()
                    }
            }.distinct()

    private fun toolsFromSpilledEnvelope(resultJson: String): List<String> =
        runCatching {
            val json = Json.parseToJsonElement(resultJson).jsonObject
            if (!json.containsKey("context_ref")) {
                return emptyList()
            }
            json["retrieval_hint"]?.jsonPrimitive?.content?.let(::toolsFromRetrievalHint).orEmpty()
        }.getOrDefault(emptyList())
}
