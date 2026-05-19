package com.browntowndev.pocketcrew.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.TextDelta
import com.anthropic.services.blocking.MessageService
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.browntowndev.pocketcrew.domain.util.FakeToolEnvelopeParserPort
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Optional
import java.util.stream.Stream
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * centralized-leaked-tool-call-recovery — CL-MD-02, CL-K-05.
 */
class LeakedToolRecoveryMutationTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun cl_md_02_tool_stream_gate_session_absent_in_inference() {
        val inferenceMain =
            Paths.get("src/main/kotlin/com/browntowndev/pocketcrew/feature/inference")
        val sessionFile = inferenceMain.resolve("ToolStreamGateSession.kt")
        assertFalse(sessionFile.exists(), "CL-MD-02 ToolStreamGateSession.kt must not exist")
        val sources =
            Files
                .walk(inferenceMain)
                .filter { it.toString().endsWith(".kt") }
                .map { it.readText() }
                .toList()
        assertFalse(
            sources.any { it.contains("class ToolStreamGateSession") },
            "CL-MD-02 no ToolStreamGateSession class in inference main",
        )
    }

    @Test
    fun cl_k_05_anthropic_does_not_call_tool_stream_gate_port_push() =
        runTest {
            val recordingGate = RecordingToolStreamGatePort()
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()

            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns
                Stream.of(
                    anthropicTextDeltaEvent("Hi"),
                    anthropicMessageStopEvent(),
                )
            every { streamResponse.close() } returns Unit

            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-sonnet-4-20250514",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = recordingGate,
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "sys",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(0, recordingGate.pushCalls.size, "CL-K-05 pushCalls")
        }

    private fun anthropicTextDeltaEvent(text: String): RawMessageStreamEvent {
        val textDelta = mockk<TextDelta>()
        every { textDelta.text() } returns text
        val textBlockDelta = mockk<RawContentBlockDelta>()
        every { textBlockDelta.text() } returns Optional.of(textDelta)
        every { textBlockDelta.inputJson() } returns Optional.empty()
        every { textBlockDelta.thinking() } returns Optional.empty()
        val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { textDeltaEvent.delta() } returns textBlockDelta
        val event = mockk<RawMessageStreamEvent>()
        every { event.messageStart() } returns Optional.empty()
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockDelta() } returns Optional.of(textDeltaEvent)
        every { event.contentBlockStop() } returns Optional.empty()
        every { event.messageDelta() } returns Optional.empty()
        every { event.messageStop() } returns Optional.empty()
        return event
    }

    private fun anthropicMessageStopEvent(): RawMessageStreamEvent {
        val event = mockk<RawMessageStreamEvent>()
        every { event.messageStart() } returns Optional.empty()
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockDelta() } returns Optional.empty()
        every { event.contentBlockStop() } returns Optional.empty()
        every { event.messageDelta() } returns Optional.empty()
        every { event.messageStop() } returns Optional.of(mockk())
        return event
    }

    private class RecordingToolStreamGatePort : ToolStreamGatePort {
        val pushCalls = mutableListOf<Pair<ToolStreamGateHandle, String>>()

        override fun create(allowToolCall: Boolean): ToolStreamGateHandle = ToolStreamGateHandle(nativeHandle = Unit)

        override fun reset(handle: ToolStreamGateHandle) {}

        override fun push(
            handle: ToolStreamGateHandle,
            chunk: String,
        ): ToolStreamGatePushResult {
            pushCalls.add(handle to chunk)
            return ToolStreamGatePushResult(emittedPrefix = chunk)
        }

        override fun finish(
            handle: ToolStreamGateHandle,
            hasNativeToolCalls: Boolean,
        ): ToolStreamGateFinish = ToolStreamGateFinish(trailingFlush = "", hasToolMarker = false)

        override fun bufferedText(handle: ToolStreamGateHandle): String = ""
    }
}
