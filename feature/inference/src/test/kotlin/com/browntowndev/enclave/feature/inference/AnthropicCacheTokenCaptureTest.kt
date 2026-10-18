// MockK is used only for Anthropic Java SDK types (com.anthropic.*), a third-party SDK boundary.
@file:Suppress("ForbiddenMockKInTests")

package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.RawMessageDeltaEvent
import com.anthropic.models.messages.RawMessageStartEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.Usage
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.Optional
import java.util.stream.Stream

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class AnthropicCacheTokenCaptureTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun `messageStart with zero cache read tokens preserves zero instead of null`() =
        runTest {
            val usage = usage(cacheCreation = 0L, cacheRead = 0L)
            val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(0, result.telemetry?.cacheCreationInputTokens)
            assertEquals(0, result.telemetry?.cacheReadInputTokens)
        }

    @Test
    fun `messageStart with nonzero cache tokens passes through correctly`() =
        runTest {
            val usage = usage(cacheCreation = 300L, cacheRead = 5000L)
            val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(300, result.telemetry?.cacheCreationInputTokens)
            assertEquals(5000, result.telemetry?.cacheReadInputTokens)
        }

    @Test
    fun `messageStart with absent cache fields passes null`() =
        runTest {
            val usage = usage(cacheCreation = null, cacheRead = null)
            val stream = streamOf(messageStartEvent(usage), anthropicMessageStopEvent())
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(null, result.telemetry?.cacheCreationInputTokens)
            assertEquals(null, result.telemetry?.cacheReadInputTokens)
        }

    @Test
    fun `messageDelta overrides messageStart cache tokens when present`() =
        runTest {
            val startUsage = usage(cacheCreation = 0L, cacheRead = 0L)
            val deltaUsage = deltaUsage(cacheCreation = 500L, cacheRead = 3000L)
            val stream = streamOf(
                messageStartEvent(startUsage),
                deltaStreamEvent(deltaUsage),
                anthropicMessageStopEvent(),
            )
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(500, result.telemetry?.cacheCreationInputTokens)
            assertEquals(3000, result.telemetry?.cacheReadInputTokens)
        }

    @Test
    fun `messageDelta absent cache fields preserves messageStart values`() =
        runTest {
            val startUsage = usage(cacheCreation = 400L, cacheRead = 2000L)
            val deltaUsage = deltaUsage(cacheCreation = null, cacheRead = null)
            val stream = streamOf(
                messageStartEvent(startUsage),
                deltaStreamEvent(deltaUsage),
                anthropicMessageStopEvent(),
            )
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(400, result.telemetry?.cacheCreationInputTokens)
            assertEquals(2000, result.telemetry?.cacheReadInputTokens)
        }

    @Test
    fun `messageDelta with zero cache tokens overrides nonzero messageStart values`() =
        runTest {
            val startUsage = usage(cacheCreation = 400L, cacheRead = 2000L)
            val deltaUsage = deltaUsage(cacheCreation = 0L, cacheRead = 0L)
            val stream = streamOf(
                messageStartEvent(startUsage),
                deltaStreamEvent(deltaUsage),
                anthropicMessageStopEvent(),
            )
            val service = createService(stream)

            val result = completeTurn(service)

            assertEquals(0, result.telemetry?.cacheCreationInputTokens)
            assertEquals(0, result.telemetry?.cacheReadInputTokens)
        }

    // --- Helpers ---

    private suspend fun completeTurn(service: AnthropicInferenceServiceImpl) =
        service.completeSingleTurn(
            sessionMessages = listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
            systemPrompt = "",
            contextSidecars = null,
            availableTools = emptyList(),
            allowToolCall = false,
            onTextToken = {},
            onThinkingToken = {},
            options = GenerationOptions(
                reasoningBudget = 0,
                assistantMessageId = com.browntowndev.enclave.domain.model.chat.MessageId("msg_1"),
                chatId = com.browntowndev.enclave.domain.model.chat.ChatId("chat_1"),
            ),
        )

    private fun createService(stream: Stream<RawMessageStreamEvent>): AnthropicInferenceServiceImpl {
        val client = mockk<AnthropicClient>()
        val messageService = mockk<MessageService>()
        val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()
        val telemetrySupport =
            FakeInferenceTelemetrySupport(ApplicationProvider.getApplicationContext<Context>())
        val captureContext =
            InferenceTelemetrySupport.CaptureContext(
                messageId = MessageId("msg_1"),
                chatId = ChatId("chat_1"),
                sequenceIndex = 0,
                clientBuild =
                    TelemetryClientBuild(
                        appVersionName = "test",
                        appVersionCode = 1L,
                        telemetrySchemaVersion = 1,
                    ),
            )

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns stream
        every { streamResponse.close() } returns Unit

        telemetrySupport.captureContextResult = captureContext
        telemetrySupport.anthropicTelemetryBuilder = { call ->
            val cc = call.context
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
                inputTokens = call.inputTokens,
                outputTokens = call.outputTokens,
                reasoningTokens = call.reasoningTokens,
                cachedInputTokens = null,
                cacheReadInputTokens = call.cacheReadInputTokens,
                cacheWriteInputTokens = null,
                cacheCreationInputTokens = call.cacheCreationInputTokens,
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
        cacheCreation: Long?,
        cacheRead: Long?,
    ): Usage {
        val u = mockk<Usage>()
        every { u.inputTokens() } returns inputTokens
        every { u.outputTokens() } returns outputTokens
        every { u.cacheCreationInputTokens() } returns Optional.ofNullable(cacheCreation)
        every { u.cacheReadInputTokens() } returns Optional.ofNullable(cacheRead)
        every { u._additionalProperties() } returns emptyMap()
        return u
    }

    private fun deltaUsage(
        outputTokens: Long = 60L,
        inputTokens: Long? = 100L,
        cacheCreation: Long?,
        cacheRead: Long?,
    ): MessageDeltaUsage {
        val u = mockk<MessageDeltaUsage>()
        every { u.outputTokens() } returns outputTokens
        every { u.inputTokens() } returns Optional.ofNullable(inputTokens)
        every { u.cacheCreationInputTokens() } returns Optional.ofNullable(cacheCreation)
        every { u.cacheReadInputTokens() } returns Optional.ofNullable(cacheRead)
        every { u._additionalProperties() } returns emptyMap()
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
