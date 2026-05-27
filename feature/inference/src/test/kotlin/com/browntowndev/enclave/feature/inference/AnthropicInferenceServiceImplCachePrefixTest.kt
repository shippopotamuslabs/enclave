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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.stream.Stream

class AnthropicInferenceServiceImplCachePrefixTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `completeSingleTurn message prefix is stable and breakpoints are placed correctly across turns for Anthropic`() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
            val capturedParams = mutableListOf<MessageCreateParams>()

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
                capturedParams.add(firstArg<MessageCreateParams>())
                streamResponse
            }
            every { streamResponse.stream() } answers {
                Stream.of(mockk(relaxed = true))
            }
            every { streamResponse.close() } returns Unit

            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            // Turn 1: System prompt + User message (U1) + Assistant response.
            // Under stableHistoryTailIndex = size - 2.
            // If we have [u1, a1], the stable history tail is at index 0 (which is u1).
            val turn1Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
                )

            service.completeSingleTurn(
                sessionMessages = turn1Messages,
                systemPrompt = "System Instruction",
                contextSidecars = "Volatile Sidecar 1",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            // Turn 2: Turn 1 is now in the history.
            // [u1, a1, u2, a2]. Stable history tail is index 2 (u2).
            val turn2Messages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello Turn 1"),
                    AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Reply Turn 1"),
                    AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello Turn 2"),
                    AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Reply Turn 2"),
                )

            service.completeSingleTurn(
                sessionMessages = turn2Messages,
                systemPrompt = "System Instruction",
                contextSidecars = "Volatile Sidecar 2",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(2, capturedParams.size)
            val params1 = capturedParams[0]
            val params2 = capturedParams[1]

            // 1. Verify system prompt cache breakpoints
            val sys1 = params1.system().orElse(null)
            val sys2 = params2.system().orElse(null)
            assertNotNull(sys1)
            assertNotNull(sys2)
            assertTrue(
                sys1.isTextBlockParams() &&
                    sys1
                        .asTextBlockParams()
                        .single()
                        .cacheControl()
                        .isPresent,
            )
            assertTrue(
                sys2.isTextBlockParams() &&
                    sys2
                        .asTextBlockParams()
                        .single()
                        .cacheControl()
                        .isPresent,
            )

            // 2. Verify messages prefix stability
            val msgs1 = params1.messages()
            val msgs2 = params2.messages()

            // Turn 1 messages assertions: U1 is the live user message, so it has sidecar prepended
            assertEquals(2, msgs1.size)
            assertEquals(
                "Volatile Sidecar 1\n\nHello Turn 1",
                msgs1[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )
            assertEquals(
                "Reply Turn 1",
                msgs1[1]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )

            // U1 has cache control breakpoint since stable history tail is index 0
            assertTrue(
                msgs1[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "U1 should have cache breakpoint in Turn 1",
            )
            assertFalse(
                msgs1[1]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "A1 should not have cache breakpoint in Turn 1",
            )

            // Turn 2 messages assertions: U1 is now history (raw text), U2 is live user message (has sidecar 2)
            assertEquals(4, msgs2.size)
            assertEquals(
                "Hello Turn 1",
                msgs2[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )
            assertEquals(
                "Reply Turn 1",
                msgs2[1]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )
            assertEquals(
                "Volatile Sidecar 2\n\nHello Turn 2",
                msgs2[2]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )
            assertEquals(
                "Reply Turn 2",
                msgs2[3]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text(),
            )

            // Breakpoint placement in Turn 2: stable history tail is index 2 (U2)
            assertFalse(
                msgs2[0]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "U1 should not have cache breakpoint in Turn 2",
            )
            assertFalse(
                msgs2[1]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "A1 should not have cache breakpoint in Turn 2",
            )
            assertTrue(
                msgs2[2]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "U2 should have cache breakpoint in Turn 2",
            )
            assertFalse(
                msgs2[3]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .cacheControl()
                    .isPresent,
                "A2 should not have cache breakpoint in Turn 2",
            )
        }
}
