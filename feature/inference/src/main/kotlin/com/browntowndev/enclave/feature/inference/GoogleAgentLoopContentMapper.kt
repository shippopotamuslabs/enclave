package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps [AgentLoopMessage] rows to Gemini [Content] for the Rust agent loop, preserving
 * tool results, assistant tool calls, summaries, and multimodal user turns.
 */
internal object GoogleAgentLoopContentMapper {
    private val toolArgsMapper = com.fasterxml.jackson.databind.ObjectMapper()

    fun lastNonToolResultUserIndex(sessionMessages: List<AgentLoopMessage>): Int =
        sessionMessages.indexOfLast { msg ->
            msg.role == Role.USER && run {
                val meta = msg.metadataJson
                if (meta == "null" || meta.isBlank()) return@run true
                val obj = runCatching { Json.parseToJsonElement(meta).jsonObject }.getOrNull()
                obj?.get("call_id")?.jsonPrimitive?.contentOrNull == null
            }
        }

    fun mapMessage(
        message: AgentLoopMessage,
        index: Int,
        lastUserIndex: Int,
        imageUris: List<String>,
        prependedText: String? = null,
    ): Content? {
        val metadata =
            if (message.metadataJson != "null" && message.metadataJson.isNotBlank()) {
                runCatching { Json.parseToJsonElement(message.metadataJson).jsonObject }.getOrNull()
            } else {
                null
            }

        val contentText =
            if (!prependedText.isNullOrBlank()) {
                "$prependedText\n\n${message.content}"
            } else {
                message.content
            }

        return when (message.role) {
            Role.SYSTEM -> null
            Role.USER -> mapUserMessage(contentText, index, lastUserIndex, imageUris)
            Role.ASSISTANT -> mapAssistantMessage(message, contentText, metadata)
        }
    }

    private fun mapUserMessage(
        contentText: String,
        index: Int,
        lastUserIndex: Int,
        imageUris: List<String>,
    ): Content {
        val attachImages = index == lastUserIndex && imageUris.isNotEmpty()
        val parts =
            if (!attachImages) {
                listOf(Part.fromText(ImagePayloads.stripBase64DataUris(contentText)))
            } else {
                buildList {
                    add(Part.fromText(ImagePayloads.stripBase64DataUris(contentText)))
                    ImagePayloads.fromUris(imageUris).forEach { payload ->
                        add(Part.fromBytes(payload.bytes, payload.mimeType))
                    }
                }
            }
        return Content.builder().role("user").parts(parts).build()
    }

    private fun mapAssistantMessage(
        message: AgentLoopMessage,
        contentText: String,
        metadata: kotlinx.serialization.json.JsonObject?,
    ): Content? {
        if (message.id.startsWith("compact-summary-")) {
            return Content
                .builder()
                .role("user")
                .parts(listOf(Part.fromText("[Conversation summary]\n$contentText")))
                .build()
        }
        val callId = metadata?.get("call_id")?.jsonPrimitive?.content
        if (callId != null) {
            return Content
                .builder()
                .role("user")
                .parts(listOf(buildFunctionResponsePart(callId, contentText)))
                .build()
        }
        val rawCalls = metadata?.get("raw_tool_calls")
        val parts = mutableListOf<Part>()
        if (contentText.isNotBlank()) {
            parts.add(Part.fromText(ImagePayloads.stripBase64DataUris(contentText)))
        }
        if (rawCalls is JsonArray) {
            rawCalls.forEach { tc ->
                val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                val callName = obj["name"]?.jsonPrimitive?.content ?: return@forEach
                val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"
                val args =
                    runCatching {
                        @Suppress("UNCHECKED_CAST")
                        toolArgsMapper.readValue(argsJson, Map::class.java) as Map<String, Any>
                    }.getOrElse { emptyMap() }
                parts.add(Part.fromFunctionCall(callName, args))
            }
        }
        if (parts.isEmpty()) {
            return null
        }
        return Content.builder().role("model").parts(parts).build()
    }

    private fun buildFunctionResponsePart(
        toolName: String,
        resultJson: String,
    ): Part =
        Part.fromFunctionResponse(
            toolName,
            mapOf("output" to resultJson),
        )
}
