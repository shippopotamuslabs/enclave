package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.google.genai.types.Content
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

/**
 * Google-specific provider coverage for volatile turn context commit correctness.
 */
class GoogleVolatileTurnContextCommitTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    // ---------------------------------------------------------------------------
    // CSPC-GOOGLE-03a — Google completeSingleTurn; RAG prefix not double-prepended
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_GOOGLE_03a_google_completeSingleTurn_rag_prefix_not_double_prepended() =
        runTest {
            val capturedContentsList = mutableListOf<List<Content>>()
            val service = createGoogleService(capturedContentsList)

            // Turn 1: sessionMessages already contain the merged content (as Rust stored it)
            // volatileTurnContext = "web-prefix" but the message content already has it
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "web-prefix\n\nquery"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        volatileTurnContext = "web-prefix",
                    ),
            )

            assertEquals(1, capturedContentsList.size, "CSPC-GOOGLE-03a turn 1: should have 1 contentsToSend capture")
            val contents1 = capturedContentsList[0]
            // The last Content (the live user message) should have the pre-merged text
            val lastContent1Text = contents1.last().extractFirstTextPart()
            assertEquals(
                "web-prefix\n\nquery",
                lastContent1Text,
                "CSPC-GOOGLE-03a turn 1: last Content text Part must be 'web-prefix\\n\\nquery'",
            )
            assertFalse(
                lastContent1Text.startsWith("web-prefix\n\nweb-prefix"),
                "CSPC-GOOGLE-03a turn 1: text must NOT start with 'web-prefix\\n\\nweb-prefix' (no double-prepend)",
            )

            // Turn 2: sessionMessages include replayed turn 1 + new user
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "web-prefix\n\nquery"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "answer"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "follow-up"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        volatileTurnContext = null,
                    ),
            )

            assertEquals(2, capturedContentsList.size, "CSPC-GOOGLE-03a: should have 2 captures total")
            val contents2 = capturedContentsList[1]

            val lastContent2Text = contents2.last().extractFirstTextPart()
            assertEquals(
                "follow-up",
                lastContent2Text,
                "CSPC-GOOGLE-03a turn 2: last Content text Part must be 'follow-up'",
            )

            val firstContent2Text = contents2.first().extractFirstTextPart()
            assertEquals(
                "web-prefix\n\nquery",
                firstContent2Text,
                "CSPC-GOOGLE-03a turn 2: first Content text Part must be 'web-prefix\\n\\nquery' (stable replay)",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-GOOGLE-05 — Google: kNN memory prefix not double-applied
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_GOOGLE_05_google_knn_memory_prefix_not_double_applied() =
        runTest {
            val capturedContentsList = mutableListOf<List<Content>>()
            val service = createGoogleService(capturedContentsList)

            val memoryPrefix = "# RELEVANT FACTS & CONTEXT:\n- fact-a"
            val expectedContent = "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"

            // Turn 1
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = expectedContent),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        volatileTurnContext = memoryPrefix,
                    ),
            )

            val contents1 = capturedContentsList[0]
            val lastContent1Text = contents1.last().extractFirstTextPart()
            assertEquals(
                expectedContent,
                lastContent1Text,
                "CSPC-GOOGLE-05 turn 1: last Content text Part must equal '$expectedContent'",
            )

            // Turn 2
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = expectedContent),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "ok"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "more"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        volatileTurnContext = null,
                    ),
            )

            val contents2 = capturedContentsList[1]
            val firstContent2Text = contents2.first().extractFirstTextPart()
            assertEquals(
                expectedContent,
                firstContent2Text,
                "CSPC-GOOGLE-05 turn 2: contentsToSend[0] text Part must equal '$expectedContent'",
            )
        }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun createGoogleService(capturedContentsList: MutableList<List<Content>>): GoogleInferenceServiceImpl {
        val sdkClient = mockk<GoogleGenAiSdkClient>()

        coEvery {
            sdkClient.generateContentStream(
                modelId = any(),
                contents = any(),
                config = any(),
                allowToolCall = any(),
                emitEvent = any(),
            )
        } answers {
            capturedContentsList.add(secondArg())
            GoogleSdkResult(
                emittedAny = true,
                functionCall = null,
                assistantContent = null,
                finishReason = "STOP",
            )
        }

        coEvery { sdkClient.createCachedContent(any(), any(), any(), any(), any()) } returns "cachedContents/test-cache"
        coEvery { sdkClient.deleteCachedContent(any()) } returns Unit

        return GoogleInferenceServiceImpl(
            sdkClient = sdkClient,
            modelId = "gemini-2.5-flash",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
        )
    }

    private fun Content.extractFirstTextPart(): String =
        parts()
            .orElse(emptyList())
            .firstOrNull()
            ?.text()
            ?.orElse("") ?: ""
}
