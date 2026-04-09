package com.browntowndev.pocketcrew.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
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
import com.anthropic.models.messages.ThinkingDelta
import com.anthropic.services.blocking.MessageService
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
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
        every { textBlockDelta.thinking() } returns Optional.empty()

        val thinkingBlockDelta = mockk<RawContentBlockDelta>()
        every { thinkingBlockDelta.text() } returns Optional.empty()
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

        val thinkingContentBlock = mockk<ContentBlock>()
        every { thinkingContentBlock.text() } returns Optional.empty()
        every { thinkingContentBlock.thinking() } returns Optional.of(thinkingBlock)

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
}
