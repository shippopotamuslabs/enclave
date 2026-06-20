package com.browntowndev.enclave.feature.inference.wire

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Typed view of an [AgentLoopMessage]`s `metadataJson` carrier.
 *
 * Single source of truth for the wire vocabulary shared by the Rust agent engine and the
 * Kotlin provider services: `call_id` (tool-result row), `raw_tool_calls` (assistant
 * tool-call row), and `provider_reasoning.reasoning_content` (replay-only reasoning).
 * Replaces the six-plus inline `toMetadataJsonObject` copies that drifted across the
 * OpenAI/Anthropic/Google/Llama services.
 */
data class AgentLoopMessageMetadata(
    /** Non-null when this row is a tool result addressing a prior assistant tool call. */
    val toolCallId: String?,
    /** Assistant tool calls emitted on a prior turn; empty for non-tool-call assistant rows. */
    val rawToolCalls: List<RawToolCallRef>,
    /** Provider reasoning content captured on a prior turn and replayed onto the wire. */
    val providerReasoningContent: String?,
    /** True for engine-injected infrastructure rows (budget, nudges, envelope failures). */
    val isEngineInternal: Boolean,
)

/**
 * One entry from a prior assistant turn's `raw_tool_calls` metadata array.
 *
 * `id` is nullable because some providers (Anthropic, Google) tolerate empty tool-call ids
 * on replay, while OpenAI drops them. The per-provider wire mapper decides how to handle a
 * null id; the classifier only reports it.
 */
data class RawToolCallRef(
    val id: String?,
    val name: String,
    val argumentsJson: String,
)

/**
 * Parses [AgentLoopMessage.metadataJson] into a typed [AgentLoopMessageMetadata].
 *
 * Malformed JSON, `"null"`, and blank strings all yield a metadata object with null/empty
 * fields — matching the prior inline behavior at every call site. Engine-internal detection
 * delegates to [EngineInternalMessageMapper.isEngineInternalMessage] so the vocabulary stays
 * in one place.
 */
fun AgentLoopMessage.metadata(): AgentLoopMessageMetadata {
    val obj = metadataJson.toMetadataJsonObject()
    val toolCallId = obj?.get("call_id")?.jsonPrimitive?.contentOrNull
    val rawToolCalls = obj.rawToolCalls()
    val reasoningContent = obj?.providerReasoningContent()
    return AgentLoopMessageMetadata(
        toolCallId = toolCallId,
        rawToolCalls = rawToolCalls,
        providerReasoningContent = reasoningContent,
        isEngineInternal = EngineInternalMessageMapper.isEngineInternalMessage(metadataJson),
    )
}

/** True when the row carries a `call_id` (tool-result row), without allocating the full metadata object. */
fun AgentLoopMessage.hasToolCallMetadata(): Boolean = metadata().toolCallId != null

private fun JsonObject?.rawToolCalls(): List<RawToolCallRef> {
    val array = this?.get("raw_tool_calls") as? JsonArray ?: return emptyList()
    return array.mapNotNull { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
        val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val argsJson = obj["arguments_json"]?.jsonPrimitive?.contentOrNull ?: "{}"
        RawToolCallRef(id = id, name = name, argumentsJson = argsJson)
    }
}

private val wireJson: Json = Json { ignoreUnknownKeys = true }

internal fun String?.toMetadataJsonObject(): JsonObject? =
    if (this.isNullOrBlank() || this == "null") {
        null
    } else {
        runCatching { wireJson.parseToJsonElement(this).jsonObject }.getOrNull()
    }

/**
 * Extracts `provider_reasoning.reasoning_content` from a parsed metadata object.
 *
 * Replaces the private top-level copy at the bottom of [BaseOpenAiSdkInferenceService] so the
 * reasoning-replay vocabulary is owned by the wire package.
 */
fun JsonObject?.providerReasoningContent(): String? =
    this
        ?.get("provider_reasoning")
        ?.jsonObject
        ?.get("reasoning_content")
        ?.jsonPrimitive
        ?.contentOrNull
        ?.takeIf { it.isNotBlank() }
