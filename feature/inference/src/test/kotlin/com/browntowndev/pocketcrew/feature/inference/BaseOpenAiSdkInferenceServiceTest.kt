package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.AgentFinishReason
import com.browntowndev.pocketcrew.domain.model.inference.AgentLoopMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateFinish
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGateHandle
import com.browntowndev.pocketcrew.domain.model.inference.ToolStreamGatePushResult
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolStreamGatePort
import com.browntowndev.pocketcrew.domain.util.FakeToolStreamGatePort
import com.browntowndev.pocketcrew.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.core.JsonValue
import com.openai.errors.BadRequestException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse

/**
 * Tests for [BaseOpenAiSdkInferenceService] focusing on:
 * - describeException edge cases
 * - mergeSystemPrompt behavior
 * - completeSingleTurn provider-message mapping
 */
class BaseOpenAiSdkInferenceServiceTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    // ── describeException ──────────────────────────────────────────────────────

    @Test
    fun `describeException ignores malformed optional error fields`() {
        val service = createService()
        val exception = mockk<BadRequestException>()
        every { exception.message } returns "400 Bad Request"
        every { exception.statusCode() } returns 400
        every { exception.code() } throws IllegalStateException("code field malformed")
        every { exception.type() } returns java.util.Optional.of("invalid_request_error")
        every { exception.param() } returns java.util.Optional.of("model")
        every { exception.body() } returns JsonValue.from(mapOf("error" to mapOf("message" to "bad request")))

        val description = service.describe(exception)

        assertFalse(description.contains("code="))
        assertFalse(description.contains("IllegalStateException"))
        verify {
            loggingPort.warning(
                "BaseOpenAiSdkInferenceServiceTest",
                match { it.contains("Skipping malformed API error field") },
            )
        }
    }

    @Test
    fun `describeException includes status code and body for OpenAIServiceException`() {
        val service = createService()
        val exception = mockk<BadRequestException>()
        every { exception.message } returns "400 Bad Request"
        every { exception.statusCode() } returns 400
        every { exception.code() } returns java.util.Optional.of("invalid_request")
        every { exception.type() } returns java.util.Optional.of("invalid_request_error")
        every { exception.param() } returns java.util.Optional.of("model")
        every { exception.body() } returns JsonValue.from(mapOf("error" to mapOf("message" to "bad request")))

        val description = service.describe(exception)

        assertTrue(description.contains("exception=BadRequestException"))
        assertTrue(description.contains("message=400 Bad Request"))
        assertTrue(description.contains("status=400"))
        assertTrue(description.contains("code=invalid_request"))
    }

    @Test
    fun `describeException handles non-OpenAI exceptions`() {
        val service = createService()
        val exception = RuntimeException("Connection timeout")

        val description = service.describe(exception)

        assertTrue(description.contains("exception=RuntimeException"))
        assertTrue(description.contains("message=Connection timeout"))
        assertFalse(description.contains("status="))
    }

    // ── completeSingleTurn / toOpenAiChatParam ────────────────────────────────

    @Test
    fun `toOpenAiChatParam maps compact-summary id to system message`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createSingleTurnService(
                    onParams = { capturedParams = it },
                    response = emptyTextResponse(""),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "compact-summary-abc",
                            role = Role.ASSISTANT,
                            content = "Prior summary",
                            metadataJson = "null",
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams!!.messages()
            assertEquals(1, messages.size)
            assertTrue(messages[0].isSystem())
            assertTrue(
                messages[0]
                    .asSystem()
                    .content()
                    .asText()
                    .contains("[Conversation summary]"),
            )
            assertTrue(
                messages[0]
                    .asSystem()
                    .content()
                    .asText()
                    .contains("Prior summary"),
            )
        }

    @Test
    fun `toOpenAiChatParam maps call_id metadata to tool message`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createSingleTurnService(
                    onParams = { capturedParams = it },
                    response = emptyTextResponse(""),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "tool-result-1",
                            role = Role.ASSISTANT,
                            content = """{"result": "ok"}""",
                            metadataJson = """{"call_id": "call_xyz"}""",
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams!!.messages()
            assertEquals(1, messages.size)
            assertTrue(messages[0].isTool())
            assertEquals("call_xyz", messages[0].asTool().toolCallId())
        }

    @Test
    fun `toOpenAiChatParam maps raw_tool_calls metadata to assistant message with tool calls`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createSingleTurnService(
                    onParams = { capturedParams = it },
                    response = emptyTextResponse(""),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-1",
                            role = Role.ASSISTANT,
                            content = "I'll search.",
                            metadataJson = """{"raw_tool_calls": [{"id": "call_1", "name": "web_search", "arguments_json": "{\"q\":\"test\"}"}]}""",
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams!!.messages()
            assertEquals(1, messages.size)
            assertTrue(messages[0].isAssistant())
            val toolCalls = messages[0].asAssistant().toolCalls()
            assertTrue(toolCalls.isPresent)
            assertEquals(1, toolCalls.get().size)
            val functionCall = toolCalls.get()[0].asFunction()
            assertEquals("web_search", functionCall.function().name())
            assertEquals("call_1", functionCall.id())
        }

    @Test
    fun `toOpenAiChatParam maps plain assistant to assistant message without tool calls`() =
        runTest {
            var capturedParams: ChatCompletionCreateParams? = null
            val service =
                createSingleTurnService(
                    onParams = { capturedParams = it },
                    response = emptyTextResponse(""),
                )

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(
                            id = "assistant-plain",
                            role = Role.ASSISTANT,
                            content = "Plain response",
                            metadataJson = "null",
                        ),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = capturedParams!!.messages()
            assertEquals(1, messages.size)
            assertTrue(messages[0].isAssistant())
            assertFalse(messages[0].asAssistant().toolCalls().isPresent)
        }

    @Test
    fun `completeSingleTurn returns Stop finish reason for text-only response`() =
        runTest {
            val service =
                createSingleTurnService(
                    response = emptyTextResponse("Hello there"),
                )

            val result =
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null")),
                    systemPrompt = "",
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )

            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals("Hello there", result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
        }

    @Test
    fun `completeSingleTurn returns ToolUse finish reason for tool call response`() =
        runTest {
            val service =
                createSingleTurnService(
                    response =
                        StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls =
                                listOf(
                                    ToolCallRequest(
                                        toolName = "web_search",
                                        argumentsJson = """{"q":"test"}""",
                                        provider = "TEST",
                                        modelType = ModelType.FAST,
                                    ),
                                ),
                            responseId = "resp_1",
                            providerToolCallIds = listOf("call_abc"),
                            providerToolItemIds = listOf("item_abc"),
                            assistantMessageText = "",
                        ),
                )

            val result =
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "search", metadataJson = "null")),
                    systemPrompt = "",
                    availableTools = emptyList(),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )

            assertEquals(AgentFinishReason.ToolUse, result.finishReason)
            assertEquals(1, result.toolCalls.size)
            assertEquals("web_search", result.toolCalls[0].name)
            assertEquals("call_abc", result.toolCalls[0].id)
            assertEquals(null, result.assistantText)
        }

    @Test
    fun cl_k_04_base_open_ai_does_not_call_tool_stream_gate_port_push() =
        runTest {
            val recordingGate = RecordingToolStreamGatePort()
            val service =
                createSingleTurnService(
                    response =
                        StreamedOpenAiResponse(
                            emittedAny = true,
                            functionCalls = emptyList(),
                            responseId = "resp_stub",
                            providerToolCallIds = emptyList(),
                            providerToolItemIds = emptyList(),
                            assistantMessageText = "ABC",
                        ),
                    toolStreamGatePort = recordingGate,
                )
            val tokens = StringBuilder()
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "hi", metadataJson = "null"),
                    ),
                systemPrompt = "",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = { tokens.append(it) },
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            assertEquals(0, recordingGate.pushCalls.size, "CL-K-04 pushCalls")
            assertEquals("ABC", tokens.toString(), "CL-K-04 onTextToken")
        }

    @Test
    fun `completeSingleTurn submits leaked DSML raw without Kotlin tool reification`() =
        runTest {
            val leakedText =
                """
                < | DSML | tool_calls>< | DSML | invoke name="web_search">
                < | DSML | parameter name="query" string="true">digg.com xAI completes training Grok V9</ | DSML | parameter>
                </ | DSML | invoke></ | DSML | tool_calls>
                """.trimIndent()
            val service =
                createSingleTurnService(
                    response = emptyTextResponse(leakedText),
                )

            val result =
                service.completeSingleTurn(
                    sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "search", metadataJson = "null")),
                    systemPrompt = "",
                    availableTools = listOf(ToolDefinition.WEB_SEARCH),
                    allowToolCall = true,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )

            assertEquals(AgentFinishReason.Stop, result.finishReason)
            assertEquals(leakedText, result.assistantText)
            assertTrue(result.toolCalls.isEmpty())
        }

    @Test
    fun `openAiChatDeltaReasoningText reads OpenRouter reasoning field`() {
        val reasoningText =
            openAiChatDeltaReasoningText(
                mapOf("reasoning" to JsonValue.from("deepseek thought")),
            )

        assertEquals("deepseek thought", reasoningText)
    }

    @Test
    fun `openAiChatDeltaReasoningText reads OpenRouter reasoning details`() {
        val reasoningText =
            openAiChatDeltaReasoningText(
                mapOf(
                    "reasoning_details" to
                        JsonValue.from(
                            listOf(
                                mapOf(
                                    "type" to "reasoning.summary",
                                    "summary" to "summary thought",
                                ),
                                mapOf(
                                    "type" to "reasoning.text",
                                    "text" to "raw thought",
                                ),
                            ),
                        ),
                ),
            )

        assertEquals("summary thoughtraw thought", reasoningText)
    }

    // ── Helper ──────────────────────────────────────────────────────────────────

    private fun createSingleTurnService(
        response: StreamedOpenAiResponse,
        onParams: (ChatCompletionCreateParams) -> Unit = {},
        toolStreamGatePort: ToolStreamGatePort = FakeToolStreamGatePort(),
    ): TestableSingleTurnService =
        TestableSingleTurnService(
            client = client,
            loggingPort = loggingPort,
            stubResponse = response,
            onParams = onParams,
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

    private fun createService(): TestableBaseOpenAiSdkInferenceService =
        TestableBaseOpenAiSdkInferenceService(
            client = client,
            modelId = "openai/gpt-5.2",
            provider = "OPENROUTER",
            modelType = ModelType.THINKING,
            baseUrl = "https://openrouter.ai/api/v1",
            loggingPort = loggingPort,
        )

    private class TestableSingleTurnService(
        client: OpenAIClient,
        loggingPort: LoggingPort,
        private val stubResponse: StreamedOpenAiResponse,
        private val onParams: (ChatCompletionCreateParams) -> Unit,
        toolStreamGatePort: ToolStreamGatePort,
    ) : TestableBaseOpenAiSdkInferenceService(
            client = client,
            modelId = "openai/gpt-5.2",
            provider = "OPENAI",
            modelType = ModelType.FAST,
            baseUrl = null,
            loggingPort = loggingPort,
            toolStreamGatePort = toolStreamGatePort,
        ) {
        override suspend fun streamChatCompletions(
            params: ChatCompletionCreateParams,
            chatId: ChatId?,
            userMessageId: MessageId?,
            assistantMessageId: MessageId?,
            emitEvent: suspend (InferenceEvent) -> Unit,
        ): StreamedOpenAiResponse {
            onParams(params)
            stubResponse.assistantMessageText.takeIf { it.isNotBlank() }?.let {
                emitEvent(InferenceEvent.PartialResponse(it, ModelType.FAST))
            }
            return stubResponse
        }
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

    private open class TestableBaseOpenAiSdkInferenceService(
        client: OpenAIClient,
        modelId: String,
        provider: String,
        modelType: ModelType,
        baseUrl: String?,
        loggingPort: LoggingPort,
        toolStreamGatePort: ToolStreamGatePort = FakeToolStreamGatePort(),
    ) : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = modelId,
            provider = provider,
            modelType = modelType,
            baseUrl = baseUrl,
            loggingPort = loggingPort,
            toolStreamGatePort = toolStreamGatePort,
        ) {
        override val tag: String = "BaseOpenAiSdkInferenceServiceTest"

        fun describe(throwable: Throwable): String = describeException(throwable as Exception)
    }
}
