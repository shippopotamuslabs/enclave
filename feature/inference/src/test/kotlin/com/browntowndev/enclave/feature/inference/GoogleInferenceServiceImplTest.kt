package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.google.genai.types.Content
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleInferenceServiceImplTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `completeSingleTurn maps raw_tool_calls to model function call parts`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val contentsSlot = slot<List<Content>>()
            coEvery {
                sdkClient.generateContentStream(any(), capture(contentsSlot), any(), any(), any())
            } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-tool-call",
                            role = Role.ASSISTANT,
                            content = "Searching.",
                            metadataJson =
                                """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"kotlin\"}"}]}""",
                        ),
                        AgentLoopMessage(id = "user-live", role = Role.USER, content = "Continue"),
                    ),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(2, contentsSlot.captured.size)
            val assistantContent = contentsSlot.captured[0]
            assertEquals("model", assistantContent.role().orElse(null))
            val parts = assistantContent.parts().orElse(emptyList())
            assertTrue(parts.any { it.functionCall().isPresent })
            assertEquals("user", contentsSlot.captured[1].role().orElse(null))
        }

    @Test
    fun `completeSingleTurn maps call_id metadata to function response part`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val contentsSlot = slot<List<Content>>()
            coEvery {
                sdkClient.generateContentStream(any(), capture(contentsSlot), any(), any(), any())
            } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                )

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "tool-result",
                            role = Role.ASSISTANT,
                            content = """{"result":"ok"}""",
                            metadataJson = """{"call_id":"call_xyz"}""",
                        ),
                        AgentLoopMessage(id = "user-live", role = Role.USER, content = "Next"),
                    ),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(2, contentsSlot.captured.size)
            val toolResultContent = contentsSlot.captured[0]
            assertEquals("user", toolResultContent.role().orElse(null))
            val parts = toolResultContent.parts().orElse(emptyList())
            assertTrue(parts.any { it.functionResponse().isPresent })
        }

    @Test
    fun `completeSingleTurn throws InferenceNetworkException when SDK client throws IOException`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            coEvery {
                sdkClient.generateContentStream(any(), any(), any(), any(), any())
            } throws java.io.IOException("Gemini API server down")

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            val exception =
                org.junit.jupiter.api.assertThrows<InferenceNetworkException> {
                    service.completeSingleTurn(
                        sessionMessages = listOf(AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello")),
                        systemPrompt = "",
                        contextSidecars = null,
                        availableTools = emptyList(),
                        allowToolCall = false,
                        onTextToken = {},
                        onThinkingToken = {},
                        options = GenerationOptions(reasoningBudget = 0),
                    )
                }

            assertEquals("GOOGLE", exception.provider)
            assertEquals("[GOOGLE] Network error: Gemini API server down", exception.message)
            assertTrue(exception.cause is java.io.IOException)
        }

    @Test
    fun `completeSingleTurn plumbs telemetry tokens and outcome execution token counts`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val telemetrySupport = mockk<InferenceTelemetrySupport>()
            val telemetryEventPort = mockk<TelemetryEventPort>(relaxed = true)
            val captureContext = mockk<InferenceTelemetrySupport.CaptureContext>()

            every { telemetrySupport.captureContext(any()) } returns captureContext
            every {
                telemetrySupport.openAiTurnTelemetry(
                    context = any(),
                    providerLabel = any(),
                    modelId = any(),
                    modelLabel = any(),
                    timing = any(),
                    inputTokens = any(),
                    outputTokens = any(),
                    reasoningTokens = any(),
                    cacheReadInputTokens = any(),
                    costUsd = null,
                    savedPricing = any(),
                )
            } returns mockk(relaxed = true)

            coEvery {
                sdkClient.generateContentStream(any(), any(), any(), any(), any())
            } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = null,
                    finishReason = "STOP",
                    promptTokens = 15,
                    completionTokens = 25,
                    cachedContentTokens = 5,
                    thoughtsTokens = 8,
                )

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )

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

            io.mockk.verify {
                telemetrySupport.openAiTurnTelemetry(
                    context = captureContext,
                    providerLabel = "Google",
                    modelId = "gemini-2.5-flash",
                    modelLabel = "gemini-2.5-flash",
                    timing = any(),
                    inputTokens = 15,
                    outputTokens = 25,
                    reasoningTokens = 8,
                    cacheReadInputTokens = 5,
                    costUsd = null,
                    savedPricing = null,
                )
            }

            io.mockk.coVerify {
                telemetryEventPort.emitInferenceOutcome(
                    match {
                        it.tokenCount == 25
                    }
                )
            }
        }

    @Test
    fun `completeSingleTurn falls back to outputTokenEstimate when completionTokens is null`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            val telemetrySupport = mockk<InferenceTelemetrySupport>()
            val telemetryEventPort = mockk<TelemetryEventPort>(relaxed = true)
            val captureContext = mockk<InferenceTelemetrySupport.CaptureContext>()

            every { telemetrySupport.captureContext(any()) } returns captureContext
            every {
                telemetrySupport.openAiTurnTelemetry(
                    context = any(),
                    providerLabel = any(),
                    modelId = any(),
                    modelLabel = any(),
                    timing = any(),
                    inputTokens = any(),
                    outputTokens = any(),
                    reasoningTokens = any(),
                    cacheReadInputTokens = any(),
                    costUsd = null,
                    savedPricing = any(),
                )
            } returns mockk(relaxed = true)

            val textPart = mockk<com.google.genai.types.Part>()
            every { textPart.thought() } returns java.util.Optional.of(false)
            every { textPart.text() } returns java.util.Optional.of("Hello world")
            val assistantContent = mockk<com.google.genai.types.Content>()
            every { assistantContent.parts() } returns java.util.Optional.of(listOf(textPart))

            coEvery {
                sdkClient.generateContentStream(any(), any(), any(), any(), any())
            } returns
                GoogleSdkResult(
                    emittedAny = true,
                    functionCall = null,
                    assistantContent = assistantContent,
                    finishReason = "STOP",
                    promptTokens = null,
                    completionTokens = null,
                    cachedContentTokens = null,
                    thoughtsTokens = null,
                )

            val service =
                GoogleInferenceServiceImpl(
                    sdkClient = sdkClient,
                    modelId = "gemini-2.5-flash",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                    telemetrySupport = telemetrySupport,
                    telemetryEventPort = telemetryEventPort,
                )

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

            // "Hello world" is 11 characters. 11 / 4 = 2.
            val expectedEstimate = 2

            io.mockk.verify {
                telemetrySupport.openAiTurnTelemetry(
                    context = captureContext,
                    providerLabel = "Google",
                    modelId = "gemini-2.5-flash",
                    modelLabel = "gemini-2.5-flash",
                    timing = any(),
                    inputTokens = null,
                    outputTokens = null,
                    reasoningTokens = null,
                    cacheReadInputTokens = null,
                    costUsd = null,
                    savedPricing = null,
                )
            }

            io.mockk.coVerify {
                telemetryEventPort.emitInferenceOutcome(
                    match {
                        it.tokenCount == expectedEstimate
                    }
                )
            }
        }
}
