package com.browntowndev.enclave.feature.inference.liveVoice

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.LiveVoiceToolset
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.RecordingWebSocket
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class LiveVoicePortImplTest {
    private val port = LiveVoicePortImpl()

    private fun asset(provider: ApiProvider) =
        LiveVoiceProviderAsset(
            id = LiveVoiceProviderId("lv-1"),
            displayName = "Test Provider",
            provider = provider,
            voiceName = "alloy",
            credentialAlias = "alias",
        )

    @ParameterizedTest
    @EnumSource(value = ApiProvider::class, names = ["OPENAI", "XAI"])
    @DisplayName("LV-ROUTE-001: OPENAI and XAI route to OpenAiRealtimeSession")
    fun lvRoute001_openAiCompatibleProviders(provider: ApiProvider) =
        runTest {
            val result = port.openSession(asset(provider), "key")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow() is OpenAiRealtimeSession)
        }

    @Test
    @DisplayName("LV-ROUTE-002: GOOGLE routes to GeminiLiveSession")
    fun lvRoute002_googleRoutesToGemini() =
        runTest {
            val result = port.openSession(asset(ApiProvider.GOOGLE), "key")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrThrow() is GeminiLiveSession)
        }

    @Test
    @DisplayName("LV-ROUTE-003: unsupported provider returns failure")
    fun lvRoute003_unsupportedProviderFails() =
        runTest {
            val result = port.openSession(asset(ApiProvider.ANTHROPIC), "key")

            assertTrue(result.isFailure)
            assertEquals(
                "Live Voice not supported for provider: ANTHROPIC",
                result.exceptionOrNull()?.message,
            )
        }

    @Test
    @DisplayName("LV-MUT-001: GOOGLE must not route to OpenAiRealtimeSession")
    fun lvMut001_googleMustNotRouteToOpenAiSession() =
        runTest {
            val result = port.openSession(asset(ApiProvider.GOOGLE), "key")

            assertTrue(result.isSuccess)
            val session = result.getOrThrow()
            assertTrue(session is GeminiLiveSession)
            assertFalse(session is OpenAiRealtimeSession)
        }

    @Test
    @DisplayName("LV-TOOL-003")
    fun lvTool003_portForwardsToolsToOpenAiSession() =
        runTest {
            val tools = LiveVoiceToolset.tools
            val result = port.openSession(asset(ApiProvider.OPENAI), "key", tools = tools)

            assertTrue(result.isSuccess)
            val session = result.getOrThrow() as OpenAiRealtimeSession
            assertEquals(tools, session.tools)
            assertEquals(11, session.tools.size)
        }

    @Test
    @DisplayName("LV-TOOL-MUT-006")
    fun lvToolMut006_portMustForwardToolsToSessionUpdate() =
        runTest {
            val tools = listOf(ToolDefinition.WEB_SEARCH)
            val webSocket = RecordingWebSocket()
            val sentMessages = webSocket.sentTexts
            val result = port.openSession(asset(ApiProvider.OPENAI), "key", tools = tools)

            assertTrue(result.isSuccess)
            assertNotNull(buildOpenAiRealtimeToolsArray(tools))
            val session = result.getOrThrow() as OpenAiRealtimeSession
            session.sendSessionUpdateForTest(webSocket)

            assertEquals(1, sentMessages.size)
            val sessionObject =
                Json
                    .parseToJsonElement(sentMessages.single())
                    .jsonObject["session"]!!
                    .jsonObject
            val toolNames =
                sessionObject["tools"]!!
                    .jsonArray
                    .map { it.jsonObject["name"]!!.jsonPrimitive.content }
            assertTrue(toolNames.contains("web_search"))
        }
}