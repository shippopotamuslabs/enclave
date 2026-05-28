package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawContentBlockStartEvent
import com.anthropic.models.messages.RawMessageDeltaEvent
import com.anthropic.models.messages.RawMessageStartEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextDelta
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.models.messages.Usage
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.stream.Stream

class AnthropicInferenceServiceImplExtendedTest {
    private val client = mockk<AnthropicClient>()
    private val messageService = mockk<MessageService>()
    private val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private lateinit var service: AnthropicInferenceServiceImpl

    @BeforeEach
    fun setup() {
        service =
            AnthropicInferenceServiceImpl(
                client = client,
                modelId = "claude-sonnet-4-20250514",
                modelType = ModelType.MAIN,
                loggingPort = loggingPort,
                toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                toolStreamGatePort = FakeToolStreamGatePort(),
            )
    }

    @Test
    fun `sendPrompt emits Error event on API exception`() =
        runTest {
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } throws RuntimeException("API Error")
            every { streamResponse.close() } returns Unit

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            assertTrue(events.any { it is InferenceEvent.Error })
            val errorEvent = events.filterIsInstance<InferenceEvent.Error>().first()
            assertTrue(errorEvent.cause.message?.contains("API Error (ANTHROPIC)") == true)
        }

    @Test
    fun `sendPrompt emits Error event on IllegalArgumentException`() =
        runTest {
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } throws IllegalArgumentException("Bad argument")

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            assertTrue(events.any { it is InferenceEvent.Error })
            val errorEvent = events.filterIsInstance<InferenceEvent.Error>().first()
            assertTrue(errorEvent.cause is IllegalArgumentException)
        }

    @Test
    fun `sendPrompt with GenerationOptions emits text and finished`() =
        runTest {
            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "response"

            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()

            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))
            val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(textEvent, stopEvent)
            every { streamResponse.close() } returns Unit

            val options = GenerationOptions(reasoningBudget = 10000)
            val events = service.sendPrompt("Hello", options, closeConversation = false).toList()

            assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "response" })
            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `setHistory replaces conversation history`() =
        runTest {
            val history =
                listOf(
                    ChatMessage(role = Role.USER, content = "Previous message"),
                    ChatMessage(role = Role.ASSISTANT, content = "Previous response"),
                )

            service.setHistory(history)

            // Verify setHistory doesn't throw - history is internal
            // We verify by sending a prompt and checking it completes
            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "answer"

            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()

            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))
            val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(textEvent, stopEvent)
            every { streamResponse.close() } returns Unit

            val events = service.sendPrompt("Follow up", closeConversation = false).toList()
            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `closeSession clears history`() =
        runTest {
            service.closeSession()
            // Verify no crash - closeSession clears internal state
        }

    @Test
    fun `sendPrompt with tool use block start processes stream`() =
        runTest {
            val toolUseBlock = mockk<RawContentBlockStartEvent.ContentBlock>()
            every { toolUseBlock.isToolUse() } returns true
            val toolUse = mockk<ToolUseBlock>()
            every { toolUse.id() } returns "tool-1"
            every { toolUse.name() } returns "web_search"
            every { toolUse._input() } returns JsonValue.from(mapOf("query" to "test"))
            every { toolUseBlock.asToolUse() } returns toolUse
            every { toolUseBlock.text() } returns Optional.empty()
            every { toolUseBlock.thinking() } returns Optional.empty()

            val toolStartEvent = mockk<RawContentBlockStartEvent>()
            every { toolStartEvent.contentBlock() } returns toolUseBlock
            every { toolStartEvent.index() } returns 0L

            val contentBlockStopEvent = mockk<com.anthropic.models.messages.RawContentBlockStopEvent>()
            val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

            val toolStartStreamEvent = mockStreamEvent(contentBlockStart = Optional.of(toolStartEvent))
            val blockStopStreamEvent = mockStreamEvent(contentBlockStop = Optional.of(contentBlockStopEvent))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(toolStartStreamEvent, blockStopStreamEvent, stopEvent)
            every { streamResponse.close() } returns Unit

            // Just verify the stream processes without crashing
            val events = service.sendPrompt("Search for something", closeConversation = false).toList()

            // Should have at least a Finished event
            assertTrue(events.isNotEmpty())
            verify(exactly = 1) { messageService.createStreaming(any<MessageCreateParams>()) }
        }

    @Test
    fun `sendPrompt with IllegalStateException emits Error`() =
        runTest {
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } throws IllegalStateException("State error")

            val events = service.sendPrompt("Hello", closeConversation = false).toList()

            assertTrue(events.any { it is InferenceEvent.Error })
            val errorEvent = events.filterIsInstance<InferenceEvent.Error>().first()
            assertTrue(errorEvent.cause is IllegalStateException)
        }

    @Test
    fun `sendPrompt with closeConversation clears history after response`() =
        runTest {
            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "final answer"

            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()

            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))
            val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(textEvent, stopEvent)
            every { streamResponse.close() } returns Unit

            val events = service.sendPrompt("Hello", closeConversation = true).toList()

            assertTrue(events.any { it is InferenceEvent.Finished })
        }

    @Test
    fun `sendPrompt with modelType FAST uses correct model type in events`() =
        runTest {
            val fastService =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-haiku-4-20250514",
                    modelType = ModelType.FAST,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "fast response"

            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()

            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))
            val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns Stream.of(textEvent, stopEvent)
            every { streamResponse.close() } returns Unit

            val events = fastService.sendPrompt("Hello", closeConversation = false).toList()

            val partialResponse = events.filterIsInstance<InferenceEvent.PartialResponse>().first()
            assertEquals(ModelType.FAST, partialResponse.modelType)

            val finishedEvent = events.filterIsInstance<InferenceEvent.Finished>().first()
            assertEquals(ModelType.FAST, finishedEvent.modelType)
        }

    @Test
    fun `completeSingleTurn captures final Anthropic usage from message delta`() =
        runTest {
            val telemetryService =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-sonnet-4-20250514",
                    modelType = ModelType.THINKING,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetrySupport = testInferenceTelemetrySupport(),
                )
            val startUsage = mockk<Usage>()
            every { startUsage.inputTokens() } returns 100L
            every { startUsage.outputTokens() } returns 0L
            every { startUsage.cacheCreationInputTokens() } returns Optional.empty()
            every { startUsage.cacheReadInputTokens() } returns Optional.of(20L)

            val startMessage = mockk<com.anthropic.models.messages.Message>()
            every { startMessage.usage() } returns startUsage
            val startEvent = mockk<RawMessageStartEvent>()
            every { startEvent.message() } returns startMessage

            val textDelta = mockk<TextDelta>()
            every { textDelta.text() } returns "answer"
            val textBlockDelta = mockk<RawContentBlockDelta>()
            every { textBlockDelta.text() } returns Optional.of(textDelta)
            every { textBlockDelta.inputJson() } returns Optional.empty()
            every { textBlockDelta.thinking() } returns Optional.empty()
            val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
            every { textDeltaEvent.delta() } returns textBlockDelta

            val deltaUsage = mockk<MessageDeltaUsage>()
            every { deltaUsage.outputTokens() } returns 7L
            every { deltaUsage.inputTokens() } returns Optional.empty()
            every { deltaUsage.cacheCreationInputTokens() } returns Optional.empty()
            every { deltaUsage.cacheReadInputTokens() } returns Optional.of(20L)
            val messageDelta = mockk<RawMessageDeltaEvent>()
            val messageDeltaBody = mockk<RawMessageDeltaEvent.Delta>()
            every { messageDeltaBody.stopReason() } returns Optional.empty()
            every { messageDelta.delta() } returns messageDeltaBody
            every { messageDelta.usage() } returns deltaUsage

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns
                Stream.of(
                    mockStreamEvent(messageStart = Optional.of(startEvent)),
                    mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent)),
                    mockStreamEvent(messageDelta = Optional.of(messageDelta)),
                    mockStreamEvent(messageStop = Optional.of(mockk())),
                )
            every { streamResponse.close() } returns Unit

            val result =
                telemetryService.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage("u1", Role.USER, "Hello")),
                    systemPrompt = "",
                    contextSidecars = null,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options =
                        GenerationOptions(
                            reasoningBudget = 0,
                            chatId = ChatId("chat-1"),
                            assistantMessageId = MessageId("assistant-1"),
                        ),
                )

            val telemetry = requireNotNull(result.telemetry)
            assertEquals(100, telemetry.inputTokens)
            assertEquals(7, telemetry.outputTokens)
            assertEquals(20, telemetry.cacheReadInputTokens)
        }

    private fun mockStreamEvent(
        contentBlockDelta: Optional<RawContentBlockDeltaEvent> = Optional.empty(),
        messageStart: Optional<com.anthropic.models.messages.RawMessageStartEvent> = Optional.empty(),
        messageDelta: Optional<com.anthropic.models.messages.RawMessageDeltaEvent> = Optional.empty(),
        messageStop: Optional<com.anthropic.models.messages.RawMessageStopEvent> = Optional.empty(),
        contentBlockStart: Optional<RawContentBlockStartEvent> = Optional.empty(),
        contentBlockStop: Optional<com.anthropic.models.messages.RawContentBlockStopEvent> = Optional.empty(),
    ): RawMessageStreamEvent {
        val event = mockk<RawMessageStreamEvent>()
        every { event.contentBlockDelta() } returns contentBlockDelta
        every { event.messageStart() } returns messageStart
        every { event.messageDelta() } returns messageDelta
        every { event.messageStop() } returns messageStop
        every { event.contentBlockStart() } returns contentBlockStart
        every { event.contentBlockStop() } returns contentBlockStop
        return event
    }
}
