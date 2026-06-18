package com.browntowndev.enclave.feature.inference.appendonly

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Normalized messages for append-only sync; excludes engine-internal rows not in committed transcripts. */
internal fun List<AgentLoopMessage>.toAppendOnlySyncMessages(): List<ProviderNormalizedMessage> =
    filterNot(EngineInternalMessageMapper::isExcludedFromCommittedProviderTranscript)
        .toProviderNormalizedMessages()

internal fun List<AgentLoopMessage>.toProviderNormalizedMessages(): List<ProviderNormalizedMessage> =
    map { message ->
        val metadata = message.metadataJson.toMetadataObject()
        ProviderNormalizedMessage(
            role = message.role.name.lowercase(),
            content = message.content,
            toolCalls = metadata?.get("raw_tool_calls")?.toString(),
            toolCallId = metadata?.get("call_id")?.jsonPrimitive?.contentOrNull,
            name = null,
            id = message.id,
        )
    }

internal fun List<ToolDefinition>.toToolsFingerprint(): String {
    if (isEmpty()) return "tools:empty"
    return "tools:" + map { it.name }.sorted().joinToString(",")
}

private fun String.toMetadataObject() =
    if (isBlank() || this == "null") {
        null
    } else {
        runCatching { Json.parseToJsonElement(this).jsonObject }.getOrNull()
    }
