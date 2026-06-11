package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolSchema
import com.browntowndev.enclave.domain.model.inference.WebExtractParams
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class LiveVoiceGeminiSchemaMapperTest {
    @Test
    @DisplayName("LV-TOOL-041")
    fun lvTool041_geminiSchemaStripsUnsupportedFormatKey() {
        val tool =
            ToolDefinition(
                name = "test_uri_tool",
                description = "Synthetic tool for schema mapping.",
                parametersClass = WebExtractParams::class,
                schemaOverride =
                    ToolSchema(
                        properties =
                            buildJsonObject {
                                putJsonObject("url") {
                                    put("type", "string")
                                    put("format", "uri")
                                }
                            },
                        required = listOf("url"),
                    ),
            )

        val declaration = LiveVoiceGeminiSchemaMapper.toFunctionDeclaration(tool)
        val urlProperty = declaration["parameters"]!!.jsonObject["properties"]!!.jsonObject["url"]!!.jsonObject

        assertFalse(urlProperty.containsKey("format"))
        val type = urlProperty["type"]!!.jsonPrimitive.content
        assertTrue(type in setOf("STRING", "INTEGER", "NUMBER", "BOOLEAN", "ARRAY", "OBJECT"))
    }
}