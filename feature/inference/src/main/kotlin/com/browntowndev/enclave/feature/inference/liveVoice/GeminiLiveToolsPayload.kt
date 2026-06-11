package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val geminiToolsPayloadJson =
    Json {
        ignoreUnknownKeys = true
    }

fun buildGeminiLiveSetupTools(tools: List<ToolDefinition>): JsonArray? {
    if (tools.isEmpty()) return null
    return JsonArray(
        listOf(
            buildJsonObject {
                putJsonArray("functionDeclarations") {
                    tools.forEach { tool ->
                        add(LiveVoiceGeminiSchemaMapper.toFunctionDeclaration(tool))
                    }
                }
            },
        ),
    )
}

fun buildGeminiToolResponseMessage(
    callId: String,
    name: String,
    resultJson: String,
): String {
    val responseElement =
        runCatching { geminiToolsPayloadJson.parseToJsonElement(resultJson) }.getOrNull()
    val responseObject: JsonElement =
        when (responseElement) {
            is JsonObject, is JsonArray -> responseElement
            else ->
                buildJsonObject {
                    put("error", resultJson)
                }
        }
    return buildJsonObject {
        put(
            "toolResponse",
            buildJsonObject {
                putJsonArray("functionResponses") {
                    add(
                        buildJsonObject {
                            put("id", callId)
                            put("name", name)
                            put("response", responseObject)
                        },
                    )
                }
            },
        )
    }.toString()
}
