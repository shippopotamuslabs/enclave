package com.browntowndev.enclave.feature.inference

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.browntowndev.enclave.core.testing.FakeLoggingPort
import com.browntowndev.enclave.core.testing.FakeTelemetryEventPort
import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.InferenceNetworkException
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.telemetry.TelemetryClientBuild
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import com.browntowndev.enclave.domain.model.telemetry.TelemetrySourceKind
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetry
import com.browntowndev.enclave.domain.model.telemetry.TurnTelemetryId
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.telemetry.InferenceTelemetrySupport
import com.google.genai.types.Content
import com.google.genai.types.Part
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class GoogleInferenceServiceImplTest {
    private val loggingPort = FakeLoggingPort()

    @Test
    fun `completeSingleTurn maps raw_tool_calls to model function call parts`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            sdkClient.streamResult =
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

            val capturedContents = sdkClient.lastGenerateContents!!
            assertEquals(2, capturedContents.size)
            val assistantContent = capturedContents[0]
            assertEquals("model", assistantContent.role().orElse(null))
            val parts = assistantContent.parts().orElse(emptyList())
            assertTrue(parts.any { it.functionCall().isPresent })
            assertEquals("user", capturedContents[1].role().orElse(null))
        }

    @Test
    fun `completeSingleTurn maps agent tool budget sidecar to user infrastructure not model`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            sdkClient.streamResult =
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
            val budgetContent =
                "<system_context>\nTool budget: 3/50 used (47 remaining). Tools enabled.\n</system_context>"

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "user-live", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(
                            id = "budget",
                            role = Role.ASSISTANT,
                            content = budgetContent,
                            metadataJson = """{"agent_tool_budget":true}""",
                        ),
                    ),
                systemPrompt = "",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val capturedContents = sdkClient.lastGenerateContents!!
            assertEquals(2, capturedContents.size)
            assertEquals("user", capturedContents[0].role().orElse(null))
            assertEquals("user", capturedContents[1].role().orElse(null))
            val budgetWireText =
                capturedContents[1]
                    .parts()
                    .orElse(emptyList())
                    .single()
                    .text()
                    .orElse("")
            assertEquals(budgetContent, budgetWireText)
            assertEquals(false, budgetWireText.contains(""""_agent""""))
        }

    @Test
    fun `completeSingleTurn maps call_id metadata to function response part`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            sdkClient.streamResult =
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

            val capturedContents = sdkClient.lastGenerateContents!!
            assertEquals(2, capturedContents.size)
            val toolResultContent = capturedContents[0]
            assertEquals("user", toolResultContent.role().orElse(null))
            val parts = toolResultContent.parts().orElse(emptyList())
            assertTrue(parts.any { it.functionResponse().isPresent })
        }

    @Test
    fun `completeSingleTurn throws InferenceNetworkException when SDK client throws IOException`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            sdkClient.streamFailure = java.io.IOException("Gemini API server down")

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
                assertThrows<InferenceNetworkException> {
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
            val sdkClient = FakeGoogleGenAiSdkClient()
            val telemetrySupport = fakeTelemetrySupport()
            val telemetryEventPort = FakeTelemetryEventPort()
            val captureContext = captureContext()
            telemetrySupport.captureContextResult = captureContext

            sdkClient.streamResult =
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
                    assistantMessageId = MessageId("msg_1"),
                    chatId = ChatId("chat_1"),
                ),
            )

            val telemetryCall = telemetrySupport.openAiCalls.single()
            assertEquals(captureContext, telemetryCall.context)
            assertEquals("Google", telemetryCall.providerLabel)
            assertEquals("gemini-2.5-flash", telemetryCall.modelId)
            assertEquals("gemini-2.5-flash", telemetryCall.modelLabel)
            assertEquals(15, telemetryCall.inputTokens)
            assertEquals(25, telemetryCall.outputTokens)
            assertEquals(8, telemetryCall.reasoningTokens)
            assertEquals(5, telemetryCall.cacheReadInputTokens)
            assertEquals(null, telemetryCall.costUsd)
            assertEquals(null, telemetryCall.savedPricing)

            assertEquals(25, telemetryEventPort.emittedInferenceOutcomes.single().tokenCount)
        }

    @Test
    fun `completeSingleTurn falls back to outputTokenEstimate when completionTokens is null`() =
        runTest {
            val sdkClient = FakeGoogleGenAiSdkClient()
            val telemetrySupport = fakeTelemetrySupport()
            val telemetryEventPort = FakeTelemetryEventPort()
            val captureContext = captureContext()
            telemetrySupport.captureContextResult = captureContext

            val assistantContent =
                Content
                    .builder()
                    .parts(
                        listOf(
                            Part
                                .builder()
                                .text("Hello world")
                                .thought(false)
                                .build(),
                        ),
                    ).build()

            sdkClient.streamResult =
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
                    assistantMessageId = MessageId("msg_1"),
                    chatId = ChatId("chat_1"),
                ),
            )

            // "Hello world" is 11 characters. 11 / 4 = 2.
            val expectedEstimate = 2

            val telemetryCall = telemetrySupport.openAiCalls.single()
            assertEquals(captureContext, telemetryCall.context)
            assertEquals("Google", telemetryCall.providerLabel)
            assertEquals("gemini-2.5-flash", telemetryCall.modelId)
            assertEquals("gemini-2.5-flash", telemetryCall.modelLabel)
            assertEquals(null, telemetryCall.inputTokens)
            assertEquals(null, telemetryCall.outputTokens)
            assertEquals(null, telemetryCall.reasoningTokens)
            assertEquals(null, telemetryCall.cacheReadInputTokens)
            assertEquals(null, telemetryCall.costUsd)
            assertEquals(null, telemetryCall.savedPricing)

            assertEquals(expectedEstimate, telemetryEventPort.emittedInferenceOutcomes.single().tokenCount)
        }

    private fun captureContext(): InferenceTelemetrySupport.CaptureContext =
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

    private fun fakeTelemetrySupport(): FakeInferenceTelemetrySupport {
        val telemetrySupport =
            FakeInferenceTelemetrySupport(ApplicationProvider.getApplicationContext<Context>())
        telemetrySupport.openAiTelemetryBuilder = { call ->
            val cc = call.context
            TurnTelemetry(
                id = TurnTelemetryId("test"),
                messageId = cc.messageId,
                chatId = cc.chatId,
                sequenceIndex = cc.sequenceIndex,
                providerKey = TelemetryProviderKey.GOOGLE,
                providerLabel = call.providerLabel,
                modelId = call.modelId,
                modelLabel = call.modelLabel,
                sourceKind = TelemetrySourceKind.REMOTE,
                requestStartedAtMs = 0L,
                firstOutputTokenAtMs = null,
                completedAtMs = 1L,
                inputTokens = call.inputTokens,
                outputTokens = call.outputTokens,
                reasoningTokens = call.reasoningTokens,
                cachedInputTokens = null,
                cacheReadInputTokens = call.cacheReadInputTokens,
                cacheWriteInputTokens = call.cacheWriteInputTokens,
                cacheCreationInputTokens = null,
                costUsd = call.costUsd,
                costStatus = TelemetryCostStatus.UNAVAILABLE,
                costSource = TelemetryCostSource.NOT_AVAILABLE,
                clientBuild = cc.clientBuild,
            )
        }
        return telemetrySupport
    }
}
