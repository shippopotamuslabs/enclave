package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.services.blocking.MessageService
import com.anthropic.models.messages.RawMessageStreamEvent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AppendOnlyProviderWiringTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun `anthropic appendOnlyContext clears on rust compaction boundary`() =
        runTest {
            val client = mockk<AnthropicClient>()
            val messageService = mockk<MessageService>()
            val streamResponse = mockk<com.anthropic.core.http.StreamResponse<RawMessageStreamEvent>>()
            every { client.messages() } returns messageService
            every { messageService.createStreaming(any<MessageCreateParams>()) } returns streamResponse
            every { streamResponse.stream() } returns java.util.stream.Stream.empty()
            every { streamResponse.close() } returns Unit

            val service =
                AnthropicInferenceServiceImpl(
                    client = client,
                    modelId = "claude-3-5-sonnet-20241022",
                    modelType = ModelType.MAIN,
                    loggingPort = loggingPort,
                    toolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
                    toolStreamGatePort = FakeToolStreamGatePort(),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "",
                contextSidecars = "State ledger JSON: {}",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val context = requireNotNull(service.appendOnlyContextForTests())
            assertEquals(1, context.committedVolatileSidecarTexts().size)

            service.clearAppendOnlyOnRustCompactionBoundary()
            assertEquals(0, context.committedVolatileSidecarTexts().size)
        }

    @Test
    fun `google appendOnlyContext is assigned during completeSingleTurn`() =
        runTest {
            val sdkClient = mockk<GoogleGenAiSdkClient>()
            coEveryGoogleStream(sdkClient)

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
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "",
                contextSidecars = "State ledger JSON: {}",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertNotNull(service.appendOnlyContextForTests())
        }

    private fun coEveryGoogleStream(sdkClient: GoogleGenAiSdkClient) {
        io.mockk.coEvery {
            sdkClient.generateContentStream(any(), any(), any(), any(), any())
        } returns
            GoogleSdkResult(
                emittedAny = true,
                functionCall = null,
                assistantContent = null,
                finishReason = "STOP",
            )
    }
}
