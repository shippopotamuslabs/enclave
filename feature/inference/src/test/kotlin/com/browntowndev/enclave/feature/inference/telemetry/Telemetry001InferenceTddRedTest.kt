package com.browntowndev.enclave.feature.inference.telemetry

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.core.http.StreamResponse
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.RawContentBlockDelta
import com.anthropic.models.messages.RawContentBlockDeltaEvent
import com.anthropic.models.messages.RawMessageDeltaEvent
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.RawMessageStopEvent
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.StopReason
import com.anthropic.models.messages.TextDelta
import com.anthropic.services.blocking.MessageService
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.AgentLoopConfig
import com.browntowndev.enclave.domain.model.inference.AgentLoopHostCallbacks
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.AgentModelTurnResult
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.AgentLoopEvent
import com.browntowndev.enclave.domain.port.inference.AgentLoopPort
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.inference.ToolStreamGatePort
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySeverity
import com.browntowndev.enclave.domain.usecase.chat.RollingSummarizer
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.domain.util.JTokkitTokenCounter
import com.browntowndev.enclave.feature.inference.AnthropicInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.BaseOpenAiSdkInferenceService
import com.browntowndev.enclave.feature.inference.GoogleGenAiSdkClient
import com.browntowndev.enclave.feature.inference.GoogleInferenceServiceImpl
import com.browntowndev.enclave.feature.inference.GoogleSdkResult
import com.browntowndev.enclave.feature.inference.RustCompatibleInferenceService
import com.browntowndev.enclave.feature.inference.RustLoopInferenceService
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.browntowndev.enclave.feature.inference.AndroidLoggingAdapter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith
import java.io.IOException
import java.util.Optional
import java.util.stream.Stream
import javax.inject.Provider

/**
 * TELEMETRY-001 TDD Red — inference layer scenarios TEL-037..TEL-044, TEL-050..TEL-053, TEL-062.
 */
class Telemetry001InferenceTddRedTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `TEL-037 AnthropicInferenceServiceImpl emits InferenceOutcome on success`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val service = createAnthropicService(telemetryPort)
            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        it.success &&
                            it.provider == "Anthropic" &&
                            it.severity == TelemetrySeverity.INFO
                    },
                )
            }
        }

    @Test
    fun `TEL-038 AnthropicInferenceServiceImpl emits InferenceOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } throws IOException("Connection refused")
            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetryEventPort = telemetryPort,
                )
            assertFailsWith<InferenceNetworkException> {
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                    systemPrompt = "",
                    contextSidecars = null,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )
            }
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        !it.success &&
                            it.errorMessage == "Connection refused" &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }

    @Test
    fun `TEL-039 GoogleInferenceServiceImpl emits InferenceOutcome on success`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            coEvery {
                sdkClient.generateContentStream(any(), any(), any(), any(), any())
            } returns GoogleSdkResult(emittedAny = true, functionCall = null, assistantContent = null, finishReason = "STOP")
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetryEventPort = telemetryPort,
                )
            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        it.success &&
                            it.provider == "Google" &&
                            it.severity == TelemetrySeverity.INFO
                    },
                )
            }
        }

    @Test
    fun `TEL-040 GoogleInferenceServiceImpl emits InferenceOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            coEvery {
                sdkClient.generateContentStream(any(), any(), any(), any(), any())
            } throws IOException("Timeout")
            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetryEventPort = telemetryPort,
                )
            assertFailsWith<InferenceNetworkException> {
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                    systemPrompt = "",
                    contextSidecars = null,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )
            }
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        !it.success &&
                            it.errorMessage == "Timeout" &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }

    @Test
    fun `TEL-041 BaseOpenAiSdkInferenceService emits InferenceOutcome on success`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val service =
                TestOpenAiService(
                    client = mockk(relaxed = true),
                    loggingPort = loggingPort,
                    telemetryEventPort = telemetryPort,
                )
            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        it.success &&
                            it.provider == "OpenAI" &&
                            it.severity == TelemetrySeverity.INFO
                    },
                )
            }
        }

    @Test
    fun `TEL-042 BaseOpenAiSdkInferenceService emits InferenceOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val service =
                TestOpenAiService(
                    client = mockk(relaxed = true),
                    loggingPort = loggingPort,
                    telemetryEventPort = telemetryPort,
                    failWith = IOException("Rate limited"),
                )
            assertFailsWith<InferenceNetworkException> {
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                    systemPrompt = "",
                    contextSidecars = null,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )
            }
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match {
                        !it.success &&
                            it.errorMessage == "Rate limited" &&
                            it.severity == TelemetrySeverity.ERROR
                    },
                )
            }
        }

    @Test
    fun `TEL-043 RustLoopInferenceService emits InferenceOutcome on success`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val port =
                CapturingAgentLoopPort { host ->
                    send(AgentLoopEvent.Finished)
                }
            val service = createRustLoopService(telemetryPort, port)
            service.sendPrompt("hello").toList()
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match { it.success && it.severity == TelemetrySeverity.INFO },
                )
            }
        }

    @Test
    fun `TEL-044 RustLoopInferenceService emits InferenceOutcome on failure`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val port =
                CapturingAgentLoopPort { _ ->
                    send(AgentLoopEvent.Failed("loop failed"))
                }
            val service = createRustLoopService(telemetryPort, port)
            service.sendPrompt("hello").toList()
            coVerify {
                telemetryPort.emitInferenceOutcome(
                    match { !it.success && it.severity == TelemetrySeverity.ERROR },
                )
            }
        }

    @Test
    fun `TEL-050 AndroidLoggingAdapter error reports non-fatal to Crashlytics when throwable present`() {
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        val adapter = AndroidLoggingAdapter()
        val throwable = RuntimeException("Test")
        adapter.error("TestTag", "Test message", throwable)
        verify(exactly = 1) { crashlytics.recordException(throwable) }
    }

    @Test
    fun `TEL-051 AndroidLoggingAdapter error does not report when throwable is null`() {
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        val adapter = AndroidLoggingAdapter()
        adapter.error("TestTag", "Test message", null)
        verify(exactly = 0) { crashlytics.recordException(any()) }
    }

    @Test
    fun `TEL-052 AndroidLoggingAdapter error handles Crashlytics failure gracefully`() {
        mockkStatic(FirebaseCrashlytics::class)
        mockkStatic(Log::class)
        every { FirebaseCrashlytics.getInstance() } throws RuntimeException("Not initialized")
        every { Log.e(any(), any(), any()) } returns 0
        val adapter = AndroidLoggingAdapter()
        val throwable = RuntimeException("Test")
        adapter.error("TestTag", "Test message", throwable)
        verify { Log.e("TestTag", "Test message", throwable) }
    }

    @Test
    fun `TEL-053 AndroidLoggingAdapter error uses crashlytics log for breadcrumbs not setCustomKey`() {
        mockkStatic(FirebaseCrashlytics::class)
        val crashlytics = mockk<FirebaseCrashlytics>(relaxed = true)
        every { FirebaseCrashlytics.getInstance() } returns crashlytics
        val adapter = AndroidLoggingAdapter()
        adapter.error("TestTag", "Test message", RuntimeException("Test"))
        verify {
            crashlytics.log(match { it.contains("TestTag") && it.contains("Test message") })
        }
        verify(exactly = 0) { crashlytics.setCustomKey("LogTag", any<String>()) }
        verify(exactly = 0) { crashlytics.setCustomKey("Message", any<String>()) }
    }

    @Test
    fun `TEL-062 Telemetry wrappers rethrow CancellationException`() =
        runTest {
            val telemetryPort = mockk<TelemetryEventPort>(relaxed = true)
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } answers {
                throw CancellationException("cancelled")
            }
            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetryEventPort = telemetryPort,
                )
            val job =
                async {
                    service.completeSingleTurn(
                        sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
                        systemPrompt = "",
                        contextSidecars = null,
                        availableTools = emptyList(),
                        allowToolCall = false,
                        onTextToken = {},
                        onThinkingToken = {},
                        options = GenerationOptions(reasoningBudget = 0),
                    )
                }
            job.cancel()
            assertFailsWith<CancellationException> {
                job.await()
            }
            coVerify(exactly = 0) { telemetryPort.emitInferenceOutcome(any()) }
        }

    private fun createAnthropicService(telemetryPort: TelemetryEventPort): AnthropicInferenceServiceImpl {
        val client = mockk<AnthropicClient>()
        val messageService = mockk<MessageService>()
        val streamResponse = mockk<StreamResponse<RawMessageStreamEvent>>()

        val textDelta = mockk<TextDelta>()
        every { textDelta.text() } returns "ok"
        val textBlockDelta = mockk<RawContentBlockDelta>()
        every { textBlockDelta.text() } returns Optional.of(textDelta)
        every { textBlockDelta.inputJson() } returns Optional.empty()
        every { textBlockDelta.thinking() } returns Optional.empty()
        val textDeltaEvent = mockk<RawContentBlockDeltaEvent>()
        every { textDeltaEvent.delta() } returns textBlockDelta

        val messageDelta = mockk<RawMessageDeltaEvent>()
        val messageDeltaInner = mockk<RawMessageDeltaEvent.Delta>()
        val deltaUsage = mockk<MessageDeltaUsage>()
        every { messageDelta.delta() } returns messageDeltaInner
        every { messageDeltaInner.stopReason() } returns Optional.of(StopReason.END_TURN)
        every { messageDelta.usage() } returns deltaUsage
        every { deltaUsage.outputTokens() } returns 7L
        every { deltaUsage.inputTokens() } returns Optional.empty()
        every { deltaUsage.cacheCreationInputTokens() } returns Optional.empty()
        every { deltaUsage.cacheReadInputTokens() } returns Optional.empty()

        val textEvent =
            mockAnthropicStreamEvent(
                contentBlockDelta = Optional.of(textDeltaEvent),
            )
        val stopEvent =
            mockAnthropicStreamEvent(
                messageStop = Optional.of(mockk<RawMessageStopEvent>()),
                messageDelta = Optional.of(messageDelta),
            )

        every { client.messages() } returns messageService
        every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
        every { streamResponse.stream() } returns Stream.of(textEvent, stopEvent)
        every { streamResponse.close() } returns Unit
        return AnthropicInferenceServiceImpl(
            client = client,
            modelId = "claude-3-5-sonnet-20241022",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
            toolStreamGatePort = FakeToolStreamGatePort(),
            telemetryEventPort = telemetryPort,
        )
    }

    private fun mockAnthropicStreamEvent(
        contentBlockDelta: Optional<RawContentBlockDeltaEvent> = Optional.empty(),
        messageDelta: Optional<RawMessageDeltaEvent> = Optional.empty(),
        messageStop: Optional<RawMessageStopEvent> = Optional.empty(),
    ): RawMessageStreamEvent {
        val event = mockk<RawMessageStreamEvent>()
        every { event.contentBlockDelta() } returns contentBlockDelta
        every { event.messageStart() } returns Optional.empty()
        every { event.messageDelta() } returns messageDelta
        every { event.messageStop() } returns messageStop
        every { event.contentBlockStart() } returns Optional.empty()
        every { event.contentBlockStop() } returns Optional.empty()
        return event
    }

    private fun createRustLoopService(
        telemetryPort: TelemetryEventPort,
        agentLoopPort: AgentLoopPort,
    ): RustLoopInferenceService {
        val inner =
            object : RustCompatibleInferenceService {
                override fun sendPrompt(
                    prompt: String,
                    closeConversation: Boolean,
                ): Flow<InferenceEvent> = flowOf(InferenceEvent.PartialResponse("ok", ModelType.MAIN))

                override fun sendPrompt(
                    prompt: String,
                    options: GenerationOptions,
                    closeConversation: Boolean,
                ): Flow<InferenceEvent> = sendPrompt(prompt, closeConversation)

                override suspend fun setHistory(messages: List<com.browntowndev.enclave.domain.model.chat.ChatMessage>) = Unit

                override suspend fun closeSession() = Unit

                override suspend fun completeSingleTurn(
                    sessionMessages: List<AgentLoopMessage>,
                    systemPrompt: String,
                    contextSidecars: String?,
                    availableTools: List<com.browntowndev.enclave.domain.model.inference.ToolDefinition>,
                    allowToolCall: Boolean,
                    onTextToken: suspend (String) -> Unit,
                    onThinkingToken: suspend (String) -> Unit,
                    options: GenerationOptions,
                ): AgentModelTurnResult = error("unused")
            }
        val toolExecutor =
            object : ToolExecutorPort {
                override suspend fun execute(request: ToolCallRequest): ToolExecutionResult =
                    ToolExecutionResult(toolName = request.toolName, resultJson = "{}")
            }
        return RustLoopInferenceService(
            inner = inner,
            agentLoopPort = agentLoopPort,
            toolExecutor = toolExecutor,
            loggingPort = loggingPort,
            provider = "OPENAI",
            modelType = ModelType.MAIN,
            inferenceFactoryProvider = Provider { mockk(relaxed = true) },
            rollingSummarizer = RollingSummarizer(loggingPort, JTokkitTokenCounter),
            resolveCompactionSummaryModel = mockk(relaxed = true),
            telemetryEventPort = telemetryPort,
        )
    }

    private class TestOpenAiService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        toolStreamGatePort: ToolStreamGatePort = FakeToolStreamGatePort(),
        private val telemetryEventPort: TelemetryEventPort? = null,
        private val failWith: Throwable? = null,
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "gpt-4.1",
            provider = "OpenAI",
            modelType = ModelType.MAIN,
            loggingPort = loggingPort,
            toolStreamGatePort = toolStreamGatePort,
            telemetryEventPort = telemetryEventPort,
        ) {
        override val tag: String = "TestOpenAiService"

        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
            userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
            assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse {
            failWith?.let { throw it }
            emitEvent(InferenceEvent.PartialResponse("ok", ModelType.MAIN))
            return StreamedOpenAiResponse(
                emittedAny = true,
                functionCalls = emptyList(),
                responseId = "resp",
                providerToolCallIds = emptyList(),
                providerToolItemIds = emptyList(),
                assistantMessageText = "ok",
            )
        }

        override suspend fun streamResponses(
            params: ResponseCreateParams,
            chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
            userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
            assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse = streamChatCompletions(
            params = ChatCompletionCreateParams.builder().model("gpt-4.1").build(),
            chatId = chatId,
            userMessageId = userMessageId,
            assistantMessageId = assistantMessageId,
            emitEvent = emitEvent,
        )
    }

    private class CapturingAgentLoopPort(
        private val behavior: suspend ProducerScope<AgentLoopEvent>.(AgentLoopHostCallbacks) -> Unit,
    ) : AgentLoopPort {
        override fun execute(
            initialMessages: List<ChatMessage>,
            config: AgentLoopConfig,
            sessionContext: Map<String, String>,
            host: AgentLoopHostCallbacks,
        ): Flow<AgentLoopEvent> =
            channelFlow {
                behavior(host)
            }
    }
}
