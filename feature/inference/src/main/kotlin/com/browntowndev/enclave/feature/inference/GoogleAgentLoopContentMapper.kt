package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.feature.inference.appendonly.SidecarMarker
import com.browntowndev.enclave.feature.inference.appendonly.SidecarUserContentEmbedder
import com.browntowndev.enclave.feature.inference.wire.ProviderWireMessageMapper
import com.browntowndev.enclave.feature.inference.wire.lastNonToolResultUserIndex as wireLastNonToolResultUserIndex
import com.browntowndev.enclave.feature.inference.wire.toMetadataJsonObject
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps [AgentLoopMessage] rows to Gemini [Content] for the Rust agent loop, preserving
 * tool results, assistant tool calls, summaries, and multimodal user turns.
 *
 * Implements [ProviderWireMessageMapper] so the shared [classify] dispatcher drives the
 * role/call_id/raw_tool_calls/compact-summary dispatch; the leaf builder calls remain
 * Google-specific. [mapMessage] remains the per-row entry point used by the service so the
 * sidecar-marker embedding and image-target index logic stay intact.
 */
internal object GoogleAgentLoopContentMapper : ProviderWireMessageMapper<Content> {
    private val toolArgsMapper =
        com.fasterxml.jackson.databind
            .ObjectMapper()

    fun lastNonToolResultUserIndex(sessionMessages: List<AgentLoopMessage>): Int =
        sessionMessages.wireLastNonToolResultUserIndex()

    fun mapMessage(
        message: AgentLoopMessage,
        index: Int,
        lastUserIndex: Int,
        imageUris: List<String>,
        sidecarMarkers: List<SidecarMarker> = emptyList(),
    ): Content? {
        val metadata = message.metadataJson.toMetadataJsonObject()
        val contentWithSidecars = SidecarUserContentEmbedder.embed(message.content, sidecarMarkers)

        if (com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper.isEngineInternalMessage(message.metadataJson)) {
            val wireText = com.browntowndev.enclave.feature.inference.EngineInternalMessageMapper.formatForProviderWire(message.content)
            if (wireText.isBlank()) return null
            return Content
                .builder()
                .role("user")
                .parts(listOf(Part.fromText(wireText)))
                .build()
        }

        return when (message.role) {
            com.browntowndev.enclave.domain.model.chat.Role.SYSTEM -> null
            com.browntowndev.enclave.domain.model.chat.Role.USER -> {
                val attachImages = index == lastUserIndex && imageUris.isNotEmpty()
                userText(contentWithSidecars, if (attachImages) imageUris else emptyList(), useCacheControl = false)
            }
            com.browntowndev.enclave.domain.model.chat.Role.ASSISTANT ->
                mapAssistantMessage(message, metadata, contentWithSidecars)
        }
    }

    private fun mapAssistantMessage(
        message: AgentLoopMessage,
        metadata: kotlinx.serialization.json.JsonObject?,
        contentText: String,
    ): Content? {
        if (message.id.startsWith("compact-summary-")) {
            return compactSummary(contentText, useCacheControl = false)
        }
        val callId = metadata?.get("call_id")?.jsonPrimitive?.content
        if (callId != null) {
            return toolResult(callId, contentText, isOrphan = false, useCacheControl = false)
        }
        val rawCalls = metadata?.get("raw_tool_calls") as? JsonArray
        val toolCalls =
            rawCalls?.mapNotNull { tc ->
                val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@mapNotNull null
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val id = obj["id"]?.jsonPrimitive?.contentOrNull
                val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"
                com.browntowndev.enclave.feature.inference.wire.RawToolCallRef(id = id, name = name, argumentsJson = argsJson)
            }.orEmpty()
        return assistantText(contentText, toolCalls, reasoningContent = null, useCacheControl = false)
    }

    override fun systemPrompt(content: String, useCacheControl: Boolean): Content? = null

    override fun userText(
        content: String,
        imageUris: List<String>,
        useCacheControl: Boolean,
    ): Content {
        val parts =
            if (imageUris.isEmpty()) {
                listOf(Part.fromText(ImagePayloads.stripBase64DataUris(content)))
            } else {
                buildList {
                    add(Part.fromText(ImagePayloads.stripBase64DataUris(content)))
                    ImagePayloads.fromUris(imageUris).forEach { payload ->
                        add(Part.fromBytes(payload.bytes, payload.mimeType))
                    }
                }
            }
        return Content
            .builder()
            .role("user")
            .parts(parts)
            .build()
    }

    override fun engineInternal(wireText: String): Content? =
        if (wireText.isBlank()) {
            null
        } else {
            Content
                .builder()
                .role("user")
                .parts(listOf(Part.fromText(wireText)))
                .build()
        }

    override fun compactSummary(content: String, useCacheControl: Boolean): Content =
        Content
            .builder()
            .role("user")
            .parts(listOf(Part.fromText(formatCompactSummaryWire(content))))
            .build()

    override fun toolResult(
        toolCallId: String,
        content: String,
        isOrphan: Boolean,
        useCacheControl: Boolean,
    ): Content =
        Content
            .builder()
            .role("user")
            .parts(listOf(buildFunctionResponsePart(toolCallId, content)))
            .build()

    override fun assistantText(
        content: String,
        toolCalls: List<com.browntowndev.enclave.feature.inference.wire.RawToolCallRef>,
        reasoningContent: String?,
        useCacheControl: Boolean,
    ): Content? {
        val parts = mutableListOf<Part>()
        if (content.isNotBlank()) {
            parts.add(Part.fromText(ImagePayloads.stripBase64DataUris(content)))
        }
        toolCalls.forEach { ref ->
            val args =
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    toolArgsMapper.readValue(ref.argumentsJson, Map::class.java) as Map<String, Any>
                }.getOrElse { emptyMap() }
            parts.add(Part.fromFunctionCall(ref.name, args))
        }
        if (parts.isEmpty()) return null
        return Content
            .builder()
            .role("model")
            .parts(parts)
            .build()
    }

    private fun buildFunctionResponsePart(
        toolName: String,
        resultJson: String,
    ): Part =
        Part.fromFunctionResponse(
            toolName,
            mapOf("output" to resultJson),
        )

    private fun formatCompactSummaryWire(content: String): String = "[Conversation summary]\n$content"
}
