package com.browntowndev.pocketcrew.feature.inference.openai

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import io.mockk.coEvery
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
            toolCallRequest = ToolCallRequest(
                toolName = "search",
                argumentsJson = """{"q":"test"}""",
                provider = "TEST",
                modelType = ModelType.THINKING,
                chatId = null,
                userMessageId = null,
            ),
            responseId = "resp_123",
            providerToolCallId = "call_456",
            providerToolItemId = "item_789",
        )
        state.streamedAssistantMessage.append("Hello world")

        val result = handler.toStreamedResponse(state)
        assertTrue(result.emittedAny)
        assertNotNull(result.functionCall)
        assertEquals("search", result.functionCall.toolName)
        assertEquals("resp_123", result.responseId)
        assertEquals("call_456", result.providerToolCallId)
        assertEquals("item_789", result.providerToolItemId)
        assertEquals("Hello world", result.assistantMessageText)
    }

    @Test
    fun `toStreamedResponse maps empty state to default values`() {
        val handler = createHandler()
        val state = StreamState()

        val result = handler.toStreamedResponse(state)
        assertFalse(result.emittedAny)
        assertNull(result.functionCall)
        assertNull(result.responseId)
        assertNull(result.providerToolCallId)
        assertNull(result.providerToolItemId)
        assertEquals("", result.assistantMessageText)
    }
}