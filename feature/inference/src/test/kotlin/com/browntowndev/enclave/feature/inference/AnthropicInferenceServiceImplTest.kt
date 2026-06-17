package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextDelta
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional
import java.util.stream.Stream

class AnthropicInferenceServiceImplTest {
    private val client = mockk<AnthropicClient>()
    private val messageService = mockk<MessageService>()
    private val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `sendPrompt is not supported on direct Anthropic service`() {
        val service = createService()
        assertThrows<IllegalStateException> {
            service.sendPrompt("Hello", closeConversation = false)
        }
    }

    @Test
    fun `completeSingleTurn throws InferenceNetworkException when client throws IOException`() =
        runTest {
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } throws java.io.IOException("Socket timeout")

            val service = createService()

            val exception =
                assertThrows<InferenceNetworkException> {
                    service.completeSingleTurn(
                        sessionMessages = listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
                        systemPrompt = "",
                        contextSidecars = null,
                        availableTools = emptyList(),
                        allowToolCall = false,
                        onTextToken = {},
                        onThinkingToken = {},
                        options = GenerationOptions(reasoningBudget = 0),
                    )
                }

            assertEquals("ANTHROPIC", exception.provider)
            assertEquals("[ANTHROPIC] Network error: Socket timeout", exception.message)
            assertTrue(exception.cause is java.io.IOException)
        }

    @Test
    fun `completeSingleTurn throws when stream ends without stop reason after partial text`() =
        runTest {
            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "partial"

            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()

            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(textEvent)
            every { streamResponse.close() } returns Unit

            val service = createService()

            val error =
                assertThrows<RuntimeException> {
                    service.completeSingleTurn(
                        sessionMessages = listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
                        systemPrompt = "",
                        contextSidecars = null,
                        availableTools = emptyList(),
                        allowToolCall = false,
                        onTextToken = {},
                        onThinkingToken = {},
                        options = GenerationOptions(reasoningBudget = 0),
                    )
                }
            assertTrue(error.message!!.contains("stream ended unexpectedly without finish signal"))
        }

    @Test
    fun `completeSingleTurn maps agent tool budget sidecar to user infrastructure not assistant`() =
        runTest {
            val paramsSlot = slot<MessageCreateParams>()
            every { client.messages() } returns messageService
            every { messageService.createStreaming(capture(paramsSlot)) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(anthropicMessageStopEvent())
            every { streamResponse.close() } returns Unit

            val service = createService()
            val budgetContent =
                "<system_context>\nTool budget: 3/50 used (47 remaining). Tools enabled.\n</system_context>"

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(
                            id = "budget",
                            role = Role.ASSISTANT,
                            content = budgetContent,
                            metadataJson = """{"agent_tool_budget":true}""",
                        ),
                    ),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = paramsSlot.captured.messages()
            assertEquals(2, messages.size)
            assertEquals(MessageParam.Role.USER, messages[0].role())
            assertEquals(MessageParam.Role.USER, messages[1].role())
            val budgetWireText =
                messages[1]
                    .content()
                    .asBlockParams()
                    .single()
                    .asText()
                    .text()
            assertEquals(budgetContent, budgetWireText)
            assertEquals(false, budgetWireText.contains(""""_agent""""))
        }

    @Test
    fun `completeSingleTurn clamps thinking budget below max tokens`() =
        runTest {
            val paramsSlot = slot<MessageCreateParams>()
            every { client.messages() } returns messageService
            every { messageService.createStreaming(capture(paramsSlot)) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(anthropicMessageStopEvent())
            every { streamResponse.close() } returns Unit

            val service = createService()

            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options =
                    GenerationOptions(
                        reasoningBudget = 8000,
                        reasoningEffort = ApiReasoningEffort.HIGH,
                        maxTokens = 4096,
                    ),
            )

            val params = paramsSlot.captured
            assertTrue(params.thinking().isPresent)
            assertTrue(params.maxTokens() > params.thinking().get().asEnabled().budgetTokens())
        }

    private fun createService(): AnthropicInferenceServiceImpl =
        AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
        )

    private fun mockStreamEvent(
        contentBlockDelta: Optional<RawContentBlockDeltaEvent> = Optional.empty(),
    ): RawMessageStreamEvent {
        val event = mockk<RawMessageStreamEvent>()
        every { event.contentBlockDelta() } returns contentBlockDelta
        every { event.messageStart() } returns Optional.empty()
        every { event.messageDelta() } returns Optional.empty()
        every { event.messageStop() } returns Optional.empty()
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockStop() } returns Optional.empty()
        return event
    }
}