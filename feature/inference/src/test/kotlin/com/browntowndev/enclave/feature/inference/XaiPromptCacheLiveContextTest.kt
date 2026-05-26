package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.util.FakeToolStreamGatePort
import com.browntowndev.enclave.feature.inference.openai.PreRetrievalContextSidecar
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.chat.completions.ChatCompletionMessageParam
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PC-XAI live-context scenarios: multi-call tool loops and a new user turn after
 * [resetAppendOnlyForNewPrompt] (same boundary as [RustLoopInferenceService.sendPrompt]).
 *
 * These prove **cache-eligible wire prefix** stability (transcript + stable system + tools[]).
 * They do not assert provider [cacheReadInputTokens] — that requires a live API or usage mocks.
 */
class XaiPromptCacheLiveContextTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    private val stableTools =
        listOf(
            ToolDefinition.WEB_SEARCH,
            ToolDefinition.APPEND_BLOCKS,
        )

    private val chatOptions =
        GenerationOptions(
            reasoningBudget = 0,
            chatId = ChatId("chat-live-context"),
            activeTools = stableTools,
        )

  @Test
    fun PC_XAI_07_user_turn_tool_loop_then_new_user_turn_preserves_cache_eligible_transcript_prefix() =
        runTest {
            val captured = mutableListOf<ChatCompletionCreateParams>()
            val service = capturingXaiService(captured)
            val fixture = liveToolLoopFixture()

            // ── User turn 1: tool loop (3 model calls, same sendPrompt session) ──
            service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(1),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(2),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.assistantSummary,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(3),
                availableTools = stableTools,
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val turnOneFinal = captured.last()
            val turnOneEligible = turnOneFinal.messages().cacheEligibleWire()
            val turnOneTools = turnOneFinal.tools().get().map { it.asFunction().function().name() }

            // ── New user prompt boundary (RustLoop sendPrompt start) ──
            service.resetAppendOnlyForNewPrompt()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.assistantSummary,
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow-up question"),
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars =
                    buildContextSidecars(
                        ledgerTurn = 4,
                        toolIndex = """{"advertised_tools":["web_search","append_blocks"]}""",
                    ),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val turnTwoFirst = captured.last()
            val turnTwoEligible = turnTwoFirst.messages().cacheEligibleWire()
            val turnOneWire = turnOneFinal.messages().map { it.asWireText() }
            val turnTwoWireLines = turnTwoFirst.messages().map { it.asWireText() }

            assertEquals(
                listOf("chat-live-context"),
                turnOneFinal._headers().values("x-grok-conv-id"),
                "turn 1 must attach conversation id for xAI cache routing",
            )
            assertEquals(
                listOf("chat-live-context"),
                turnTwoFirst._headers().values("x-grok-conv-id"),
            )
            assertEquals(turnOneTools, turnTwoFirst.tools().get().map { it.asFunction().function().name() })

            assertTrue(
                turnTwoEligible.size > turnOneEligible.size,
                "turn 2 must extend turn 1 transcript",
            )
            val sharedPrefixLength = turnOneEligible.size
            for (index in 0 until sharedPrefixLength) {
                assertEquals(
                    turnOneEligible[index],
                    turnTwoEligible[index],
                    "cache-eligible wire mismatch at transcript index $index " +
                        "(provider prefix cache would miss from this byte)",
                )
            }
            for (index in turnOneWire.indices) {
                assertEquals(
                    turnOneWire[index],
                    turnTwoWireLines[index],
                    "real xAI messages[] prefix mismatch at index $index",
                )
            }

            val turnTwoWire = turnTwoFirst.messages()
            assertTrue(
                turnTwoWire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == ledgerSidecar(1)
                },
                "prior sidecars are part of the xAI messages[] prefix and must be replayed",
            )
            assertTrue(
                turnTwoEligible.any { it.contains("Follow-up question") },
            )
            assertTrue(
                turnTwoWire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText().contains("advertised_tools")
                },
                "current-turn tool index sidecar must be present on the live wire tail",
            )
        }

    @Test
    fun PC_XAI_09_tool_result_json_unchanged_across_in_turn_model_calls() =
        runTest {
            val captured = mutableListOf<ChatCompletionCreateParams>()
            val service = capturingXaiService(captured)
            val fixture = liveToolLoopFixture()
            val toolResultJson = """{"results":[{"title":"Cat"}]}"""

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(2),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val afterTools = captured.last().messages().first { it.isTool() }

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.assistantSummary,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(3),
                availableTools = stableTools,
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val afterSummary =
                captured
                    .last()
                    .messages()
                    .first { it.isTool() && it.asTool().content().asText() == toolResultJson }

            assertEquals(
                afterTools.asTool().toolCallId(),
                afterSummary.asTool().toolCallId(),
            )
            assertEquals(toolResultJson, afterSummary.asTool().content().asText())
        }

    @Test
    fun PC_XAI_11_pre_tool_request_bytes_are_prefix_of_post_tool_request() =
        runTest {
            val captured = mutableListOf<ChatCompletionCreateParams>()
            val service = capturingXaiService(captured)
            val fixture = liveToolLoopFixture()

            service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(1),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val preTool = captured.last().messages()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.agentBudgetMessage,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars =
                    buildContextSidecars(
                        ledgerTurn = 2,
                        toolIndex = """{"advertised_tools":["web_search"]}""",
                    ),
                availableTools = stableTools,
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val postTool = captured.last().messages()
            val preToolParams = captured[captured.size - 2]
            val postToolParams = captured.last()

            assertTrue(
                postTool.size > preTool.size,
                "post-tool wire must extend pre-tool wire",
            )
            assertEquals(
                preToolParams.tools().orElse(null),
                postToolParams.tools().orElse(null),
                "tools[] must be byte-stable when toolChoice flips to none",
            )
            for (index in preTool.indices) {
                assertEquals(
                    preTool[index].asWireText(),
                    postTool[index].asWireText(),
                    "xAI cache requires request[$index] bytes unchanged after tools; was invalidated",
                )
            }
        }

    @Test
    fun PC_XAI_08_in_turn_tool_loop_preserves_cache_eligible_prefix_and_tools_with_ledger() =
        runTest {
            val captured = mutableListOf<ChatCompletionCreateParams>()
            val service = capturingXaiService(captured)
            val fixture = liveToolLoopFixture()

            service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(1),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val toolsCall1 =
                captured.last().tools().get().map { it.asFunction().function().name() }

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars =
                    buildContextSidecars(
                        ledgerTurn = 2,
                        toolIndex = """{"advertised_tools":["web_search"]}""",
                    ),
                availableTools = stableTools,
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val call2Eligible = captured.last().messages().cacheEligibleWire()
            val toolsCall2 =
                captured.last().tools().get().map { it.asFunction().function().name() }

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.assistantSummary,
                    ),
                systemPrompt = fixture.systemPrompt,
                contextSidecars = ledgerSidecar(3),
                availableTools = stableTools,
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = chatOptions,
            )
            val call3Eligible = captured.last().messages().cacheEligibleWire()

            assertEquals(toolsCall1, toolsCall2, "tools[] must be identical across in-turn model calls")
            assertTrue(call3Eligible.size >= call2Eligible.size)
            for (index in call2Eligible.indices) {
                assertEquals(
                    call2Eligible[index],
                    call3Eligible[index],
                    "in-turn cache-eligible prefix must not shift when post-tool assistant is added",
                )
            }
        }

    private data class LiveToolLoopFixture(
        val systemPrompt: String,
        val userMessage: AgentLoopMessage,
        val assistantWithTool: AgentLoopMessage,
        val toolResult: AgentLoopMessage,
        val agentBudgetMessage: AgentLoopMessage,
        val assistantSummary: AgentLoopMessage,
    )

    private fun liveToolLoopFixture(): LiveToolLoopFixture =
        LiveToolLoopFixture(
            systemPrompt = "You are a helpful assistant.",
            userMessage =
                AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
            assistantWithTool =
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson =
                        """
                        {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                        """.trimIndent(),
                ),
            toolResult =
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"results":[{"title":"Cat"}]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
            agentBudgetMessage =
                AgentLoopMessage(
                    id = "tool-agent-budget-0-0",
                    role = Role.USER,
                    content = """{"_agent":{"tool_calls_used":1,"tool_calls_limit":35,"allow_tool_call":true}}""",
                    metadataJson = """{"agent_tool_budget":true}""",
                ),
            assistantSummary =
                AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Here are cat results."),
        )

    private fun ledgerSidecar(turn: Int): String = """State ledger JSON: {"turn":$turn}"""

    private fun buildContextSidecars(
        ledgerTurn: Int,
        toolIndex: String? = null,
    ): String =
        buildString {
            appendLine("Context management sidecars:")
            appendLine("State ledger JSON: {\"turn\":$ledgerTurn}")
            toolIndex?.let { appendLine("Tool context index JSON: $it") }
        }.trimEnd()

    private fun capturingXaiService(
        captured: MutableList<ChatCompletionCreateParams>,
    ): BaseOpenAiSdkInferenceService =
        object : BaseOpenAiSdkInferenceService(
            client = mockk<OpenAIClient>(),
            modelId = "grok-4.3",
            provider = "XAI",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
            override val tag: String = "XaiPromptCacheLiveContextTest"

            override suspend fun streamChatCompletions(
                params: ChatCompletionCreateParams,
                chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse {
                captured.add(params)
                return StreamedOpenAiResponse(
                    emittedAny = true,
                    functionCalls = emptyList(),
                    responseId = "resp_1",
                    providerToolCallIds = emptyList(),
                    providerToolItemIds = emptyList(),
                    assistantMessageText = "ok",
                )
            }
        }

    private fun List<ChatCompletionMessageParam>.cacheEligibleWire(): List<String> =
        filterNot { it.isVolatileSidecar() }.map { it.toCacheEligibleWireLine() }

    private fun ChatCompletionMessageParam.isVolatileSidecar(): Boolean {
        if (!isSystem()) return false
        val text = asSystem().content().asText()
        return PreRetrievalContextSidecar.isSidecarText(text) ||
            text.startsWith("State ledger JSON:") ||
            text.startsWith("Context management sidecars:") ||
            text.startsWith("Tool context index JSON:") ||
            text.startsWith("File context index JSON:") ||
            text.startsWith("Cache hints:")
    }

    private fun ChatCompletionMessageParam.toCacheEligibleWireLine(): String =
        when {
            isSystem() -> "SYSTEM:${asSystem().content().asText()}"
            isUser() -> "USER:${asUser().content().asText()}"
            isAssistant() -> {
                val assistant = asAssistant()
                val tools =
                    if (assistant.toolCalls().isPresent) {
                        assistant.toolCalls().get().joinToString { tc ->
                            tc.asFunction().function().name()
                        }
                    } else {
                        ""
                    }
                val text =
                    if (assistant.content().isPresent && assistant.content().get().isText()) {
                        assistant.content().get().asText()
                    } else {
                        ""
                    }
                "ASSISTANT:text=$text;tools=$tools"
            }
            isTool() -> "TOOL:${asTool().toolCallId()}:${asTool().content().asText()}"
            else -> toString()
        }

    private fun ChatCompletionMessageParam.asWireText(): String =
        when {
            isSystem() -> "SYSTEM:${asSystem().content().asText()}"
            isUser() -> "USER:${asUser().content().asText()}"
            isAssistant() -> "ASSISTANT:${asAssistant().toolCalls().isPresent}"
            isTool() -> "TOOL:${asTool().toolCallId()}:${asTool().content().asText()}"
            else -> toString()
        }
}
