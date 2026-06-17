package com.browntowndev.enclave.feature.inference.openai

import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LifecycleHandlersRecoverableStreamTest {
    @Test
    fun `MAR-K-04 openrouter idle timeout is recoverable stream termination`() {
        assertTrue(
            isRecoverableStreamTermination(
                "SseException: 200: Upstream idle timeout exceeded provider_unavailable",
            ),
        )
    }

    @Test
    fun `short transient partial defers to model api retry instead of salvaging`() {
        val state = StreamState(emittedAny = true)
        state.streamedAssistantMessage.append("partial sentence")
        assertFalse(shouldRecoverFromStreamTermination(state, "connection reset"))
    }

    @Test
    fun `substantial transient partial is salvaged`() {
        val state = StreamState(emittedAny = true)
        state.streamedAssistantMessage.append("y".repeat(MIN_SALVAGEABLE_STREAM_CHARS))
        assertTrue(shouldRecoverFromStreamTermination(state, "connection reset"))
    }

    @Test
    fun `buffered tool calls are salvaged regardless of text length`() {
        val state = StreamState(emittedAny = true)
        state.toolCallRequests +=
            ToolCallRequest(
                toolName = "web_search",
                argumentsJson = """{"q":"x"}""",
                provider = "TEST",
                modelType = ModelType.THINKING,
                chatId = null,
                userMessageId = null,
            )
        assertTrue(shouldRecoverFromStreamTermination(state, "connection reset"))
    }

    @Test
    fun `permanent error is never salvaged`() {
        val state = StreamState(emittedAny = true)
        state.streamedAssistantMessage.append("z".repeat(MIN_SALVAGEABLE_STREAM_CHARS))
        assertFalse(shouldRecoverFromStreamTermination(state, "401 unauthorized: invalid api key"))
    }
}
