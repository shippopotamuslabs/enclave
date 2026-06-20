package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression coverage for the system-prompt prepend decision across a Rust agent-loop
 * tool-call sub-turn (seq=0 model request → tool execution → seq=1 model request with
 * tool results). DeepSeek prompt caching is prefix-based, so the stable system message
 * must remain at wire index 0 on every sub-turn; if it vanishes the cache prefix breaks.
 *
 * Root cause under test: [BaseOpenAiSdkInferenceService.isStableSessionSystemMessage]
 * classifies an engine-internal system message whose content collapses to `""` (an
 * `_agent` payload run through [EngineInternalMessageMapper.formatForProviderWire]) as a
 * *stable* session system message, because [EngineInternalMessageMapper.isEngineInternalWireText]
 * only matches the `<system_context>` prefix and returns false for `""`. That false
 * positive suppresses the stable system-prompt prepend, dropping the system message
 * off the wire and breaking the cache prefix.
 */
class SystemPromptPrependToolTurnRegressionTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)
    private val client = mockk<OpenAIClient>()

    private val rawToolCallsMetadata =
        """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"test\"}"}]}"""

    @Test
    fun `system prompt is prepended on tool-result sub-turn with no system messages in session`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = createTestService(capturedParams)

            // seq=1 of a sendPrompt: assistant tool call + tool result, NO Role.SYSTEM messages.
            val toolResultTurn =
                listOf(
                    AgentLoopMessage(
                        id = "assistant-tool-call",
                        role = Role.ASSISTANT,
                        content = "",
                        metadataJson = rawToolCallsMetadata,
                    ),
                    AgentLoopMessage(
                        id = "tool-result-1",
                        role = Role.ASSISTANT,
                        content = """{"result": "ok"}""",
                        metadataJson = """{"call_id": "call_1"}""",
                    ),
                )

            service.completeSingleTurn(
                sessionMessages = toolResultTurn,
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-regression")),
            )

            assertEquals(1, capturedParams.size)
            val messages = capturedParams[0].messages()
            // System prompt MUST be prepended at index 0 even when session has no system messages.
            assertTrue(messages.isNotEmpty(), "messages must not be empty")
            assertTrue(
                messages[0].isSystem(),
                "expected system message at index 0 but got ${messages[0]::class.simpleName}",
            )
            assertTrue(
                messages[0].asSystem().content().asText().contains("helpful assistant"),
                "system prompt content must be prepended",
            )
        }

    @Test
    fun `system prompt is prepended when engine-internal _agent payload collapses to empty system message`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = createTestService(capturedParams)

            // seq=1 of a sendPrompt where the engine appended an _agent-payload system message
            // after the tool result (budget/nudge). formatForProviderWire collapses it to "".
            val toolResultTurnWithAgentPayload =
                listOf(
                    AgentLoopMessage(
                        id = "assistant-tool-call",
                        role = Role.ASSISTANT,
                        content = "",
                        metadataJson = rawToolCallsMetadata,
                    ),
                    AgentLoopMessage(
                        id = "tool-result-1",
                        role = Role.ASSISTANT,
                        content = """{"result": "ok"}""",
                        metadataJson = """{"call_id": "call_1"}""",
                    ),
                    AgentLoopMessage(
                        id = "agent-budget",
                        role = Role.ASSISTANT,
                        content = """{"_agent":{"tool_calls_used":1,"allow_tool_call":true}}""",
                        metadataJson = """{"agent_tool_budget":true}""",
                    ),
                )

            service.completeSingleTurn(
                sessionMessages = toolResultTurnWithAgentPayload,
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-regression")),
            )

            assertEquals(1, capturedParams.size)
            val messages = capturedParams[0].messages()
            // The empty-content engine-internal system message must NOT suppress the prepend.
            assertTrue(
                messages.isNotEmpty(),
                "messages must not be empty",
            )
            assertTrue(
                messages[0].isSystem(),
                "expected stable system prompt at index 0 but got ${messages[0]::class.simpleName} " +
                    "(engine-internal empty system message likely suppressed prepend)",
            )
            assertTrue(
                messages[0].asSystem().content().asText().contains("helpful assistant"),
                "stable system prompt content must be prepended",
            )
        }

    @Test
    fun `system prompt is prepended when system_context engine-internal message is present`() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = createTestService(capturedParams)

            // Control: a <system_context> engine-internal message IS correctly classified
            // volatile, so the prepend must still fire.
            val toolResultTurnWithSystemContext =
                listOf(
                    AgentLoopMessage(
                        id = "assistant-tool-call",
                        role = Role.ASSISTANT,
                        content = "",
                        metadataJson = rawToolCallsMetadata,
                    ),
                    AgentLoopMessage(
                        id = "tool-result-1",
                        role = Role.ASSISTANT,
                        content = """{"result": "ok"}""",
                        metadataJson = """{"call_id": "call_1"}""",
                    ),
                    AgentLoopMessage(
                        id = "agent-budget",
                        role = Role.ASSISTANT,
                        content =
                            "<system_context>\nTool budget: 1/35 used (34 remaining). Tools enabled.\n</system_context>",
                        metadataJson = """{"agent_tool_budget":true}""",
                    ),
                )

            service.completeSingleTurn(
                sessionMessages = toolResultTurnWithSystemContext,
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-regression")),
            )

            assertEquals(1, capturedParams.size)
            val messages = capturedParams[0].messages()
            assertTrue(messages[0].isSystem(), "stable system prompt must be at index 0")
            assertTrue(
                messages[0].asSystem().content().asText().contains("helpful assistant"),
                "stable system prompt content must be prepended",
            )
        }

    private fun createTestService(
        capturedParams: MutableList<ChatCompletionCreateParams>,
    ): BaseOpenAiSdkInferenceService =
        object : BaseOpenAiSdkInferenceService(
            client = client,
            modelId = "deepseek-chat",
            provider = "DEEPSEEK",
            modelType = ModelType.FAST,
            baseUrl = "https://api.deepseek.com/v1",
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
            override val tag: String = "SystemPromptPrependToolTurnRegressionTest"

            override suspend fun streamChatCompletions(
                params: ChatCompletionCreateParams,
                chatId: ChatId?,
                userMessageId: MessageId?,
                assistantMessageId: MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse {
                capturedParams.add(params)
                return StreamedOpenAiResponse(
                    emittedAny = true,
                    functionCalls = emptyList(),
                    responseId = "resp_stub",
                    providerToolCallIds = emptyList(),
                    providerToolItemIds = emptyList(),
                    assistantMessageText = "mock-response",
                )
            }

            override suspend fun streamResponses(
                params: ResponseCreateParams,
                chatId: ChatId?,
                userMessageId: MessageId?,
                assistantMessageId: MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse = error("responses path not used")
        }
}