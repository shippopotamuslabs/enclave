package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

private val toolsPayloadJson =
    Json {
        ignoreUnknownKeys = true
    }

fun buildOpenAiRealtimeToolsArray(tools: List<ToolDefinition>): JsonArray? {
    if (tools.isEmpty()) return null
    return JsonArray(tools.map(::buildOpenAiRealtimeToolEntry))
}

fun applyOpenAiRealtimeToolsToSession(
    sessionBuilder: JsonObjectBuilder,
    tools: List<ToolDefinition>,
) {
    if (tools.isEmpty()) return
    val toolsArray = buildOpenAiRealtimeToolsArray(tools) ?: return
    sessionBuilder.put("tools", toolsArray)
    sessionBuilder.put("tool_choice", "auto")
}

@Suppress("UnusedParameter")
fun buildOpenAiToolResultMessages(
    callId: String,
    name: String,
    resultJson: String,
): List<String> {
    val itemCreate =
        buildJsonObject {
            put("type", "conversation.item.create")
            put(
                "item",
                buildJsonObject {
                    put("type", "function_call_output")
                    put("call_id", callId)
                    put("output", resultJson)
                },
            )
        }
    val responseCreate =
        buildJsonObject {
            put("type", "response.create")
        }
    return listOf(itemCreate.toString(), responseCreate.toString())
}

private fun buildOpenAiRealtimeToolEntry(tool: ToolDefinition): JsonObject {
    val parameters = toolsPayloadJson.parseToJsonElement(tool.parametersJson).jsonObject
    return buildJsonObject {
        put("type", "function")
        put("name", tool.name)
        put("description", tool.description)
        put("parameters", parameters)
    }
}
