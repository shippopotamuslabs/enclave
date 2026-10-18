package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.usecase.chat.ProcessThinkingTokensUseCase
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class LiteRtInferenceServiceImplTelemetryTest {
    private lateinit var conversationManager: FakeConversationManager
    private lateinit var conversation: FakeLiteRtConversation
    private lateinit var telemetrySupport: InferenceTelemetrySupport
    private lateinit var service: LiteRtInferenceServiceImpl
    private val toolExecutionEventPort = FakeToolExecutionEventPort()

    @BeforeEach
    fun setup() {
        conversation = FakeLiteRtConversation()
        conversationManager =
            FakeConversationManager().also {
                it.conversation = conversation
                it.telemetryContextResult = LiteRtTelemetryContext(emptyList(), "")
            }
        telemetrySupport = testInferenceTelemetrySupport()

        service =
            LiteRtInferenceServiceImpl(
                conversationManager = conversationManager,
                processThinkingTokens = ProcessThinkingTokensUseCase(),
                toolExecutionEventPort = toolExecutionEventPort,
                modelType = ModelType.FAST,
                telemetrySupport = telemetrySupport,
            )
    }

    @Test
    fun `sendPrompt captures LiteRT benchmark token counts reasoning estimate and TTFT`() =
        runTest {
            conversation.responseFlow =
                flowOf(
                    ConversationResponse(thought = "Let me think about this carefully"),
                    ConversationResponse(text = "Hello"),
                )
            conversation.benchmarkSnapshot =
                LiteRtBenchmarkSnapshot(
                    initTimeInSecond = 2.0,
                    timeToFirstTokenInSecond = 0.5,
                    lastPrefillTokenCount = 200,
                    lastDecodeTokenCount = 42,
                    lastPrefillTokensPerSecond = 800.0,
                    lastDecodeTokensPerSecond = 50.0,
                )

            val options =
                GenerationOptions(
                    reasoningBudget = 1024,
                    modelType = ModelType.FAST,
                    modelId = "test-litert",
                    chatId = ChatId("chat-1"),
                    assistantMessageId = MessageId("assistant-1"),
                )

            val events = service.sendPrompt("Hello", options, closeConversation = false).toList()
            val telemetryEvent = events.filterIsInstance<InferenceEvent.TelemetryCaptured>().single()

            val telemetry = telemetryEvent.telemetry
            assertEquals(200, telemetry.inputTokens)
            assertEquals(42, telemetry.outputTokens)
            assertEquals(8, telemetry.reasoningTokens)
            assertNotNull(telemetry.firstOutputTokenAtMs)
            assertTrue(telemetry.firstOutputTokenAtMs!! >= telemetry.requestStartedAtMs)
            assertTrue(telemetry.completedAtMs!! >= telemetry.firstOutputTokenAtMs!!)
        }

    @Test
    fun `sendPrompt estimates total input and cache read from conversation history`() =
        runTest {
            conversation.responseFlow = flowOf(ConversationResponse(text = "Answer"))
            conversation.benchmarkSnapshot =
                LiteRtBenchmarkSnapshot(
                    initTimeInSecond = 0.0,
                    timeToFirstTokenInSecond = 13.668,
                    lastPrefillTokenCount = 44,
                    lastDecodeTokenCount = 20,
                    lastPrefillTokensPerSecond = 800.0,
                    lastDecodeTokensPerSecond = 50.0,
                )
            conversationManager.telemetryContextResult =
                LiteRtTelemetryContext(
                    history =
                        listOf(
                            ChatMessage(Role.USER, "a".repeat(400)),
                            ChatMessage(Role.ASSISTANT, "b".repeat(400)),
                            ChatMessage(Role.USER, "c".repeat(400)),
                            ChatMessage(Role.ASSISTANT, "d".repeat(400)),
                        ),
                    systemPrompt = "You are helpful.",
                )

            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    modelType = ModelType.FAST,
                    modelId = "test-litert",
                    chatId = ChatId("chat-1"),
                    assistantMessageId = MessageId("assistant-1"),
                )

            val events = service.sendPrompt("Latest question", options, closeConversation = false).toList()
            val telemetry = events.filterIsInstance<InferenceEvent.TelemetryCaptured>().single().telemetry

            assertTrue((telemetry.inputTokens ?: 0) > 44)
            assertTrue((telemetry.cacheReadInputTokens ?: 0) > 0)
            assertTrue(telemetry.firstOutputTokenAtMs!! - telemetry.requestStartedAtMs < 5_000L)
        }

    @Test
    fun `sendPrompt still captures estimated telemetry when benchmark info is unavailable`() =
        runTest {
            conversation.responseFlow = flowOf(ConversationResponse(text = "Hello world"))
            conversation.benchmarkSnapshot = null

            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    modelType = ModelType.FAST,
                    modelId = "test-litert",
                    chatId = ChatId("chat-1"),
                    assistantMessageId = MessageId("assistant-1"),
                )

            val events = service.sendPrompt("Hello", options, closeConversation = false).toList()

            assertTrue(events.any { it is InferenceEvent.Finished })
            assertTrue(events.none { it is InferenceEvent.Error })
            val telemetryEvent = events.filterIsInstance<InferenceEvent.TelemetryCaptured>().single()
            assertEquals(2, telemetryEvent.telemetry.outputTokens)
            assertEquals(15, telemetryEvent.telemetry.inputTokens)
        }
}
