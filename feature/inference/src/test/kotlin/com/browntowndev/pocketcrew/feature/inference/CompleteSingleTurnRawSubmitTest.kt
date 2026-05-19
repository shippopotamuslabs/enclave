package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.AgentRawToolCall
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeBatch
import com.browntowndev.pocketcrew.domain.model.inference.ToolEnvelopeField
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.browntowndev.pocketcrew.domain.util.FakeToolEnvelopeParserPort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * TOOL-PARSE-002 K-03, K-04, MD-01 — `completeSingleTurn` submits raw buffered text
 * and native tools only; never parses assistant envelopes in Kotlin.
 */
class CompleteSingleTurnRawSubmitTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    private val dsmlOnly =
        """
        <|DSML|tool_calls><|DSML|invoke name="web_search">
        <|DSML|parameter name="query" string="true">kotlin</|DSML|parameter>
        </|DSML|invoke></|DSML|tool_calls>
        """.trimIndent()

    @Test
    fun cl_k_06_complete_single_turn_never_calls_tool_envelope_parser_port() =
        runTest {
            val fakePort = RecordingEnvelopeParserPort()
            val service =
                createSingleTurnService(
                    response = emptyTextResponse(dsmlOnly),
                    toolEnvelopeParserPort = fakePort,
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
                options = GenerationOptions(modelId = "openai/gpt-5.2", reasoningBudget = 0),
            )

            assertEquals(0, fakePort.parseCalls.size, "CL-K-06 parseCalls")
        }

    @Test
    fun k04NativeToolsOnlyWhenStreamAlsoContainsDsml() =
        runTest {
            val fakePort = FakeToolEnvelopeParserPort()
            fakePort.parseResponses[dsmlOnly] =
                ToolEnvelopeBatch(
                    successes =
                        listOf(
                            ToolEnvelopeField(
                                toolName = "append_blocks",
                                argumentsJson = """{"artifactId":"a1","blocks":[]}""",
                                repaired = false,
                                repairs = emptyList(),
                            ),
                        ),
                    failures = emptyList(),
                    visiblePrefix = "",
                    visibleSuffix = "",
                )
            val native =
                AgentRawToolCall(
                    id = "call_native",
                    name = "web_search",
                    argumentsJson = """{"query":"kotlin"}""",
                )
            val service =
                createSingleTurnService(
                    response =
                        StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls =
                                listOf(
                                    ToolCallRequest(
                                        toolName = "web_search",
                                        argumentsJson = """{"query":"kotlin"}""",
                                        provider = "OPENROUTER",
                                        modelType = ModelType.FAST,
                                    ),
                                ),
                            responseId = "resp_1",
                            providerToolCallIds = listOf("call_native"),
                            providerToolItemIds = emptyList(),
                            assistantMessageText = dsmlOnly,
                        ),
                    toolEnvelopeParserPort = fakePort,
                )

            val result =
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
                    options = GenerationOptions(modelId = "openai/gpt-5.2", reasoningBudget = 0),
                )

            assertEquals(1, result.toolCalls.size, "K-04 toolCalls.size")
            assertEquals("web_search", result.toolCalls[0].name, "K-04 native tool name")
            assertEquals(dsmlOnly, result.assistantText, "K-04 raw assistantText for engine")
            assertEquals(0, fakePort.parseCalls.size, "K-04 must not merge leaked tools via parse")
        }

    @Test
    fun md01AgentLoopNeverParsesAssistantTextInKotlin() =
        runTest {
            val fakePort = FakeToolEnvelopeParserPort()
            val service =
                createSingleTurnService(
                    response = emptyTextResponse(dsmlOnly),
                    toolEnvelopeParserPort = fakePort,
                )

            service.completeSingleTurn(
                sessionMessages = emptyList(),
                systemPrompt = "sys",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(modelId = "openai/gpt-5.2", reasoningBudget = 0),
            )

            assertEquals(0, fakePort.parseCalls.size, "MD-01 parseCallCount")
        }

    private class RecordingEnvelopeParserPort : ToolEnvelopeParserPort {
        val parseCalls = mutableListOf<String>()

        override fun parse(raw: String): ToolEnvelopeBatch {
            parseCalls.add(raw)
            error("MD-01/K-03: parse must not run in completeSingleTurn")
        }

        override fun coerceProviderArguments(
            toolName: String,
            argsJson: String,
        ) = ToolEnvelopeField(toolName, argsJson, false, emptyList())

        override fun hasLocalToolContract(systemPrompt: String?) = false

        override fun buildToolResultMessage(resultJson: String) = resultJson
    }

    private fun createSingleTurnService(
        response: StreamedOpenAiResponse,
        toolEnvelopeParserPort: ToolEnvelopeParserPort = FakeToolEnvelopeParserPort(),
        toolStreamGatePort: ToolStreamGatePort = FakeToolStreamGatePort(),
    ): TestableSingleTurnService =
        TestableSingleTurnService(
            client = client,
            loggingPort = loggingPort,
            stubResponse = response,
            onParams = {},
            toolStreamGatePort = toolStreamGatePort,
        )

    private fun emptyTextResponse(text: String = "") =
        StreamedOpenAiResponse(
            emittedAny = text.isNotEmpty(),
            functionCalls = emptyList(),
            responseId = "resp_stub",
            providerToolCallIds = emptyList(),
            providerToolItemIds = emptyList(),
            assistantMessageText = text,
        )

    private class TestableSingleTurnService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        private val stubResponse: StreamedOpenAiResponse,
        private val onParams: (ChatCompletionCreateParams) -> Unit,
        toolStreamGatePort: ToolStreamGatePort,
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "openai/gpt-5.2",
            provider = "OPENROUTER",
            modelType = ModelType.FAST,
            baseUrl = "https://openrouter.ai/api/v1",
            loggingPort = loggingPort,
            toolStreamGatePort = toolStreamGatePort,
        ) {
        override val tag: String = "CompleteSingleTurnRawSubmitTest"

        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: com.browntowndev.pocketcrew.domain.model.chat.ChatId?,
            userMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId?,
            assistantMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse {
            onParams(params)
            stubResponse.assistantMessageText.takeIf { it.isNotBlank() }?.let {
                emitEvent(InferenceEvent.PartialResponse(it, ModelType.FAST))
            }
            return stubResponse
        }
    }
}
