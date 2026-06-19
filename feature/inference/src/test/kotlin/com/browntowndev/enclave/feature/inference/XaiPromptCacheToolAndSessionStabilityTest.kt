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
import com.browntowndev.enclave.feature.inference.openai.StreamedOpenAiResponse
import com.openai.client.OpenAIClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * PC-XAI: xAI/OpenAI prefix cache requires stable tools[] and append-only provider-visible
 * sidecars across user prompts.
 */
class XaiPromptCacheToolAndSessionStabilityTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun PC_XAI_05_tools_array_identical_when_rust_active_schema_grows() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiService(capturedParams)

            val options =
                GenerationOptions(
                    reasoningBudget = 0,
                    activeTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
                )
            val baseMessages =
                listOf(
                    AgentLoopMessage(id = "u1", role = Role.USER, content = "Search"),
                )

            service.completeSingleTurn(
                sessionMessages = baseMessages,
                systemPrompt = "System",
                contextSidecars = null,
                availableTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            val toolsTurn1 =
                capturedParams
                    .last()
                    .tools()
                    .get()
                    .map { tool -> tool.asFunction().function().name() }

            service.completeSingleTurn(
                sessionMessages =
                    baseMessages +
                        listOf(
                            AgentLoopMessage(
                                id = "a1",
                                role = Role.ASSISTANT,
                                content = "",
                                metadataJson =
                                    """
                                    {"raw_tool_calls":[{"id":"c1","name":"web_search","arguments_json":"{}"}]}
                                    """.trimIndent(),
                            ),
                            AgentLoopMessage(
                                id = "t1",
                                role = Role.USER,
                                content = """{"ok":true}""",
                                metadataJson = """{"call_id":"c1"}""",
                            ),
                        ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = listOf(ToolDefinition.WEB_SEARCH, ToolDefinition.APPEND_BLOCKS),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            val toolsTurn2 =
                capturedParams
                    .last()
                    .tools()
                    .get()
                    .map { tool -> tool.asFunction().function().name() }

            assertEquals(toolsTurn1, toolsTurn2, "tools[] must not shrink or grow when only Rust active schema changes")
        }

    @Test
    fun PC_XAI_06_resetAppendOnlyForNewPrompt_preserves_same_chat_frozen_sidecars() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiService(capturedParams)
            val options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-1"))

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "First question"),
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            assertTrue(
                requireNotNull(service.appendOnlyContextForTests()).committedVolatileSidecarTexts().isNotEmpty(),
            )

            service.resetAppendOnlyForNewPrompt()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "First question"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "First answer"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second question"),
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )

            val wire = capturedParams.last().messages()
            assertTrue(
                wire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":1}"""
                },
                "same-chat frozen sidecars must replay because xAI caches the real messages[] prefix",
            )
            assertTrue(
                wire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":2}"""
                },
            )
        }

    @Test
    fun PC_XAI_06b_frozen_sidecars_are_isolated_by_chat_id() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiService(capturedParams)

            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "Chat one")),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"chat":1}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-1")),
            )
            service.resetAppendOnlyForNewPrompt()

            service.completeSingleTurn(
                sessionMessages = listOf(AgentLoopMessage(id = "u2", role = Role.USER, content = "Chat two")),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"chat":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-2")),
            )

            val wire = capturedParams.last().messages()
            assertTrue(
                wire.none { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"chat":1}"""
                },
                "frozen sidecars must not leak between x-grok-conv-id values",
            )
            assertTrue(
                wire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"chat":2}"""
                },
            )
        }

    @Test
    fun PC_XAI_08_cross_turn_tool_budget_does_not_clear_append_only_log() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiService(capturedParams)
            val options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-1"))
            val fixture = toolLoopWithBudgetFixture()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.agentBudgetMessage,
                        fixture.assistantSummary,
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            val appendOnly = requireNotNull(service.appendOnlyContextForTests())
            assertTrue(appendOnly.committedVolatileSidecarTexts().isNotEmpty())

            service.resetAppendOnlyForNewPrompt()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        fixture.assistantSummary,
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second question"),
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )

            assertEquals(0, appendOnly.clearCount)
            assertNull(appendOnly.lastClearReason)
            assertTrue(appendOnly.committedVolatileSidecarTexts().isNotEmpty())
            val wire = capturedParams.last().messages()
            assertTrue(
                wire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":1}"""
                },
                "frozen sidecars must still replay after cross-turn rehydrate without budget row",
            )
        }

    @Test
    fun PC_XAI_09_cross_turn_rehydrate_prefix_drift_preserves_frozen_sidecars() =
        runTest {
            val capturedParams = mutableListOf<ChatCompletionCreateParams>()
            val service = xaiService(capturedParams)
            val options = GenerationOptions(reasoningBudget = 0, chatId = ChatId("chat-1"))
            val fixture = toolLoopWithBudgetFixture()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool.copy(
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{}"}]}
                                """.trimIndent(),
                        ),
                        fixture.toolResult,
                        fixture.assistantSummary,
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )
            val appendOnly = requireNotNull(service.appendOnlyContextForTests())
            assertEquals(1, appendOnly.committedVolatileSidecarTexts().size)

            service.resetAppendOnlyForNewPrompt()

            service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool.copy(
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                """.trimIndent(),
                        ),
                        fixture.toolResult,
                        fixture.assistantSummary,
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second question"),
                    ),
                systemPrompt = "System",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = options,
            )

            assertEquals(0, appendOnly.clearCount)
            assertNull(appendOnly.lastClearReason)
            assertEquals(2, appendOnly.committedVolatileSidecarTexts().size)
            val wire = capturedParams.last().messages()
            assertTrue(
                wire.any { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":1}"""
                },
                "frozen turn:1 sidecar must replay after cross-turn rehydrate prefix drift",
            )
        }

    private data class ToolLoopWithBudgetFixture(
        val userMessage: AgentLoopMessage,
        val assistantWithTool: AgentLoopMessage,
        val toolResult: AgentLoopMessage,
        val agentBudgetMessage: AgentLoopMessage,
        val assistantSummary: AgentLoopMessage,
    )

    private fun toolLoopWithBudgetFixture(): ToolLoopWithBudgetFixture =
        ToolLoopWithBudgetFixture(
            userMessage = AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
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
                    id = "budget",
                    role = Role.ASSISTANT,
                    content =
                        "<system_context>\nTool budget: 1/35 used (34 remaining). Tools enabled.\n</system_context>",
                    metadataJson = """{"agent_tool_budget":true}""",
                ),
            assistantSummary =
                AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Here are cat results."),
        )

    private fun xaiService(capturedParams: MutableList<ChatCompletionCreateParams>): BaseOpenAiSdkInferenceService =
        object : XaiInferenceServiceImpl(
            client = mockk<OpenAIClient>(),
            modelId = "grok-4.3",
            modelType = ModelType.FAST,
            loggingPort = loggingPort,
            toolStreamGatePort = FakeToolStreamGatePort(),
        ) {
            override val tag: String = "XaiPromptCacheToolAndSessionStabilityTest"

            override suspend fun streamChatCompletions(
                params: ChatCompletionCreateParams,
                chatId: com.browntowndev.enclave.domain.model.chat.ChatId?,
                userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                assistantMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
                emitEvent: suspend (InferenceEvent) -> Unit,
            ): StreamedOpenAiResponse {
                capturedParams.add(params)
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
}
