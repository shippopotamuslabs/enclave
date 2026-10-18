package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleInferenceServiceImplCachePrefixTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun `completeSingleTurn creates, reuses and deletes prompt cache for Google`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            sdkClient.streamResult =
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )
            sdkClient.createCachedContentResult = "cachedContents/test-cache-999"

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            // Turn 1: History contains enough tokens to exceed the flash threshold of 1024 tokens.
            // "stable prefix " is 14 characters. Replicated 150 times gives a very long prompt to exceed 1024 tokens.
            val longPrefixText = "stable prefix ".repeat(1500)
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = longPrefixText),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "acknowledgement"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Turn 1 active user message"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System instruction to cache",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Verify cache creation on Turn 1
            assertEquals(listOf("gemini-2.5-flash"), sdkClient.createCachedContentCalls)
            assertEquals(listOf(300L), sdkClient.createCachedContentTtls)

            // Verify Turn 1 request configuration: cache is created, but the generation call itself uses the cached content.
            val config1 = sdkClient.lastGenerateConfig!!
            assertTrue(config1.cachedContent().isPresent)
            assertEquals("cachedContents/test-cache-999", config1.cachedContent().get())

            // Verify Turn 1 contentsToSend only contains delta contents (the last user message "Turn 1 active user message").
            // Since Turn 1's stableHistory is cached, only the delta (turn1Messages.last()) is sent.
            val capturedContents1 = sdkClient.lastGenerateContents!!
            assertEquals(1, capturedContents1.size)
            assertEquals(
                "Turn 1 active user message",
                capturedContents1
                    .single()
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )

            // Turn 2: Keep the prefix, append Turn 1's assistant answer, and a new Turn 2 active user message.
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = longPrefixText),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "acknowledgement"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Turn 1 active user message"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Turn 1 model response"),
                    AgentLoopMessage(id = "u3", role = Role.USER, content = "Turn 2 active user message"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System instruction to cache",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Verify no new cached content was created (the existing one was reused)
            assertEquals(1, sdkClient.createCachedContentCalls.size)

            // Verify Turn 2 request configuration continues to use the active cached content
            val config2 = sdkClient.lastGenerateConfig!!
            assertTrue(config2.cachedContent().isPresent)
            assertEquals("cachedContents/test-cache-999", config2.cachedContent().get())

            // Verify Turn 2 contentsToSend only contains the delta history relative to the cache signature.
            // Cached signature contains stable history up to [u1, a1].
            // So delta history has u2, a2, and the live message u3.
            // That is 3 messages total!
            val capturedContents2 = sdkClient.lastGenerateContents!!
            assertEquals(3, capturedContents2.size)
            assertEquals(
                "Turn 1 active user message",
                capturedContents2[0]
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )
            assertEquals(
                "Turn 1 model response",
                capturedContents2[1]
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )
            assertEquals(
                "Turn 2 active user message",
                capturedContents2[2]
                    .parts()
                    .get()
                    .single()
                    .text()
                    .get(),
            )

            // Close session and verify cleanup
            service.closeSession()
            assertEquals(listOf("cachedContents/test-cache-999"), sdkClient.deleteCachedContentCalls)
        }
}
