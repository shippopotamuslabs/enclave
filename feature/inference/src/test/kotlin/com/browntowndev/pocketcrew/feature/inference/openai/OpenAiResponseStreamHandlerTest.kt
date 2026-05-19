package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.openai.models.responses.ResponseCompletedEvent
import com.openai.models.responses.ResponseErrorEvent
import com.openai.models.responses.ResponseFailedEvent
import com.openai.models.responses.ResponseIncompleteEvent
import com.openai.models.responses.ResponseTextDeltaEvent
import com.openai.models.responses.ResponseTextDoneEvent
import com.openai.models.responses.ResponseReasoningTextDeltaEvent
import com.openai.models.responses.ResponseReasoningTextDoneEvent
import com.openai.models.responses.ResponseRefusalDeltaEvent
import com.openai.models.responses.ResponseRefusalDoneEvent
import com.openai.models.responses.ResponseStreamEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class OpenAiResponseStreamHandlerTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val emittedEvents = mutableListOf<InferenceEvent>()
    private val emitEvent: suspend (InferenceEvent) -> Unit = { emittedEvents.add(it) }

    @org.junit.Before
    fun clearEmittedEvents() {
        emittedEvents.clear()
    }

    private fun createHandler(
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
        assistantMessageId: MessageId? = null,
    ) = OpenAiResponseStreamHandler(
        provider = "TEST",
        modelId = "test-model",
        modelType = ModelType.THINKING,
        loggingPort = loggingPort,
        tag = "TestHandler",
        chatId = chatId,
        userMessageId = userMessageId,
        assistantMessageId = assistantMessageId,
        emitEvent = emitEvent,
    )

    // ── novelStreamSuffix ────────────────────────────────────────────────────

    @Test
    fun `novelStreamSuffix returns full text when no incremental chunks were emitted`() {
        assertEquals("full response", novelStreamSuffix(streamedText = "", finalizedText = "full response"))
    }

    @Test
    fun `novelStreamSuffix returns only missing suffix when done event follows deltas`() {
        assertEquals(" world", novelStreamSuffix(streamedText = "Hello", finalizedText = "Hello world"))
    }

    @Test
    fun `novelStreamSuffix avoids duplicate emission when done text matches streamed text`() {
        assertEquals("", novelStreamSuffix(streamedText = "Hello world", finalizedText = "Hello world"))
    }

    @Test
    fun `novelStreamSuffix returns empty string when finalized text is empty`() {
        assertEquals("", novelStreamSuffix(streamedText = "some text", finalizedText = ""))
    }

    @Test
    fun `novelStreamSuffix handles partial prefix overlap`() {
        assertEquals("xyz", novelStreamSuffix(streamedText = "ab", finalizedText = "abxyz"))
    }

    // ── handleStreamTermination ──────────────────────────────────────────────

    @Test
    fun `handleStreamTermination returns state when recovery is possible`() {
        val handler = createHandler()
        val state = StreamState(emittedAny = true)
        val result = handler.handleStreamTermination(state, "stream ended unexpectedly", "checking next event")
        assertNotNull(result)
        assertEquals(state, result)
    }

    @Test
    fun `handleStreamTermination returns null when nothing was emitted`() {
        val handler = createHandler()
        val state = StreamState(emittedAny = false)
        val result = handler.handleStreamTermination(state, "stream ended unexpectedly", "checking next event")
        assertNull(result)
    }

    @Test
    fun `handleStreamTermination returns null for non-recoverable error`() {
        val handler = createHandler()
        val state = StreamState(emittedAny = true)
        val result = handler.handleStreamTermination(state, "connection refused", "reading next event")
        assertNull(result)
    }

    // ── toStreamedResponse ────────────────────────────────────────────────────

    @Test
    fun `toStreamedResponse maps all state fields correctly`() {
        val handler = createHandler()
        val state =
            StreamState(
                emittedAny = true,
            )
        state.toolCallRequests +=
            ToolCallRequest(
                toolName = "search",
                argumentsJson = """{"q":"test"}""",
                provider = "TEST",
                modelType = ModelType.THINKING,
                chatId = null,
                userMessageId = null,
            )
        state.responseId = "resp_123"
        state.providerToolCallIds += "call_456"
        state.providerToolItemIds += "item_789"
        state.streamedAssistantMessage.append("Hello world")

        val result = handler.toStreamedResponse(state)
        assertTrue(result.emittedAny)
        assertEquals(1, result.functionCalls.size)
        assertEquals("search", result.functionCalls[0].toolName)
        assertEquals("resp_123", result.responseId)
        assertEquals(listOf("call_456"), result.providerToolCallIds)
        assertEquals(listOf("item_789"), result.providerToolItemIds)
        assertEquals("Hello world", result.assistantMessageText)
    }

    @Test
    fun `toStreamedResponse maps empty state to default values`() {
        val handler = createHandler()
        val state = StreamState()

        val result = handler.toStreamedResponse(state)
        assertFalse(result.emittedAny)
        assertTrue(result.functionCalls.isEmpty())
        assertNull(result.responseId)
        assertTrue(result.providerToolCallIds.isEmpty())
        assertTrue(result.providerToolItemIds.isEmpty())
        assertEquals("", result.assistantMessageText)
    }

    // ── handleEvent: output text ─────────────────────────────────────────────

    @Test
    fun `handleEvent emits partial response for output text delta`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val delta = mockk<ResponseTextDeltaEvent>()
            every { delta.delta() } returns "Hello"
            every { delta.itemId() } returns "item_1"
            every { delta.outputIndex() } returns 0L
            every { delta.contentIndex() } returns 0L
            every { event.isOutputTextDelta() } returns true
            every { event.isOutputTextDone() } returns false
            every { event.outputTextDelta() } returns Optional.of(delta)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val partial = emittedEvents.single() as InferenceEvent.PartialResponse
            assertEquals("Hello", partial.chunk)
            assertTrue(state.emittedAny)
            assertEquals("Hello", state.streamedAssistantMessage.toString())
            assertEquals(1, state.outputTextDeltaCount)
        }

    @Test
    fun `handleEvent emits fallback partial for output text done with novel suffix`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val key = "item_1:0:0"
            state.outputTextByPart[key] = StringBuilder("Hello")
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val done = mockk<ResponseTextDoneEvent>()
            every { done.text() } returns "Hello world"
            every { done.itemId() } returns "item_1"
            every { done.outputIndex() } returns 0L
            every { done.contentIndex() } returns 0L
            every { event.isOutputTextDelta() } returns false
            every { event.isOutputTextDone() } returns true
            every { event.outputTextDone() } returns Optional.of(done)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val partial = emittedEvents.single() as InferenceEvent.PartialResponse
            assertEquals(" world", partial.chunk)
            assertEquals(" world", state.streamedAssistantMessage.toString())
        }

    // ── handleEvent: reasoning ───────────────────────────────────────────────

    @Test
    fun `handleEvent emits thinking for reasoning text delta`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val delta = mockk<ResponseReasoningTextDeltaEvent>()
            every { delta.delta() } returns "think"
            every { delta.itemId() } returns "item_r"
            every { delta.outputIndex() } returns 0L
            every { delta.contentIndex() } returns 0L
            every { event.isReasoningTextDelta() } returns true
            every { event.reasoningTextDelta() } returns Optional.of(delta)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val thinking = emittedEvents.single() as InferenceEvent.Thinking
            assertEquals("think", thinking.chunk)
            assertEquals(1, state.reasoningTextDeltaCount)
        }

    @Test
    fun `handleEvent emits thinking fallback for reasoning text done`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val key = "item_r:0:0"
            state.reasoningTextByPart[key] = StringBuilder("think")
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val done = mockk<ResponseReasoningTextDoneEvent>()
            every { done.text() } returns "thinking more"
            every { done.itemId() } returns "item_r"
            every { done.outputIndex() } returns 0L
            every { done.contentIndex() } returns 0L
            every { event.isReasoningTextDone() } returns true
            every { event.reasoningTextDone() } returns Optional.of(done)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val thinking = emittedEvents.single() as InferenceEvent.Thinking
            assertEquals("ing more", thinking.chunk)
        }

    // ── handleEvent: refusal ─────────────────────────────────────────────────

    @Test
    fun `handleEvent emits partial response for refusal delta`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val refusalDelta = mockk<ResponseRefusalDeltaEvent>()
            every { refusalDelta.delta() } returns "No."
            every { refusalDelta.itemId() } returns "item_ref"
            every { refusalDelta.outputIndex() } returns 0L
            every { refusalDelta.contentIndex() } returns 0L
            every { event.isRefusalDelta() } returns true
            every { event.refusalDelta() } returns Optional.of(refusalDelta)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val partial = emittedEvents.single() as InferenceEvent.PartialResponse
            assertEquals("No.", partial.chunk)
            assertTrue(state.emittedAny)
            assertEquals("No.", state.streamedAssistantMessage.toString())
        }

    @Test
    fun `handleEvent emits fallback partial for refusal done`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val key = "item_ref:0:0"
            state.refusalTextByPart[key] = StringBuilder("No")
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val done = mockk<ResponseRefusalDoneEvent>()
            every { done.refusal() } returns "No thanks"
            every { done.itemId() } returns "item_ref"
            every { done.outputIndex() } returns 0L
            every { done.contentIndex() } returns 0L
            every { event.isRefusalDone() } returns true
            every { event.refusalDone() } returns Optional.of(done)

            handler.handleEvent(event, state)

            assertEquals(1, emittedEvents.size)
            val partial = emittedEvents.single() as InferenceEvent.PartialResponse
            assertEquals(" thanks", partial.chunk)
        }

    // ── handleEvent: lifecycle ───────────────────────────────────────────────

    @Test
    fun `handleEvent sets response id on completed`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val completed = mockk<ResponseCompletedEvent>()
            val response = mockk<com.openai.models.responses.Response>()
            every { response.id() } returns "resp_done"
            every { completed.response() } returns response
            every { event.isCompleted() } returns true
            every { event.completed() } returns Optional.of(completed)

            handler.handleEvent(event, state)

            assertEquals("resp_done", state.responseId)
            verify { loggingPort.debug("TestHandler", any()) }
        }

    @Test
    fun `handleEvent sets response id on incomplete`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val incomplete = mockk<ResponseIncompleteEvent>()
            val response = mockk<com.openai.models.responses.Response>()
            every { response.id() } returns "resp_partial"
            every { response.incompleteDetails() } returns Optional.empty()
            every { incomplete.response() } returns response
            every { event.isIncomplete() } returns true
            every { event.incomplete() } returns Optional.of(incomplete)

            handler.handleEvent(event, state)

            assertEquals("resp_partial", state.responseId)
        }

    @Test
    fun `handleEvent throws on failed when not recoverable`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val failed = mockk<ResponseFailedEvent>()
            val response = mockk<com.openai.models.responses.Response>()
            every { response.error() } returns Optional.of(mockk { every { message() } returns "fatal" })
            every { failed.response() } returns response
            every { event.isFailed() } returns true
            every { event.failed() } returns Optional.of(failed)

            assertFailsWith<RuntimeException> {
                handler.handleEvent(event, state)
            }
        }

    @Test
    fun `handleEvent recovers from failed after partial output`() =
        runTest {
            val handler = createHandler()
            val state = StreamState(emittedAny = true)
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val failed = mockk<ResponseFailedEvent>()
            val response = mockk<com.openai.models.responses.Response>()
            every { response.error() } returns
                Optional.of(mockk { every { message() } returns "stream ended unexpectedly" })
            every { failed.response() } returns response
            every { event.isFailed() } returns true
            every { event.failed() } returns Optional.of(failed)

            val result = handler.handleEvent(event, state)

            assertEquals(state, result)
            verify { loggingPort.warning("TestHandler", any()) }
        }

    @Test
    fun `handleEvent throws on stream error when not recoverable`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val errorEvent = mockk<ResponseErrorEvent>()
            every { errorEvent.message() } returns "broken pipe"
            every { event.isError() } returns true
            every { event.error() } returns Optional.of(errorEvent)

            assertFailsWith<RuntimeException> {
                handler.handleEvent(event, state)
            }
        }

    @Test
    fun `handleEvent ignores created lifecycle event without logging`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            every { event.isCreated() } returns true

            handler.handleEvent(event, state)

            verify(exactly = 0) { loggingPort.debug(any(), any()) }
            verify(exactly = 0) { loggingPort.warning(any(), any()) }
            assertTrue(emittedEvents.isEmpty())
        }

    @Test
    fun `handleEvent logs warning for unrecognized event`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            every { event.toString() } returns "UnknownEventPayload"

            handler.handleEvent(event, state)

            verify { loggingPort.warning("TestHandler", match { it.contains("unrecognized event") }) }
        }

    // ── handleEvent: tool calls ──────────────────────────────────────────────

    @Test
    fun `multiple function_call_arguments_done events accumulate both tool calls`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()

            val firstEvent = mockk<ResponseStreamEvent>(relaxed = true)
            val firstCallDone =
                mockFunctionCallArgumentsDoneEvent(
                    itemId = "call_1",
                    name = "web_search",
                    arguments = """{"query":"first search"}""",
                )
            every { firstEvent.isFunctionCallArgumentsDone() } returns true
            every { firstEvent.functionCallArgumentsDone() } returns Optional.of(firstCallDone)

            handler.handleEvent(firstEvent, state)
            assertEquals("web_search", state.toolCallRequests.firstOrNull()?.toolName)

            val secondEvent = mockk<ResponseStreamEvent>(relaxed = true)
            val secondCallDone =
                mockFunctionCallArgumentsDoneEvent(
                    itemId = "call_2",
                    name = "attached_image_inspect",
                    arguments = """{"question":"describe this image"}""",
                )
            every { secondEvent.isFunctionCallArgumentsDone() } returns true
            every { secondEvent.functionCallArgumentsDone() } returns Optional.of(secondCallDone)

            handler.handleEvent(secondEvent, state)

            assertEquals("web_search", state.toolCallRequests[0].toolName)
            assertEquals("attached_image_inspect", state.toolCallRequests[1].toolName)
            assertEquals(listOf("call_1", "call_2"), state.providerToolCallIds)
            assertEquals(listOf("call_1", "call_2"), state.providerToolItemIds)
        }

    @Test
    fun `handleEvent dispatches function_call_arguments_done and captures tool call`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()

            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val functionCallDone =
                mockFunctionCallArgumentsDoneEvent(
                    itemId = "fc_1",
                    name = "web_search",
                    arguments = """{"query":"test"}""",
                )
            every { event.isFunctionCallArgumentsDone() } returns true
            every { event.functionCallArgumentsDone() } returns Optional.of(functionCallDone)

            handler.handleEvent(event, state)
            assertNotNull(state.toolCallRequests.firstOrNull())
            assertEquals("web_search", state.toolCallRequests.firstOrNull()?.toolName)
        }

    @Test
    fun `handleEvent ignores function call arguments delta without logging`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()
            val event = mockk<ResponseStreamEvent>(relaxed = true)
            every { event.isFunctionCallArgumentsDelta() } returns true

            handler.handleEvent(event, state)

            verify(exactly = 0) { loggingPort.debug(any(), any()) }
            verify(exactly = 0) { loggingPort.warning(any(), any()) }
            assertTrue(emittedEvents.isEmpty())
        }

    @Test
    fun `handleEvent captures tool call regardless of tool call configuration`() =
        runTest {
            val handler = createHandler()
            val state = StreamState()

            val event = mockk<ResponseStreamEvent>(relaxed = true)
            val functionCallDone =
                mockFunctionCallArgumentsDoneEvent(
                    itemId = "call_1",
                    name = "web_search",
                    arguments = """{"query":"test"}""",
                )
            every { event.isFunctionCallArgumentsDone() } returns true
            every { event.functionCallArgumentsDone() } returns Optional.of(functionCallDone)

            handler.handleEvent(event, state)
            assertEquals(1, state.toolCallRequests.size)
            assertEquals("web_search", state.toolCallRequests[0].toolName)
        }

    private fun mockFunctionCallArgumentsDoneEvent(
        itemId: String,
        name: String,
        arguments: String,
    ): com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent {
        val functionCallDone = mockk<com.openai.models.responses.ResponseFunctionCallArgumentsDoneEvent>()
        every { functionCallDone.itemId() } returns itemId
        every { functionCallDone.name() } returns name
        every { functionCallDone.arguments() } returns arguments
        return functionCallDone
    }
}
