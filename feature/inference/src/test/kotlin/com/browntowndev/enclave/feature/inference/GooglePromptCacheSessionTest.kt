package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.google.genai.types.Content
import com.google.genai.types.Part
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class GooglePromptCacheSessionTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

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

            coVerify(exactly = 2) {
                sdkClient.createCachedContent(
                    modelId = "gemini-2.5-flash",
                    contents = any(),
                    systemInstruction = any(),
                    tools = any(),
                    ttlSeconds = 300L,
                )
            }
            coVerify(exactly = 1) { sdkClient.deleteCachedContent("cachedContents/cache-a") }
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

            coVerify(exactly = 2) {
                sdkClient.createCachedContent(
                    modelId = "gemini-2.5-flash",
                    contents = any(),
                    systemInstruction = any(),
                    tools = any(),
                    ttlSeconds = 300L,
                )
            }
            coVerify(exactly = 1) { sdkClient.deleteCachedContent("cachedContents/cache-a") }
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

            coVerify(exactly = 2) {
                sdkClient.createCachedContent(
                    modelId = "gemini-2.5-flash",
                    contents = any(),
                    systemInstruction = any(),
                    tools = any(),
                    ttlSeconds = 300L,
                )
            }
            coVerify(exactly = 1) { sdkClient.deleteCachedContent("cachedContents/cache-a") }
        }

    private fun cachedContentClient(): GoogleGenAiSdkClient {
        val sdkClient = mockk<GoogleGenAiSdkClient>()
        coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returnsMany
            listOf("cachedContents/cache-a", "cachedContents/cache-b")
        coEvery { sdkClient.deleteCachedContent(any()) } returns Unit
        return sdkClient
    }

    private fun promptCacheSession(sdkClient: GoogleGenAiSdkClient): GooglePromptCacheSession =
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
