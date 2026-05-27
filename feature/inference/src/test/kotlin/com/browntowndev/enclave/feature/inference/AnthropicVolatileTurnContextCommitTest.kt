package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.stream.Stream

/**
 * Anthropic-specific provider coverage for volatile turn context commit correctness.
 */
class AnthropicVolatileTurnContextCommitTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    // ---------------------------------------------------------------------------
    // CSPC-ANTHRO-03a — RAG prefix is not double-prepended in Anthropic wire body
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_ANTHRO_03a_anthropic_completeSingleTurn_rag_prefix_not_double_prepended() =
        runTest {
            val capturedParams = mutableListOf<MessageCreateParams>()
            val service = createAnthropicService(capturedParams)

            // Turn 1: sessionMessages already contain the merged content (as Rust stored it)
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "web-prefix\n\noriginal query"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System",
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

            assertEquals(1, capturedParams.size, "CSPC-ANTHRO-03a turn 1: should have 1 params capture")
            val msgs1 = capturedParams[0].messages()
            assertEquals(1, msgs1.size)

            val firstUserText1 =
                msgs1[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(
                "web-prefix\n\noriginal query",
                firstUserText1,
                "CSPC-ANTHRO-03a turn 1: first USER text block must contain exactly 'web-prefix\\n\\noriginal query'",
            )
            assertFalse(
                firstUserText1.startsWith("web-prefix\n\nweb-prefix"),
                "CSPC-ANTHRO-03a turn 1: text must NOT start with 'web-prefix\\n\\nweb-prefix' (no double-prepend)",
            )

            // Turn 2: sessionMessages include replayed turn 1 (with merged content) + new user
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "web-prefix\n\noriginal query"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "answer"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "follow-up"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System",
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

            assertEquals(2, capturedParams.size, "CSPC-ANTHRO-03a: should have 2 captures total")
            val msgs2 = capturedParams[1].messages()
            assertEquals(3, msgs2.size)

            val firstUserText2 =
                msgs2[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(
                "web-prefix\n\noriginal query",
                firstUserText2,
                "CSPC-ANTHRO-03a turn 2: first USER text block must still be 'web-prefix\\n\\noriginal query' (replayed from Rust)",
            )

            val thirdUserText2 =
                msgs2[2]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(
                "follow-up",
                thirdUserText2,
                "CSPC-ANTHRO-03a turn 2: third USER text block must be exactly 'follow-up'",
            )

            // Confirm no double-prepend anywhere
            val allTexts =
                msgs2.mapNotNull { msg ->
                    runCatching {
                        msg
                            .content()
                            .asBlockParams()
                            .single()
                            .asText()
                            .text()
                    }.getOrNull()
                }
            assertFalse(
                allTexts.any { it.contains("web-prefix\n\nweb-prefix") },
                "CSPC-ANTHRO-03a: 'web-prefix\\n\\nweb-prefix' must NOT appear in any message text block",
            )
        }

    // ---------------------------------------------------------------------------
    // CSPC-ANTHRO-05 — kNN memory prefix not double-applied
    // ---------------------------------------------------------------------------

    @Test
    fun CSPC_ANTHRO_05_anthropic_knn_memory_prefix_not_double_applied() =
        runTest {
            val capturedParams = mutableListOf<MessageCreateParams>()
            val service = createAnthropicService(capturedParams)

            val memoryPrefix = "# RELEVANT FACTS & CONTEXT:\n- fact-a"
            val expectedContent = "# RELEVANT FACTS & CONTEXT:\n- fact-a\n\nhelp"

            // Turn 1
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = expectedContent),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System",
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

            val msgs1 = capturedParams[0].messages()
            val firstUserText1 =
                msgs1[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(
                expectedContent,
                firstUserText1,
                "CSPC-ANTHRO-05 turn 1: first USER text block must equal '$expectedContent'",
            )
            assertFalse(
                firstUserText1.contains("# RELEVANT FACTS & CONTEXT:\n- fact-a\n\n# RELEVANT FACTS"),
                "CSPC-ANTHRO-05 turn 1: double-prefix must NOT appear",
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
                systemPrompt = "System",
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

            val msgs2 = capturedParams[1].messages()
            val firstUserText2 =
                msgs2[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(
                expectedContent,
                firstUserText2,
                "CSPC-ANTHRO-05 turn 2: sessionMessages[0] text block must equal '$expectedContent'",
            )
        }

    // ---------------------------------------------------------------------------
    // Helper
    // ---------------------------------------------------------------------------

    private fun createAnthropicService(capturedParams: MutableList<MessageCreateParams>): AnthropicInferenceServiceImpl {
        val client = mockk<AnthropicClient>()
        val messageService = mockk<MessageService>()
        val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
            capturedParams.add(firstArg<MessageCreateParams>())
            streamResponse
        }
        every { streamResponse.stream() } answers {
            Stream.of(mockk(relaxed = true))
        }
        every { streamResponse.close() } returns Unit

        return AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-3-5-sonnet-20241022",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
        )
    }
}
