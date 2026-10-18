package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GooglePromptCacheSessionTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun ensureCacheForStablePrefix_recreatesCacheWhenSystemPromptChanges(): Unit =
        runTest {
            val sdkClient = cachedContentClient()
            val session = promptCacheSession(sdkClient)
            val stableHistory = stableHistoryOverCacheThreshold()

            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt A",
                stableHistory = stableHistory,
                availableTools = emptyList(),
                mapToContent = ::toContent,
            )
            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt B",
                stableHistory = stableHistory,
                availableTools = emptyList(),
                mapToContent = ::toContent,
            )

            assertEquals(
                listOf("gemini-2.5-flash", "gemini-2.5-flash"),
                sdkClient.createCachedContentCalls,
            )
            assertEquals(listOf(300L, 300L), sdkClient.createCachedContentTtls)
            assertEquals(listOf("cachedContents/cache-a"), sdkClient.deleteCachedContentCalls)
        }

    @Test
    fun ensureCacheForStablePrefix_recreatesCacheWhenStableMetadataChanges(): Unit =
        runTest {
            val sdkClient = cachedContentClient()
            val session = promptCacheSession(sdkClient)

            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt",
                stableHistory = stableHistoryOverCacheThreshold(metadataJson = """{"version":1}"""),
                availableTools = emptyList(),
                mapToContent = ::toContent,
            )
            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt",
                stableHistory = stableHistoryOverCacheThreshold(metadataJson = """{"version":2}"""),
                availableTools = emptyList(),
                mapToContent = ::toContent,
            )

            assertEquals(
                listOf("gemini-2.5-flash", "gemini-2.5-flash"),
                sdkClient.createCachedContentCalls,
            )
            assertEquals(listOf(300L, 300L), sdkClient.createCachedContentTtls)
            assertEquals(listOf("cachedContents/cache-a"), sdkClient.deleteCachedContentCalls)
        }

    @Test
    fun ensureCacheForStablePrefix_recreatesCacheWhenToolSetChanges(): Unit =
        runTest {
            val sdkClient = cachedContentClient()
            val session = promptCacheSession(sdkClient)
            val stableHistory = stableHistoryOverCacheThreshold()

            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt",
                stableHistory = stableHistory,
                availableTools = emptyList(),
                mapToContent = ::toContent,
            )
            session.ensureCacheForStablePrefix(
                systemPrompt = "System prompt",
                stableHistory = stableHistory,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                mapToContent = ::toContent,
            )

            assertEquals(
                listOf("gemini-2.5-flash", "gemini-2.5-flash"),
                sdkClient.createCachedContentCalls,
            )
            assertEquals(listOf(300L, 300L), sdkClient.createCachedContentTtls)
            assertEquals(listOf("cachedContents/cache-a"), sdkClient.deleteCachedContentCalls)
        }

    private fun cachedContentClient(): FakeGoogleGenAiSdkClient {
        val sdkClient = FakeGoogleGenAiSdkClient()
        sdkClient.createCachedContentResultQueue.addAll(
            listOf("cachedContents/cache-a", "cachedContents/cache-b"),
        )
        return sdkClient
    }

    private fun promptCacheSession(sdkClient: FakeGoogleGenAiSdkClient): GooglePromptCacheSession =
        GooglePromptCacheSession(
            modelId = "gemini-2.5-flash",
            sdkClient = sdkClient,
            loggingPort = loggingPort,
            tag = "GooglePromptCacheSessionTest",
        )

    private fun stableHistoryOverCacheThreshold(metadataJson: String = "null"): List<AgentLoopMessage> =
        listOf(
            AgentLoopMessage(
                id = "u1",
                role = Role.USER,
                content = "stable prefix ".repeat(1300),
                metadataJson = metadataJson,
            ),
            AgentLoopMessage(
                id = "a1",
                role = Role.ASSISTANT,
                content = "stable answer",
            ),
        )

    private fun toContent(message: AgentLoopMessage): Content = Content.fromParts(Part.fromText(message.content))
}
