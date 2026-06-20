package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopWireKeys
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Maps engine-injected infrastructure messages (budget, nudges, envelope failures) to
 * provider-safe wire text. Never emits `[Agent]\n{json}` or raw agent-state JSON payloads.
 */
internal object EngineInternalMessageMapper {
    private val ENGINE_INTERNAL_METADATA_KEYS =
        setOf(
            "agent_tool_budget",
            "tools_blocked_nudge",
            "envelope_parse_failure",
        )

    fun isEngineInternalMessage(metadataJson: String): Boolean {
        val metadata = metadataJson.toMetadataJsonObject() ?: return false
        return ENGINE_INTERNAL_METADATA_KEYS.any { metadata.containsKey(it) }
    }

    /**
     * Messages excluded from committed provider transcripts and append-only sync accounting.
     * They may still be sent on the provider wire within a live agent turn.
     */
    fun isExcludedFromCommittedProviderTranscript(message: AgentLoopMessage): Boolean {
        if (isEngineInternalMessage(message.metadataJson)) {
            return true
        }
        val contentObj =
            runCatching {
                Json.parseToJsonElement(message.content).jsonObject
            }.getOrNull()
        return contentObj != null &&
            contentObj.size == 1 &&
            contentObj.containsKey(AgentLoopWireKeys.ENGINE_PAYLOAD_JSON_KEY)
    }

    /**
     * Formats engine-internal content for provider wire transport.
     * [SYSTEM_CONTEXT_PREFIX] prose (Contract C1) is passed through unchanged.
     */
    fun formatForProviderWire(content: String): String {
        val trimmed = content.trim()
        if (trimmed.startsWith(AgentLoopWireKeys.SYSTEM_CONTEXT_PREFIX)) {
            return content
        }
        if (trimmed.contains("\"${AgentLoopWireKeys.ENGINE_PAYLOAD_JSON_KEY}\"")) {
            return ""
        }
        return content.ifBlank { "{}" }
    }

    fun isEngineInternalWireText(text: String): Boolean =
        text.startsWith(AgentLoopWireKeys.SYSTEM_CONTEXT_PREFIX)

    private fun String.toMetadataJsonObject(): JsonObject? =
        if (isBlank() || this == "null") {
            null
        } else {
            runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()
        }
}
