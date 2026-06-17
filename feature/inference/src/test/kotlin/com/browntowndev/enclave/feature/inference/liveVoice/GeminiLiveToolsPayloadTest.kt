package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.WebSocket
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class GeminiLiveToolsPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("LV-TOOL-040")
    fun lvTool040_setupIncludesFunctionDeclarations() {
        val toolsArray =
            buildGeminiLiveSetupTools(
                listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.SEARCH_CHAT),
            )

        assertNotNull(toolsArray)
        assertEquals(1, toolsArray!!.size)
        val declarations = toolsArray[0].jsonObject["functionDeclarations"]!!.jsonArray
        assertEquals(2, declarations.size)
        assertEquals("web_search", declarations[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals("search_chat", declarations[1].jsonObject["name"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("LV-TOOL-051")
    fun lvTool051_sendToolResultEmitsToolResponse() =
        runTest {
            val sentMessages = mutableListOf<String>()
            val webSocket =
                mockk<WebSocket> {
                    every { send(any<String>()) } answers {
                        sentMessages.add(firstArg())
                        true
                    }
                }
            val session =
                GeminiLiveSession(
                    asset = geminiAsset(),
                    apiKey = "key",
                )
            session.attachWebSocketForTest(webSocket)

            session.sendToolResult(
                callId = "fc_9",
                name = "manage_memories",
                resultJson = """{"memories":[]}""",
            )

            assertEquals(1, sentMessages.size)
            val root = json.parseToJsonElement(sentMessages.single()).jsonObject
            val response = root["toolResponse"]!!.jsonObject["functionResponses"]!!.jsonArray[0].jsonObject
            assertEquals("fc_9", response["id"]!!.jsonPrimitive.content)
            assertEquals("manage_memories", response["name"]!!.jsonPrimitive.content)
            assertTrue(response["response"]!!.jsonObject.containsKey("memories"))
            assertEquals(0, response["response"]!!.jsonObject["memories"]!!.jsonArray.size)
        }

    @Test
    @DisplayName("LV-TOOL-052")
    fun lvTool052_nonJsonResultWrappedAsError() =
        runTest {
            val sentMessages = mutableListOf<String>()
            val webSocket =
                mockk<WebSocket> {
                    every { send(any<String>()) } answers {
                        sentMessages.add(firstArg())
                        true
                    }
                }
            val session =
                GeminiLiveSession(
                    asset = geminiAsset(),
                    apiKey = "key",
                )
            session.attachWebSocketForTest(webSocket)

            session.sendToolResult(
                callId = "fc_err",
                name = "web_search",
                resultJson = "not-json",
            )

            assertEquals(1, sentMessages.size)
            val root = json.parseToJsonElement(sentMessages.single()).jsonObject
            val response =
                root["toolResponse"]!!
                    .jsonObject["functionResponses"]!!
                    .jsonArray[0]
                    .jsonObject["response"]!!
                    .jsonObject
            assertEquals("not-json", response["error"]!!.jsonPrimitive.content)
        }

    @Test
    @DisplayName("LV-TOOL-MUT-005")
    fun lvToolMut005_geminiSetupIncludesToolsWhenNonEmpty() {
        val toolsArray = buildGeminiLiveSetupTools(listOf(ToolDefinition.WEB_SEARCH))

        assertNotNull(toolsArray)
        assertTrue(toolsArray!!.toString().contains("functionDeclarations"))
    }

    private fun geminiAsset() =
        LiveVoiceProviderAsset(
            id = LiveVoiceProviderId("lv-1"),
            displayName = "Gemini Live",
            provider = ApiProvider.GOOGLE,
            voiceName = "Puck",
            credentialAlias = "alias",
        )
}