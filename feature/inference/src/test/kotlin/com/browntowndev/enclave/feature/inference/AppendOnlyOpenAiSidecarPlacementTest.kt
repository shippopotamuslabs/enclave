package com.browntowndev.enclave.feature.inference

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
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class AppendOnlyOpenAiSidecarPlacementTest {
    private val loggingPort = mockk<LoggingPort>(relaxed = true)

    @Test
    fun AOC_02_single_user_message_tail_sidecar() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "msg1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"key":"value"}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            assertEquals(3, messages.size)
            assertTrue(messages[0].isSystem())
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertTrue(messages[1].isSystem())
            assertEquals("""State ledger JSON: {"key":"value"}""", messages[1].asSystem().content().asText())
            assertTrue(messages[2].isUser())
            assertEquals("Hello", messages[2].asUser().content().asText())
        }

    @Test
    fun AOC_03_tool_loop_preserves_committed_prefix_bytes() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val first = captured.params.single().messages()

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                """.trimIndent(),
                        ),
                        AgentLoopMessage(
                            id = "t1",
                            role = Role.USER,
                            content = """{"results":[{"title":"Cat"}]}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val second = captured.params.last().messages()

            assertEquals(first[0].asSystem().content().asText(), second[0].asSystem().content().asText())
            assertEquals("You are a helpful assistant.", first[0].asSystem().content().asText())
            assertTrue(second[1].isSystem())
            assertEquals("""State ledger JSON: {"turn":1}""", second[1].asSystem().content().asText())
            assertEquals("Search for cats", first[2].asUser().content().asText())
            assertEquals("Search for cats", second[2].asUser().content().asText())
            assertNotEquals("""State ledger JSON: {"turn":2}""", second[1].asSystem().content().asText())
            val turnTwoSidecarIndex =
                second.indexOfFirst { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":2}"""
                }
            assertTrue(turnTwoSidecarIndex > 2)
            assertFalse(second[1].isUser())
        }

    @Test
    fun wire_caps_frozen_sidecars_at_two_on_third_tool_loop_turn() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val userMessage =
                AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats")
            val assistantWithTool =
                AgentLoopMessage(
                    id = "a1",
                    role = Role.ASSISTANT,
                    content = "",
                    metadataJson =
                        """
                        {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                        """.trimIndent(),
                )
            val toolResult =
                AgentLoopMessage(
                    id = "t1",
                    role = Role.USER,
                    content = """{"results":[]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                )

            captured.service.completeSingleTurn(
                sessionMessages = listOf(userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages = listOf(userMessage, assistantWithTool, toolResult),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        userMessage,
                        assistantWithTool,
                        toolResult,
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":3}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val thirdTurn = captured.params.last().messages()
            val sidecarTexts =
                thirdTurn
                    .filter { it.isSystem() }
                    .map { it.asSystem().content().asText() }
                    .filter { it.startsWith("State ledger JSON:") }
            assertEquals(3, sidecarTexts.size)
            assertEquals("""State ledger JSON: {"turn":1}""", sidecarTexts[0])
            assertEquals("""State ledger JSON: {"turn":2}""", sidecarTexts[1])
            assertEquals("""State ledger JSON: {"turn":3}""", sidecarTexts[2])
            assertEquals(
                "Search for cats",
                thirdTurn[2].asUser().content().asText(),
                "turn:1 sidecar must not front-prepend before user",
            )
        }

    @Test
    fun digest_mismatch_logs_append_only_warning() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello altered"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            verify {
                loggingPort.warning(
                    "AppendOnlyOpenAiSidecarPlacementTest",
                    match { it.contains("digest_mismatch") },
                )
            }
        }

    @Test
    fun AOC_04_openai_wire_carries_both_sidecars_from_AOC_03() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                """.trimIndent(),
                        ),
                        AgentLoopMessage(
                            id = "t1",
                            role = Role.USER,
                            content = """{"results":[]}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val second = captured.params.last().messages()
            assertEquals("""State ledger JSON: {"turn":1}""", second[1].asSystem().content().asText())
            assertEquals(
                """State ledger JSON: {"turn":2}""",
                second[5].asSystem().content().asText(),
            )
            assertTrue(
                second.indexOfFirst {
                    it.isSystem() && it.asSystem().content().asText() == """State ledger JSON: {"turn":1}"""
                } <
                    second.indexOfFirst {
                        it.isSystem() && it.asSystem().content().asText() == """State ledger JSON: {"turn":2}"""
                    },
            )
        }

    @Test
    fun AOC_06_openai_toolChoice_none_keeps_tools() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = listOf(ToolDefinition.WEB_SEARCH),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0, toolingEnabled = true),
            )

            val params = captured.params.single()
            assertTrue(params.tools().isPresent)
            assertEquals(1, params.tools().get().size)
            assertTrue(params.toolChoice().isPresent)
            assertTrue(params.toolChoice().get().isAuto())
        }

    @Test
    fun AOC_10_openrouter_tail_sidecar_matches_AOC_02() =
        runTest {
            val captured =
                captureOpenAiParams(
                    provider = "OPENROUTER",
                    modelId = "openai/gpt-4o",
                )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "msg1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"key":"value"}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            assertEquals(3, messages.size)
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertEquals("""State ledger JSON: {"key":"value"}""", messages[1].asSystem().content().asText())
            assertEquals("Hello", messages[2].asUser().content().asText())
        }

    @Test
    fun AOC_11_closeSession_invalidates_append_only_context() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hi"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val context = captured.service.appendOnlyContextForTests()
            checkNotNull(context)
            assertEquals(1, context.ensureStablePrefix("You are a helpful assistant.", "tools:empty").version)

            captured.service.closeSession()
            assertEquals(null, captured.service.appendOnlyContextForTests())

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Again"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val refreshed = captured.service.appendOnlyContextForTests()
            checkNotNull(refreshed)
            assertEquals(1, refreshed.ensureStablePrefix("You are a helpful assistant.", "tools:empty").version)
            assertEquals(1, refreshed.committedMessageCount())
        }

    @Test
    fun AOC_12_multi_message_session_rejects_index_1_sidecar_pin() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            val sidecars = """State ledger JSON: {"k":1}"""
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "First"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Ack"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Second"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecars,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            val sidecarIndex =
                messages.indexOfFirst { message ->
                    message.isSystem() && message.asSystem().content().asText() == sidecars
                }
            assertTrue(sidecarIndex > 1)
            assertEquals(sidecars, messages[messages.lastIndex - 1].asSystem().content().asText())
        }

    @Test
    fun AOC_14_null_and_blank_sidecars_skip_volatile_system_message() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = null,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            assertEquals(
                2,
                captured.params
                    .single()
                    .messages()
                    .size,
            )

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = "",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            assertEquals(
                2,
                captured.params
                    .last()
                    .messages()
                    .size,
            )
        }

    @Test
    fun AOC_15_xai_sidecar_before_tool_result_tail() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val sidecars = """State ledger JSON: {"turn":1}"""
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search for cats"),
                        AgentLoopMessage(
                            id = "a1",
                            role = Role.ASSISTANT,
                            content = "",
                            metadataJson =
                                """
                                {"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"query\":\"cats\"}"}]}
                                """.trimIndent(),
                        ),
                        AgentLoopMessage(
                            id = "t1",
                            role = Role.USER,
                            content = """{"results":[]}""",
                            metadataJson = """{"call_id":"call_1"}""",
                        ),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecars,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            assertEquals(5, messages.size)
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertEquals("Search for cats", messages[1].asUser().content().asText())
            assertTrue(messages[2].isAssistant())
            assertTrue(messages[3].isTool())
            assertEquals(sidecars, messages[4].asSystem().content().asText())
            assertFalse(messages[1].isSystem())
        }

    @Test
    fun AOC_18_post_tool_third_call_preserves_messages_prefix_bytes() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val fixture = toolLoopFixture()

            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val second = captured.params.last().messages()

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":3}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val third = captured.params.last().messages()

            assertTrue(third.size >= 5, "expected at least 5 messages on third call")
            for (index in 0..4) {
                assertEquals(
                    second[index].asWireText(),
                    third[index].asWireText(),
                    "messages[$index] must be byte-equal between calls 2 and 3",
                )
            }
        }

    @Test
    fun AOC_19_frozen_sidecar_replays_at_original_mapped_index_not_index_1() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val fixture = toolLoopFixture()

            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val second = captured.params.last().messages()
            val turnTwoSidecarIndex =
                second.indexOfFirst { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":2}"""
                }
            assertTrue(turnTwoSidecarIndex > 1, "turn:2 sidecar must not be pinned at index 1 on call 2")

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":3}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val third = captured.params.last().messages()
            val turnTwoSidecarIndexOnThird =
                third.indexOfFirst { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == """State ledger JSON: {"turn":2}"""
                }
            assertEquals(
                turnTwoSidecarIndex,
                turnTwoSidecarIndexOnThird,
                "frozen turn:2 sidecar must replay at the same index as call 2",
            )
            assertNotEquals(
                1,
                turnTwoSidecarIndexOnThird,
                "frozen turn:2 sidecar must not be front-prepended to index 1 on call 3",
            )
        }

    @Test
    fun AOC_20_fourth_call_does_not_swap_index_1_sidecar_via_cap() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val fixture = toolLoopFixture()
            val assistantSummary =
                AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary")

            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        assistantSummary,
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":3}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val third = captured.params.last().messages()
            val indexOneOnThird = third[1].asWireText()

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        assistantSummary,
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":4}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val fourth = captured.params.last().messages()

            assertEquals(
                indexOneOnThird,
                fourth[1].asWireText(),
                "messages[1] must be unchanged on turn 4 (no cap-at-2 prefix swap)",
            )
        }

    @Test
    fun AOC_21_sidecar_before_tool_result_when_agent_budget_trails_session() =
        runTest {
            val captured = captureOpenAiParams(provider = "XAI", modelId = "grok-4.3")
            val fixture = toolLoopFixture()
            val sidecars = """State ledger JSON: {"turn":2}"""

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        AgentLoopMessage(
                            id = "budget",
                            role = Role.USER,
                            content =
                                "<system_context>\nTool budget: 1/35 used (34 remaining). Tools enabled.\n</system_context>",
                            metadataJson = """{"agent_tool_budget":true}""",
                        ),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecars,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            val sidecarIndex =
                messages.indexOfFirst { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == sidecars
                }
            val toolIndex = messages.indexOfFirst { it.isTool() }
            val budgetIndex = messages.indexOfFirst {
                it.isSystem() && it.asSystem().content().asText().contains("Tool budget")
            }
            assertTrue(sidecarIndex >= 0, "sidecar must be present")
            assertTrue(toolIndex >= 0, "tool result must be present")
            assertTrue(budgetIndex >= 0, "budget message must be present")
            assertTrue(
                sidecarIndex > toolIndex,
                "volatile sidecar must anchor after tool result to avoid breaking contiguity",
            )
            assertTrue(
                sidecarIndex < budgetIndex,
                "volatile sidecar must anchor before agent budget tail",
            )
        }

    @Test
    fun AOC_16_openrouter_cache_control_on_stable_prior_user() =
        runTest {
            val captured =
                captureOpenAiParams(
                    provider = "OPENROUTER",
                    modelId = "anthropic/claude-3.5-sonnet",
                )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"key":"value"}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.single().messages()
            assertEquals(4, messages.size)
            val helloParts = messages[1].asUser().content().asArrayOfContentParts()
            assertTrue(helloParts.any { it.isText() })
            assertEquals("Follow up", messages[3].asUser().content().asText())
            assertTrue(messages[2].isSystem())
        }

    @Test
    @DisplayName("SC-REG-02 AOC_22 semantic progression preserved")
    fun SC_REG_02_AOC_22_tool_loop_does_not_increment_append_only_clear_count() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENROUTER", modelId = "deepseek/deepseek-v4-flash")
            val fixture = toolLoopFixture()

            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":1}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages = listOf(fixture.userMessage, fixture.assistantWithTool, fixture.toolResult),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        fixture.userMessage,
                        fixture.assistantWithTool,
                        fixture.toolResult,
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = """State ledger JSON: {"turn":3}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val context = requireNotNull(captured.service.appendOnlyContextForTests())
            assertEquals(0, context.clearCount)
            assertEquals(3, context.frozenSidecarsForWireReplay().size)

            val identicalCaptured = captureOpenAiParams(provider = "OPENROUTER", modelId = "deepseek/deepseek-v4-flash")
            val identicalFixture = toolLoopFixture()
            val identicalSidecar = """State ledger JSON: {"turn":1}"""
            identicalCaptured.service.completeSingleTurn(
                sessionMessages = listOf(identicalFixture.userMessage),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            identicalCaptured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        identicalFixture.userMessage,
                        identicalFixture.assistantWithTool,
                        identicalFixture.toolResult,
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = true,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            identicalCaptured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        identicalFixture.userMessage,
                        identicalFixture.assistantWithTool,
                        identicalFixture.toolResult,
                        AgentLoopMessage(id = "a2", role = Role.ASSISTANT, content = "Summary"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            assertEquals(
                1,
                requireNotNull(identicalCaptured.service.appendOnlyContextForTests())
                    .frozenSidecarsForWireReplay()
                    .size,
            )
        }

    @Test
    @DisplayName("SC-DEDUPE-01 three hops identical contextSidecars")
    fun SC_DEDUPE_01_threeHopsIdenticalSidecarDedupesFreeze() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            val identicalSidecar = """State ledger JSON: {"turn":1}"""

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = identicalSidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val context = requireNotNull(captured.service.appendOnlyContextForTests())
            assertEquals(1, context.frozenSidecarsForWireReplay().size)

            val hop2Messages = captured.params[1].messages()
            val hop2SidecarCount =
                hop2Messages.count { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == identicalSidecar
                }
            assertEquals(1, hop2SidecarCount)

            val hop3Messages = captured.params[2].messages()
            val hop3SidecarCount =
                hop3Messages.count { message ->
                    message.isSystem() &&
                        message.asSystem().content().asText() == identicalSidecar
                }
            assertEquals(1, hop3SidecarCount)
            assertFalse(
                hop3Messages.last().isSystem() &&
                    hop3Messages.last().asSystem().content().asText() == identicalSidecar,
            )
        }

    @Test
    @DisplayName("SC-DEDUPE-MUT three identical hops must not triple-freeze")
    fun SC_DEDUPE_MUT_threeIdenticalHopsMustNotTripleFreeze() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            val sidecar = """State ledger JSON: {"frozen":true}"""

            repeat(3) { hop ->
                val session =
                    buildList {
                        add(AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"))
                        repeat(hop) { turn ->
                            add(
                                AgentLoopMessage(
                                    id = "a$turn",
                                    role = Role.ASSISTANT,
                                    content = "Reply $turn",
                                ),
                            )
                            add(
                                AgentLoopMessage(
                                    id = "u${turn + 2}",
                                    role = Role.USER,
                                    content = "Follow up $turn",
                                ),
                            )
                        }
                    }
                captured.service.completeSingleTurn(
                    sessionMessages = session,
                    systemPrompt = "You are a helpful assistant.",
                    contextSidecars = sidecar,
                    availableTools = emptyList(),
                    allowToolCall = false,
                    onTextToken = {},
                    onThinkingToken = {},
                    options = GenerationOptions(reasoningBudget = 0),
                )
            }

            val context = requireNotNull(captured.service.appendOnlyContextForTests())
            assertEquals(1, context.frozenSidecarsForWireReplay().size)
            assertNotEquals(3, context.frozenSidecarsForWireReplay().size)
        }

    @Test
    @DisplayName("SC-DEDUPE-02 ledger version progression")
    fun SC_DEDUPE_02_v1ThenV2ThenV2ProducesTwoFrozenSnapshots() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            val v1 = """State ledger JSON: {"v":1}"""
            val v2 = """State ledger JSON: {"v":2}"""

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = v1,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = v2,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Follow up"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = v2,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val frozen = requireNotNull(captured.service.appendOnlyContextForTests()).frozenSidecarsForWireReplay()
            assertEquals(2, frozen.size)
            assertEquals(v1, frozen[0].text)
            assertEquals(v2, frozen[1].text)
        }

    @Test
    @DisplayName("SC-DEDUPE-04 reanchor boundary with identical sidecar")
    fun SC_DEDUPE_04_reanchorWithIdenticalSidecarDedupes() =
        runTest {
            val captured = captureOpenAiParams(provider = "OPENAI", modelId = "gpt-4o")
            val sidecar = """State ledger JSON: {"turn":1}"""

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )
            val context = requireNotNull(captured.service.appendOnlyContextForTests())
            context.markTurnBoundaryForReanchor()

            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                        AgentLoopMessage(id = "a1", role = Role.ASSISTANT, content = "Hi"),
                        AgentLoopMessage(id = "u2", role = Role.USER, content = "Next question"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = sidecar,
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            assertEquals(1, context.frozenSidecarsForWireReplay().size)
            assertNull(context.resolveContextSidecarsForWire(sidecar))
            val hop2Wire = captured.params.last().messages()
            assertTrue(
                hop2Wire.any { message ->
                    message.isSystem() && message.asSystem().content().asText() == sidecar
                },
            )
        }

    private data class ToolLoopFixture(
        val userMessage: AgentLoopMessage,
        val assistantWithTool: AgentLoopMessage,
        val toolResult: AgentLoopMessage,
    )

    private fun toolLoopFixture(): ToolLoopFixture =
        ToolLoopFixture(
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
                    content = """{"results":[]}""",
                    metadataJson = """{"call_id":"call_1"}""",
                ),
        )

    private fun com.openai.models.chat.completions.ChatCompletionMessageParam.asWireText(): String =
        when {
            isSystem() -> "SYSTEM:${asSystem().content().asText()}"
            isUser() -> "USER:${asUser().content().asText()}"
            isAssistant() -> "ASSISTANT:${asAssistant().toolCalls().isPresent}"
            isTool() -> "TOOL:${asTool().content().asText()}"
            else -> toString()
        }

    private data class CapturedOpenAiParams(
        val service: BaseOpenAiSdkInferenceService,
        val params: MutableList<ChatCompletionCreateParams>,
    )

    private fun captureOpenAiParams(
        provider: String,
        modelId: String,
    ): CapturedOpenAiParams {
        val capturedParams = mutableListOf<ChatCompletionCreateParams>()
        val service = when (provider) {
            "XAI" -> object : XaiInferenceServiceImpl(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                modelType = ModelType.FAST,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "AppendOnlyOpenAiSidecarPlacementTest"

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
                        assistantMessageText = "done",
                    )
                }
            }
            "OPENROUTER" -> object : OpenRouterInferenceServiceImpl(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                modelType = ModelType.FAST,
                openRouterRouting = com.browntowndev.enclave.domain.model.config.OpenRouterRoutingConfiguration(),
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "AppendOnlyOpenAiSidecarPlacementTest"

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
                        assistantMessageText = "done",
                    )
                }
            }
            else -> object : BaseOpenAiSdkInferenceService(
                client = mockk<OpenAIClient>(),
                modelId = modelId,
                provider = provider,
                modelType = ModelType.FAST,
                loggingPort = loggingPort,
                toolStreamGatePort = FakeToolStreamGatePort(),
            ) {
                override val tag: String = "AppendOnlyOpenAiSidecarPlacementTest"

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
                        assistantMessageText = "done",
                    )
                }
            }
        }
        return CapturedOpenAiParams(service = service, params = capturedParams)
    }

}
