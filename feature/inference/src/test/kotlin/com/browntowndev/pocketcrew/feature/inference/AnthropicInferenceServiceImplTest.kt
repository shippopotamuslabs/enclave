package com.browntowndev.pocketcrew.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.InputJsonDelta
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawContentBlockStartEvent
import com.anthropic.models.messages.RawContentBlockStartEvent.ContentBlock
import com.anthropic.models.messages.RawContentBlockStopEvent
import com.anthropic.models.messages.RawMessageDeltaEvent
import com.anthropic.models.messages.RawMessageStartEvent
import com.anthropic.models.messages.RawMessageStopEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextDelta
import com.anthropic.models.messages.TextBlock
import com.anthropic.models.messages.ThinkingDelta
import com.anthropic.models.messages.ToolUseBlock
import com.anthropic.services.blocking.MessageService
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import java.util.stream.Stream
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicInferenceServiceImplTest {

    private val client = mockk<AnthropicClient>()
    private val messageService = mockk<MessageService>()
    private val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `sendPrompt streams text thinking and finished events`() = runTest {
        val textDelta = mockk<TextDelta>()
        every { textDelta.text() } returns "answer"

        val thinkingDelta = mockk<ThinkingDelta>()
        every { thinkingDelta.thinking() } returns "reasoning"

        val textBlockDelta = mockk<RawContentBlockDelta>()
        every { textBlockDelta.text() } returns Optional.of(textDelta)
        every { textBlockDelta.inputJson() } returns Optional.empty()
        every { textBlockDelta.thinking() } returns Optional.empty()

        val thinkingBlockDelta = mockk<RawContentBlockDelta>()
        every { thinkingBlockDelta.text() } returns Optional.empty()
        every { thinkingBlockDelta.inputJson() } returns Optional.empty()
        every { thinkingBlockDelta.thinking() } returns Optional.of(thinkingDelta)

        val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { textDeltaEvent.delta() } returns textBlockDelta

        val thinkingDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { thinkingDeltaEvent.delta() } returns thinkingBlockDelta

        val textEvent = mockStreamEvent(contentBlockDelta = Optional.of(textDeltaEvent))
        val thinkingEvent = mockStreamEvent(contentBlockDelta = Optional.of(thinkingDeltaEvent))
        val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns Stream.of(textEvent, thinkingEvent, stopEvent)
        every { streamResponse.close() } returns Unit

        val service = AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
        )

        val events = service.sendPrompt("Hello", closeConversation = false).toList()

        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "answer" })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "reasoning" })
        assertTrue(events.any { it is InferenceEvent.Finished })
        verify(exactly = 1) { messageService.createStreaming(any<MessageCreateParams>()) }
        verify(exactly = 1) { streamResponse.close() }
    }

    @Test
    fun `sendPrompt emits text and thinking from content block start events`() = runTest {
        val textBlock = mockk<com.anthropic.models.messages.TextBlock>()
        every { textBlock.text() } returns "start answer"

        val thinkingBlock = mockk<com.anthropic.models.messages.ThinkingBlock>()
        every { thinkingBlock.thinking() } returns "start reasoning"

        val textContentBlock = mockk<ContentBlock>()
        every { textContentBlock.text() } returns Optional.of(textBlock)
        every { textContentBlock.thinking() } returns Optional.empty()
        every { textContentBlock.isToolUse() } returns false

        val thinkingContentBlock = mockk<ContentBlock>()
        every { thinkingContentBlock.text() } returns Optional.empty()
        every { thinkingContentBlock.thinking() } returns Optional.of(thinkingBlock)
        every { thinkingContentBlock.isToolUse() } returns false

        val textStartEvent = mockk<RawContentBlockStartEvent>()
        every { textStartEvent.contentBlock() } returns textContentBlock

        val thinkingStartEvent = mockk<RawContentBlockStartEvent>()
        every { thinkingStartEvent.contentBlock() } returns thinkingContentBlock

        val textEvent = mockStreamEvent(contentBlockStart = Optional.of(textStartEvent))
        val thinkingEvent = mockStreamEvent(contentBlockStart = Optional.of(thinkingStartEvent))
        val stopEvent = mockStreamEvent(messageStop = Optional.of(mockk()))

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns Stream.of(textEvent, thinkingEvent, stopEvent)
        every { streamResponse.close() } returns Unit

        val service = AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
        )

        val events = service.sendPrompt("Hello", closeConversation = false).toList()

        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "start answer" })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "start reasoning" })
        assertTrue(events.any { it is InferenceEvent.Finished })
    }

    @Test
    fun `sendPrompt emits finished when stream ends without stop event`() = runTest {
        val textDelta = mockk<TextDelta>()
        every { textDelta.text() } returns "answer"

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

        val service = AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
        )

        val events = service.sendPrompt("Hello", closeConversation = true).toList()

        assertEquals(2, events.size)
        assertTrue(events.last() is InferenceEvent.Finished)
        verify(exactly = 1) { messageService.createStreaming(any<MessageCreateParams>()) }
    }

    @Test
    fun `sendPrompt completes one tool_use and tool_result round trip when search is enabled`() = runTest {
        val toolExecutor = mockk<ToolExecutorPort>()
        val initialStreamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val followUpStreamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val capturedParams = mutableListOf<MessageCreateParams>()
        val followUpThinkingDelta = mockk<ThinkingDelta>()
        every { followUpThinkingDelta.thinking() } returns "Need to inspect the search result."
        val followUpThinkingBlockDelta = mockk<RawContentBlockDelta>()
        every { followUpThinkingBlockDelta.text() } returns Optional.empty()
        every { followUpThinkingBlockDelta.inputJson() } returns Optional.empty()
        every { followUpThinkingBlockDelta.thinking() } returns Optional.of(followUpThinkingDelta)
        val followUpThinkingDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { followUpThinkingDeltaEvent.delta() } returns followUpThinkingBlockDelta
        every { followUpThinkingDeltaEvent.index() } returns 0L

        val followUpTextDelta = mockk<TextDelta>()
        every { followUpTextDelta.text() } returns "Use the search result summary."
        val followUpTextBlockDelta = mockk<RawContentBlockDelta>()
        every { followUpTextBlockDelta.text() } returns Optional.of(followUpTextDelta)
        every { followUpTextBlockDelta.inputJson() } returns Optional.empty()
        every { followUpTextBlockDelta.thinking() } returns Optional.empty()
        val followUpTextDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { followUpTextDeltaEvent.delta() } returns followUpTextBlockDelta
        every { followUpTextDeltaEvent.index() } returns 0L

        every { client.messages() } returns messageService
        every { messageService.createStreaming(capture(capturedParams)) } returnsMany listOf(
            initialStreamResponse,
            followUpStreamResponse,
        )
        every { initialStreamResponse.stream() } returns Stream.of(
            mockThinkingStartEvent("Need to search first."),
            mockToolUseStartEvent(toolInput = JsonValue.from(emptyMap<String, Any>())),
            mockInputJsonDeltaEvent("""{"query":"latest android tool calling"}"""),
            mockStreamEvent(messageStop = Optional.of(mockk())),
        )
        every { initialStreamResponse.close() } returns Unit
        every { followUpStreamResponse.stream() } returns Stream.of(
            mockStreamEvent(contentBlockDelta = Optional.of(followUpThinkingDeltaEvent)),
            mockStreamEvent(contentBlockDelta = Optional.of(followUpTextDeltaEvent)),
            mockStreamEvent(messageStop = Optional.of(mockk())),
        )
        every { followUpStreamResponse.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )
        val service = AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "Find recent Android agent news",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to search first." })
        assertTrue(events.any { it is InferenceEvent.Thinking && it.chunk == "Need to inspect the search result." })
        assertTrue(events.any { it is InferenceEvent.PartialResponse && it.chunk == "Use the search result summary." })
        assertTrue(events.any { it is InferenceEvent.Finished && it.modelType == ModelType.FAST })
        assertTrue(capturedParams[1].toString().contains("latest android tool calling"))
        coVerify(exactly = 1) {
            toolExecutor.execute(withArg { request ->
                assertEquals("""{"query":"latest android tool calling"}""", request.argumentsJson)
            })
        }
        verify(exactly = 2) { messageService.createStreaming(any<MessageCreateParams>()) }
    }

    @Test
    fun `sendPrompt rejects recursive second anthropic tool request`() = runTest {
        val toolExecutor = mockk<ToolExecutorPort>()
        val initialStreamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val followUpStreamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returnsMany listOf(
            initialStreamResponse,
            followUpStreamResponse,
        )
        every { initialStreamResponse.stream() } returns Stream.of(
            mockToolUseStartEvent(),
            mockStreamEvent(messageStop = Optional.of(mockk())),
        )
        every { initialStreamResponse.close() } returns Unit
        every { followUpStreamResponse.stream() } returns Stream.of(
            mockToolUseStartEvent(),
        )
        every { followUpStreamResponse.close() } returns Unit
        coEvery { toolExecutor.execute(any()) } returns ToolExecutionResult(
            toolName = "tavily_web_search",
            resultJson = """{"query":"latest android tool calling","results":[{"url":"https://example.invalid/stub"}]}""",
        )

        val service = AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolExecutor = toolExecutor,
        )

        val events = service.sendPrompt(
            prompt = "Search twice",
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            ),
            closeConversation = false,
        ).toList()

        val error = events.filterIsInstance<InferenceEvent.Error>().single()
        assertTrue(error.cause is IllegalStateException)
        assertEquals("Search skill recursion limit exceeded", error.cause.message)
    }

    private fun mockStreamEvent(
        contentBlockDelta: Optional<RawContentBlockDeltaEvent> = Optional.empty(),
        messageStart: Optional<RawMessageStartEvent> = Optional.empty(),
        messageDelta: Optional<RawMessageDeltaEvent> = Optional.empty(),
        messageStop: Optional<RawMessageStopEvent> = Optional.empty(),
        contentBlockStart: Optional<RawContentBlockStartEvent> = Optional.empty(),
        contentBlockStop: Optional<RawContentBlockStopEvent> = Optional.empty(),
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

    private fun mockToolUseMessage(): Message {
        val toolUse = mockk<ToolUseBlock>()
        every { toolUse.name() } returns "tavily_web_search"
        every { toolUse.id() } returns "toolu_1"
        every { toolUse._input() } returns JsonValue.from(mapOf("query" to "latest android tool calling"))

        val contentBlock = mockk<com.anthropic.models.messages.ContentBlock>()
        every { contentBlock.isToolUse() } returns true
        every { contentBlock.asToolUse() } returns toolUse

        val message = mockk<Message>()
        every { message.content() } returns listOf(contentBlock)
        every { message.toParam() } returns mockk(relaxed = true)
        return message
    }

    private fun mockToolUseStartEvent(): RawMessageStreamEvent {
        val toolUse = mockk<ToolUseBlock>()
        every { toolUse.name() } returns "tavily_web_search"
        every { toolUse.id() } returns "toolu_1"
        every { toolUse._input() } returns JsonValue.from(mapOf("query" to "latest android tool calling"))

        return mockToolUseStartEvent(toolUse)
    }

    private fun mockToolUseStartEvent(toolInput: JsonValue): RawMessageStreamEvent {
        val toolUse = mockk<ToolUseBlock>()
        every { toolUse.name() } returns "tavily_web_search"
        every { toolUse.id() } returns "toolu_1"
        every { toolUse._input() } returns toolInput

        return mockToolUseStartEvent(toolUse)
    }

    private fun mockToolUseStartEvent(toolUse: ToolUseBlock): RawMessageStreamEvent {
        val contentBlock = mockk<ContentBlock>()
        every { contentBlock.text() } returns Optional.empty()
        every { contentBlock.thinking() } returns Optional.empty()
        every { contentBlock.isToolUse() } returns true
        every { contentBlock.asToolUse() } returns toolUse

        val startEvent = mockk<RawContentBlockStartEvent>()
        every { startEvent.contentBlock() } returns contentBlock
        every { startEvent.index() } returns 0L

        return mockStreamEvent(contentBlockStart = Optional.of(startEvent))
    }

    private fun mockInputJsonDeltaEvent(partialJson: String): RawMessageStreamEvent {
        val inputJsonDelta = mockk<InputJsonDelta>()
        every { inputJsonDelta.partialJson() } returns partialJson

        val blockDelta = mockk<RawContentBlockDelta>()
        every { blockDelta.text() } returns Optional.empty()
        every { blockDelta.inputJson() } returns Optional.of(inputJsonDelta)
        every { blockDelta.thinking() } returns Optional.empty()

        val deltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { deltaEvent.delta() } returns blockDelta
        every { deltaEvent.index() } returns 0L

        return mockStreamEvent(contentBlockDelta = Optional.of(deltaEvent))
    }

    private fun mockThinkingStartEvent(text: String): RawMessageStreamEvent {
        val thinkingBlock = mockk<com.anthropic.models.messages.ThinkingBlock>()
        every { thinkingBlock.thinking() } returns text

        val contentBlock = mockk<ContentBlock>()
        every { contentBlock.text() } returns Optional.empty()
        every { contentBlock.thinking() } returns Optional.of(thinkingBlock)
        every { contentBlock.isToolUse() } returns false

        val startEvent = mockk<RawContentBlockStartEvent>()
        every { startEvent.contentBlock() } returns contentBlock

        return mockStreamEvent(contentBlockStart = Optional.of(startEvent))
    }

    private fun mockTextMessage(text: String): Message {
        val textBlock = mockk<TextBlock>()
        every { textBlock.text() } returns text

        val contentBlock = mockk<com.anthropic.models.messages.ContentBlock>()
        every { contentBlock.isToolUse() } returns false
        every { contentBlock.isText() } returns true
        every { contentBlock.asText() } returns textBlock

        val message = mockk<Message>()
        every { message.content() } returns listOf(contentBlock)
        return message
    }
}
