package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenAiResponseStreamHandlerTest {

    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val emittedEvents = mutableListOf<InferenceEvent>()
    private val emitEvent: suspend (InferenceEvent) -> Unit = { emittedEvents.add(it) }

    private fun createHandler(
        allowToolCall: Boolean = true,
        chatId: ChatId? = null,
        userMessageId: MessageId? = null,
    ) = OpenAiResponseStreamHandler(
        provider = "TEST",
        modelId = "test-model",
        modelType = ModelType.THINKING,
        loggingPort = loggingPort,
        tag = "TestHandler",
        allowToolCall = allowToolCall,
        chatId = chatId,
        userMessageId = userMessageId,
        emitEvent = emitEvent,
    )

    // ── novelStreamSuffix ────────────────────────────────────────────────────

    @Test
    fun `novelStreamSuffix returns full text when no incremental chunks were emitted`() {
        val handler = createHandler()
        assertEquals("full response", handler.novelStreamSuffix(streamedText = "", finalizedText = "full response"))
    }

    @Test
    fun `novelStreamSuffix returns only missing suffix when done event follows deltas`() {
        val handler = createHandler()
        assertEquals(" world", handler.novelStreamSuffix(streamedText = "Hello", finalizedText = "Hello world"))
    }

    @Test
    fun `novelStreamSuffix avoids duplicate emission when done text matches streamed text`() {
        val handler = createHandler()
        assertEquals("", handler.novelStreamSuffix(streamedText = "Hello world", finalizedText = "Hello world"))
    }

    @Test
    fun `novelStreamSuffix returns empty string when finalized text is empty`() {
        val handler = createHandler()
        assertEquals("", handler.novelStreamSuffix(streamedText = "some text", finalizedText = ""))
    }

    @Test
    fun `novelStreamSuffix handles partial prefix overlap`() {
        val handler = createHandler()
        assertEquals("xyz", handler.novelStreamSuffix(streamedText = "ab", finalizedText = "abxyz"))
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
        val state = StreamState(
            emittedAny = true,
        )
        state.toolCallRequests += ToolCallRequest(
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

    // ── Multiple function calls in sequence ──────────────────────────────────

    @Test
    fun `multiple function_call_arguments_done events accumulate both tool calls`() =
        kotlinx.coroutines.test.runTest {
            // When a model emits multiple function_call_arguments_done events
            // in a single response (parallel tool calls), both calls are accumulated
            // in the list rather than overwriting each other.
            val handler = createHandler(allowToolCall = true)
            val state = StreamState()

            // First function call arrives via handleEvent
            val firstEvent = mockk<com.openai.models.responses.ResponseStreamEvent>(relaxed = true)
            val firstCallDone = mockFunctionCallArgumentsDoneEvent(
                itemId = "call_1",
                name = "tavily_web_search",
                arguments = """{"query":"first search"}"""
            )
            every { firstEvent.isFunctionCallArgumentsDone() } returns true
            every { firstEvent.functionCallArgumentsDone() } returns java.util.Optional.of(firstCallDone)

            handler.handleEvent(firstEvent, state)
            assertEquals("tavily_web_search", state.toolCallRequests.firstOrNull()?.toolName)

            // Second function call arrives in the same response
            val secondEvent = mockk<com.openai.models.responses.ResponseStreamEvent>(relaxed = true)
            val secondCallDone = mockFunctionCallArgumentsDoneEvent(
                itemId = "call_2",
                name = "attached_image_inspect",
                arguments = """{"question":"describe this image"}"""
            )
            every { secondEvent.isFunctionCallArgumentsDone() } returns true
            every { secondEvent.functionCallArgumentsDone() } returns java.util.Optional.of(secondCallDone)

            handler.handleEvent(secondEvent, state)

            // Both tool calls are now captured in the list (bug fixed):
            assertEquals("tavily_web_search", state.toolCallRequests[0].toolName)
            assertEquals("attached_image_inspect", state.toolCallRequests[1].toolName)

            // The provider tool call IDs are accumulated in a list:
            assertEquals(listOf("call_1", "call_2"), state.providerToolCallIds)
            assertEquals(listOf("call_1", "call_2"), state.providerToolItemIds)
        }

    // ── Single function call capture ────────────────────────────────────────────

    @Test
    fun `handleEvent dispatches function_call_arguments_done and captures tool call`() =
        kotlinx.coroutines.test.runTest {
            val handler = createHandler(allowToolCall = true)
            val state = StreamState()

            val event = mockk<com.openai.models.responses.ResponseStreamEvent>(relaxed = true)
            val functionCallDone = mockFunctionCallArgumentsDoneEvent(
                itemId = "fc_1",
                name = "tavily_web_search",
                arguments = """{"query":"test"}"""
            )
            every { event.isFunctionCallArgumentsDone() } returns true
            every { event.functionCallArgumentsDone() } returns java.util.Optional.of(functionCallDone)

            handler.handleEvent(event, state)
            assertNotNull(state.toolCallRequests.firstOrNull())
            assertEquals("tavily_web_search", state.toolCallRequests.firstOrNull()?.toolName)
        }

    @Test
    fun `handleEvent throws IllegalStateException when allowToolCall is false and function call arrives`() =
        kotlinx.coroutines.test.runTest {
            val handler = createHandler(allowToolCall = false)
            val state = StreamState()

            val event = mockk<com.openai.models.responses.ResponseStreamEvent>(relaxed = true)
            val functionCallDone = mockFunctionCallArgumentsDoneEvent(
                itemId = "call_1",
                name = "tavily_web_search",
                arguments = """{"query":"test"}"""
            )
            every { event.isFunctionCallArgumentsDone() } returns true
            every { event.functionCallArgumentsDone() } returns java.util.Optional.of(functionCallDone)

            try {
                handler.handleEvent(event, state)
                org.junit.jupiter.api.Assertions.fail("Expected IllegalStateException")
            } catch (e: IllegalStateException) {
                assertEquals("Search skill recursion limit exceeded", e.message)
            }
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