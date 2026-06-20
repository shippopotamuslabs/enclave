package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.feature.inference.wire.toMetadataJsonObject
import com.openai.models.responses.ResponseFunctionToolCall
import com.openai.models.responses.ResponseInputImage
import com.openai.models.responses.ResponseInputItem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Maps [AgentLoopMessage] rows directly to Responses API wire segments.
 *
 * Chat Completions mapping lives in [BaseOpenAiSdkInferenceService.toMappedSessionBody];
 * this mapper is the primary Responses path and must stay faithful to tool-call replay.
 */
internal object AgentLoopOpenAiResponseMapper {
    data class MappedResponseSessionBody(
        val segments: List<ResponseWireSegment>,
        val sessionIndexToMappedIndex: IntArray,
    )

    fun mapSession(
        sessionMessages: List<AgentLoopMessage>,
        imageUris: List<String>,
        imageTargetIndex: Int,
        loggingPort: LoggingPort,
        tag: String,
        modelId: String,
    ): MappedResponseSessionBody {
        val sessionIndexToMappedIndex = IntArray(sessionMessages.size) { -1 }
        val pendingToolCallIds = mutableSetOf<String>()
        val segments = mutableListOf<ResponseWireSegment>()
        sessionMessages.forEachIndexed { index, message ->
            if (message.isSyntheticAssistantError()) {
                return@forEachIndexed
            }
            val attachImages = index == imageTargetIndex && imageUris.isNotEmpty()
            val mapped =
                message.toResponseWireSegments(
                    pendingToolCallIds = pendingToolCallIds,
                    imageUris = if (attachImages) imageUris else emptyList(),
                    loggingPort = loggingPort,
                    tag = tag,
                    modelId = modelId,
                )
            if (mapped.isNotEmpty()) {
                sessionIndexToMappedIndex[index] = segments.size
                mapped.forEach { segment ->
                    segment.toolCallIds.forEach { pendingToolCallIds.add(it) }
                    segments.add(segment.segment)
                }
            }
        }
        return MappedResponseSessionBody(
            segments = segments,
            sessionIndexToMappedIndex = sessionIndexToMappedIndex,
        )
    }

    private data class MappedResponseWireSegment(
        val segment: ResponseWireSegment,
        val toolCallIds: List<String> = emptyList(),
    )

    private fun AgentLoopMessage.toResponseWireSegments(
        pendingToolCallIds: MutableSet<String>,
        imageUris: List<String>,
        loggingPort: LoggingPort,
        tag: String,
        modelId: String,
    ): List<MappedResponseWireSegment> {
        val metadata = metadataJson.toMetadataJsonObject()
        if (EngineInternalMessageMapper.isEngineInternalMessage(metadataJson)) {
            return listOf(
                MappedResponseWireSegment(
                    segment =
                        ResponseWireSegment.SystemText(
                            EngineInternalMessageMapper.formatForProviderWire(content),
                        ),
                ),
            )
        }
        val toolCallId = metadata?.get("call_id")?.jsonPrimitive?.contentOrNull
        if (toolCallId != null) {
            return listOf(
                toToolResultResponseSegment(
                    toolCallId = toolCallId,
                    pendingToolCallIds = pendingToolCallIds,
                    loggingPort = loggingPort,
                    tag = tag,
                    modelId = modelId,
                ),
            )
        }

        return when (role) {
            Role.SYSTEM ->
                listOf(
                    MappedResponseWireSegment(
                        segment = ResponseWireSegment.SystemText(ImagePayloads.stripBase64DataUris(content)),
                    ),
                )
            Role.USER ->
                listOf(
                    MappedResponseWireSegment(
                        segment =
                            ResponseWireSegment.Input(
                                buildUserResponseInput(
                                    content = content,
                                    imageUris = imageUris,
                                ),
                            ),
                    ),
                )
            Role.ASSISTANT -> toAssistantResponseSegments(metadata, loggingPort, tag, modelId)
        }
    }

    private fun AgentLoopMessage.toToolResultResponseSegment(
        toolCallId: String,
        pendingToolCallIds: MutableSet<String>,
        loggingPort: LoggingPort,
        tag: String,
        modelId: String,
    ): MappedResponseWireSegment {
        if (toolCallId.isNotBlank() && pendingToolCallIds.remove(toolCallId)) {
            return MappedResponseWireSegment(
                segment =
                    ResponseWireSegment.Input(
                        ResponseInputItem.ofFunctionCallOutput(
                            ResponseInputItem.FunctionCallOutput
                                .builder()
                                .callId(toolCallId)
                                .output(content.ifBlank { "{}" })
                                .build(),
                        ),
                    ),
            )
        }

        loggingPort.warning(
            tag,
            "Converting orphan tool result to user message model=$modelId messageId=$id callId=$toolCallId",
        )
        return MappedResponseWireSegment(
            segment =
                ResponseWireSegment.Input(
                    ResponsesInputMessageSupport.textInputItem(
                        role = Role.USER,
                        content = "[Tool result]\n${content.ifBlank { "{}" }}",
                    ),
                ),
        )
    }

    private fun AgentLoopMessage.toAssistantResponseSegments(
        metadata: JsonObject?,
        loggingPort: LoggingPort,
        tag: String,
        modelId: String,
    ): List<MappedResponseWireSegment> {
        if (id.startsWith("compact-summary-")) {
            return listOf(
                MappedResponseWireSegment(
                    segment = ResponseWireSegment.SystemText("[Conversation summary]\n$content"),
                ),
            )
        }

        val segments = mutableListOf<MappedResponseWireSegment>()
        val sanitizedContent = ImagePayloads.stripBase64DataUris(content)
        if (sanitizedContent.isNotBlank()) {
            segments.add(
                MappedResponseWireSegment(
                    segment =
                        ResponseWireSegment.Input(
                            ResponsesInputMessageSupport.textInputItem(Role.ASSISTANT, sanitizedContent),
                        ),
                ),
            )
        }

        val toolCallIds = mutableListOf<String>()
        val rawCalls = metadata?.get("raw_tool_calls")
        if (rawCalls is JsonArray) {
            rawCalls.forEach { tc ->
                val obj = runCatching { tc.jsonObject }.getOrNull() ?: return@forEach
                val callName = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@forEach
                val rawCallId = obj["id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                if (rawCallId == null) {
                    loggingPort.warning(
                        tag,
                        "Dropping assistant tool call without provider id model=$modelId messageId=$id tool=$callName",
                    )
                    return@forEach
                }
                val argsJson = obj["arguments_json"]?.jsonPrimitive?.content ?: "{}"
                segments.add(
                    MappedResponseWireSegment(
                        segment =
                            ResponseWireSegment.Input(
                                ResponseInputItem.ofFunctionCall(
                                    ResponseFunctionToolCall
                                        .builder()
                                        .callId(rawCallId)
                                        .name(callName)
                                        .arguments(argsJson)
                                        .build(),
                                ),
                            ),
                        toolCallIds = listOf(rawCallId),
                    ),
                )
                toolCallIds.add(rawCallId)
            }
        }

        if (segments.isEmpty()) {
            loggingPort.warning(tag, "Skipping empty assistant response segment model=$modelId messageId=$id")
            return emptyList()
        }
        return segments
    }

    private fun buildUserResponseInput(
        content: String,
        imageUris: List<String>,
    ): ResponseInputItem {
        if (imageUris.isEmpty()) {
            return ResponsesInputMessageSupport.textInputItem(
                role = Role.USER,
                content = ImagePayloads.stripBase64DataUris(content),
            )
        }

        val builder =
            ResponseInputItem.Message
                .builder()
                .role(ResponseInputItem.Message.Role.of("user"))
                .addInputTextContent(ImagePayloads.stripBase64DataUris(content))

        ImagePayloads.fromUris(imageUris).forEach { payload ->
            ImagePayloads.validate(payload)
            builder.addContent(
                ResponseInputImage
                    .builder()
                    .detail(ResponseInputImage.Detail.AUTO)
                    .imageUrl(payload.dataUrl)
                    .build(),
            )
        }
        return ResponseInputItem.ofMessage(builder.build())
    }

    private fun AgentLoopMessage.isSyntheticAssistantError(): Boolean =
        role == Role.ASSISTANT &&
            content.startsWith(ProviderInferenceConstants.SYNTHETIC_API_ERROR_PREFIX)

}

internal sealed class ResponseWireSegment {
    data class SystemText(
        val text: String,
    ) : ResponseWireSegment()

    data class Input(
        val item: ResponseInputItem,
    ) : ResponseWireSegment()
}
