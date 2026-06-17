package com.browntowndev.enclave.feature.inference

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.RawMessageDeltaEvent
import com.anthropic.models.messages.RawMessageStartEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.Usage
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.stream.Stream

/**
 * Verifies reasoning tokens emitted by Anthropic-compatible third-party gateways flow
 * through [AnthropicInferenceServiceImpl.streamMessages] into the captured [TurnTelemetry].
 *
 * Mirrors the MockK streaming fixture pattern of [AnthropicCacheTokenCaptureTest], extending the
 * `usage`/`deltaUsage` helpers to also stub [Usage._additionalProperties] /
 * [MessageDeltaUsage._additionalProperties].
 */
class AnthropicReasoningTokenCaptureTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `messageStart top-level reasoning_tokens flows into telemetry`() = runTest {
        val usage =
            usage(
                additionalProperties = mapOf("reasoning_tokens" to JsonValue.from(128)),
            )
        val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
        val service = createService(stream)

        val result = completeTurn(service)

        assertEquals(128, result.telemetry?.reasoningTokens)
    }

    @Test
    fun `messageStart nested completion_tokens_details reasoning_tokens flows into telemetry`() =
        runTest {
            val usage =
                usage(
                    additionalProperties =
                        mapOf(
                            "completion_tokens_details" to
                                JsonValue.from(mapOf("reasoning_tokens" to 2048)),
                        ),
                )
            val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(2048, result.telemetry?.reasoningTokens)
        }

    @Test
    fun `messageDelta reasoning_tokens overrides messageStart value`() = runTest {
        val startUsage = usage(additionalProperties = mapOf("reasoning_tokens" to JsonValue.from(100)))
        val deltaUsage =
            deltaUsage(additionalProperties = mapOf("reasoning_tokens" to JsonValue.from(999)))
        val stream =
            streamOf(
                messageStartEvent(startUsage),
                deltaStreamEvent(deltaUsage),
                anthropicMessageStopEvent(),
            )
        val service = createService(stream)

        val result = completeTurn(service)

        assertEquals(999, result.telemetry?.reasoningTokens)
    }

    @Test
    fun `standard Anthropic response with no reasoning fields yields null reasoning tokens`() =
        runTest {
            // Regression guard: real Anthropic responses carry no additional properties, so
            // reasoningTokens must stay null exactly as before this feature.
            val usage = usage(additionalProperties = emptyMap())
            val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(null, result.telemetry?.reasoningTokens)
        }

    // --- Helpers ---

    private suspend fun completeTurn(service: AnthropicInferenceServiceImpl) =
        service.completeSingleTurn(
            sessionMessages =
                listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
            systemPrompt = "",
            contextSidecars = null,
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options =
                GenerationOptions(
                    reasoningBudget = 0,
                    assistantMessageId = com.browntowndev.enclave.domain.model.chat.MessageId("msg_1"),
                    chatId = com.browntowndev.enclave.domain.model.chat.ChatId("chat_1"),
                ),
        )

    private fun createService(stream: Stream<RawMessageStreamEvent>): AnthropicInferenceServiceImpl {
        val client = mockk<AnthropicClient>()
        val messageService = mockk<MessageService>()
        val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val telemetrySupport = mockk<InferenceTelemetrySupport>()
        val captureContext = mockk<InferenceTelemetrySupport.CaptureContext>(relaxed = true)

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns stream
        every { streamResponse.close() } returns Unit

        every { telemetrySupport.captureContext(any()) } returns captureContext
        every {
            telemetrySupport.anthropicTurnTelemetry(
                context = any(),
                providerLabel = any(),
                modelId = any(),
                modelLabel = any(),
                timing = any(),
                inputTokens = any(),
                outputTokens = any(),
                reasoningTokens = any(),
                cacheCreationInputTokens = any(),
                cacheReadInputTokens = any(),
                savedPricing = any(),
            )
        } answers {
            val cc = firstArg<InferenceTelemetrySupport.CaptureContext>()
            val reasonTok = arg<Int?>(7)
            TurnTelemetry(
                id = com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId("test"),
                messageId = cc.messageId,
                chatId = cc.chatId,
                sequenceIndex = cc.sequenceIndex,
                providerKey = com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey.ANTHROPIC,
                providerLabel = "Anthropic",
                modelId = "claude-sonnet-4-20250514",
                modelLabel = "claude-sonnet-4-20250514",
                sourceKind = com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind.REMOTE,
                requestStartedAtMs = 0L,
                firstOutputTokenAtMs = null,
                completedAtMs = 1L,
                inputTokens = arg<Int?>(5),
                outputTokens = arg<Int?>(6),
                reasoningTokens = reasonTok,
                cachedInputTokens = null,
                cacheReadInputTokens = arg<Int?>(9),
                cacheWriteInputTokens = null,
                cacheCreationInputTokens = arg<Int?>(8),
                costUsd = null,
                costStatus = com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus.UNAVAILABLE,
                costSource = com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource.NOT_AVAILABLE,
                clientBuild = cc.clientBuild,
            )
        }

        return AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-sonnet-4-20250514",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
            telemetrySupport = telemetrySupport,
        )
    }

    private fun usage(
        inputTokens: Long = 100L,
        outputTokens: Long = 50L,
        additionalProperties: Map<String, JsonValue> = emptyMap(),
    ): Usage {
        val u = mockk<Usage>()
        every { u.inputTokens() } returns inputTokens
        every { u.outputTokens() } returns outputTokens
        every { u.cacheCreationInputTokens() } returns Optional.empty()
        every { u.cacheReadInputTokens() } returns Optional.empty()
        every { u._additionalProperties() } returns additionalProperties
        return u
    }

    private fun deltaUsage(
        outputTokens: Long = 60L,
        additionalProperties: Map<String, JsonValue> = emptyMap(),
    ): MessageDeltaUsage {
        val u = mockk<MessageDeltaUsage>()
        every { u.outputTokens() } returns outputTokens
        every { u.inputTokens() } returns Optional.empty()
        every { u.cacheCreationInputTokens() } returns Optional.empty()
        every { u.cacheReadInputTokens() } returns Optional.empty()
        every { u._additionalProperties() } returns additionalProperties
        return u
    }

    private fun messageStartEvent(usage: Usage): RawMessageStreamEvent {
        val message = mockk<Message>()
        every { message.usage() } returns usage
        val startEvent = mockk<RawMessageStartEvent>()
        every { startEvent.message() } returns message
        val event = mockk<RawMessageStreamEvent>(relaxed = true)
        every { event.messageStart() } returns Optional.of(startEvent)
        every { event.messageDelta() } returns Optional.empty()
        every { event.messageStop() } returns Optional.empty()
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockDelta() } returns Optional.empty()
        every { event.contentBlockStop() } returns Optional.empty()
        return event
    }

    private fun deltaStreamEvent(usage: MessageDeltaUsage): RawMessageStreamEvent {
        val delta = mockk<RawMessageDeltaEvent.Delta>()
        every { delta.stopReason() } returns Optional.empty()
        val deltaEvent = mockk<RawMessageDeltaEvent>()
        every { deltaEvent.usage() } returns usage
        every { deltaEvent.delta() } returns delta
        val event = mockk<RawMessageStreamEvent>(relaxed = true)
        every { event.messageStart() } returns Optional.empty()
        every { event.messageDelta() } returns Optional.of(deltaEvent)
        every { event.messageStop() } returns Optional.empty()
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockDelta() } returns Optional.empty()
        every { event.contentBlockStop() } returns Optional.empty()
        return event
    }

    private fun streamOf(vararg events: RawMessageStreamEvent): Stream<RawMessageStreamEvent> =
        Stream.of(*events)
}
