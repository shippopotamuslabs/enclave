package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.LiveVoiceToolset
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.RecordingWebSocket
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class OpenAiRealtimeToolsPayloadTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    @DisplayName("LV-TOOL-010")
    fun lvTool010_sessionUpdateIncludesToolsAndToolChoiceAuto() {
        val session =
            buildJsonObject {
                applyOpenAiRealtimeToolsToSession(this, listOf(ToolDefinition.WEB_SEARCH))
            }

        val tools = session["tools"]!!.jsonArray
        assertEquals(1, tools.size)
        assertEquals("function", tools[0].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("web_search", tools[0].jsonObject["name"]!!.jsonPrimitive.content)
        assertEquals(
            "Search the public web for current or explicitly requested online information. " +
                "Use directly without tool_discovery_tool when a web lookup is needed.",
            tools[0].jsonObject["description"]!!.jsonPrimitive.content,
        )
        assertEquals(
            "object",
            tools[0].jsonObject["parameters"]!!.jsonObject["type"]!!.jsonPrimitive.content,
        )
        assertEquals("auto", session["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    @DisplayName("LV-TOOL-011")
    fun lvTool011_emptyToolsOmitsToolsAndToolChoice() {
        val session =
            buildJsonObject {
                applyOpenAiRealtimeToolsToSession(this, emptyList())
            }

        assertFalse(session.containsKey("tools"))
        assertFalse(session.containsKey("tool_choice"))
    }

    @Test
    @DisplayName("LV-TOOL-012")
    fun lvTool012_fullWhitelistProducesElevenFunctionDeclarations() {
        val toolsArray = buildOpenAiRealtimeToolsArray(LiveVoiceToolset.tools)

        assertNotNull(toolsArray)
        assertEquals(11, toolsArray!!.size)
        toolsArray.forEach { element ->
            assertEquals("function", element.jsonObject["type"]!!.jsonPrimitive.content)
        }
        val names = toolsArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }.toSet()
        assertEquals(
            setOf(
                "web_search",
                "web_extract",
                "search_chat_history",
                "search_chat",
                "get_message_context",
                "manage_memories",
                "search_x_recent",
                "x_get_thread",
                "x_trending",
                "x_user_tweets",
                "x_mention_search",
            ),
            names,
        )
    }

    @Test
    @DisplayName("LV-TOOL-030")
    fun lvTool030_sendToolResultWireFormat() =
        runTest {
            val webSocket = RecordingWebSocket()
            val sentMessages = webSocket.sentTexts
            val session =
                OpenAiRealtimeSession(
                    asset = openAiAsset(),
                    apiKey = "key",
                )
            session.attachWebSocketForTest(webSocket)

            session.sendToolResult(
                callId = "call_1",
                name = "web_search",
                resultJson = """{"hits":[]}""",
            )

            assertEquals(2, sentMessages.size)
            val message0 = json.parseToJsonElement(sentMessages[0]).jsonObject
            assertEquals("conversation.item.create", message0["type"]!!.jsonPrimitive.content)
            val item = message0["item"]!!.jsonObject
            assertEquals("function_call_output", item["type"]!!.jsonPrimitive.content)
            assertEquals("call_1", item["call_id"]!!.jsonPrimitive.content)
            assertFalse(item.containsKey("name"))
            assertEquals("""{"hits":[]}""", item["output"]!!.jsonPrimitive.content)
            val message1 = json.parseToJsonElement(sentMessages[1]).jsonObject
            assertEquals("response.create", message1["type"]!!.jsonPrimitive.content)
        }

    @Test
    @DisplayName("LV-TOOL-031")
    fun lvTool031_sendToolResultAfterCloseIsNoOp() =
        runTest {
            val webSocket = RecordingWebSocket().apply { sendResult = false }
            val sentMessages = webSocket.sentTexts
            val session =
                OpenAiRealtimeSession(
                    asset = openAiAsset(),
                    apiKey = "key",
                )
            session.attachWebSocketForTest(webSocket)
            session.close()

            session.sendToolResult(
                callId = "call_1",
                name = "web_search",
                resultJson = "{}",
            )

            assertEquals(0, sentMessages.size)
        }

    @Test
    @DisplayName("LV-TOOL-MUT-003")
    fun lvToolMut003_toolChoiceRequiredWhenToolsPresent() {
        val session =
            buildJsonObject {
                applyOpenAiRealtimeToolsToSession(this, listOf(ToolDefinition.WEB_SEARCH))
            }

        assertEquals("auto", session["tool_choice"]!!.jsonPrimitive.content)
        assertTrue(session.containsKey("tools"))
    }

    private fun openAiAsset() =
        LiveVoiceProviderAsset(
            id = LiveVoiceProviderId("lv-1"),
            displayName = "OpenAI RT",
            provider = ApiProvider.OPENAI,
            voiceName = "alloy",
            credentialAlias = "alias",
        )
}