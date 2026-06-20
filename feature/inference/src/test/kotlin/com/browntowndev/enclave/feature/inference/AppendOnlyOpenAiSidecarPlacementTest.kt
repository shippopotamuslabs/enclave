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
import com.browntowndev.enclave.feature.inference.appendonly.SidecarUserContentEmbedder
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
            assertEquals(2, messages.size)
            assertTrue(messages[0].isSystem())
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            assertTrue(messages[1].isUser())
            val userText = messages[1].asUser().content().asText()
            assertTrue(userText.startsWith("Hello"))
            assertTrue(userText.contains("[Sidecar-"))
            assertTrue(userText.contains("""State ledger JSON: {"key":"value"}"""))
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
            // Turn-1 frozen sidecar replays as a [Sidecar-<uuid>:] suffix on
            // the first user message, not as a separate system message.
            val firstUserSecond = second.first { it.isUser() }.asUser().content().asText()
            assertTrue(firstUserSecond.contains("[Sidecar-"))
            assertTrue(firstUserSecond.contains("""State ledger JSON: {"turn":1}"""))
            assertEquals("Search for cats", first[1].asUser().content().asText().substringBefore("\n\n[Sidecar-"))
            assertEquals("Search for cats", firstUserSecond.substringBefore("\n\n[Sidecar-"))
            // Turn-2 live sidecar is on the tail user message (trailing anchor
            // for tool-result tails attaches to the last user message).
            val turnTwoSidecarPresent = second.any { message ->
                message.isUser() &&
                    message.asUser().content().asText().contains("""State ledger JSON: {"turn":2}""")
            }
            assertTrue(turnTwoSidecarPresent)
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
            assertEquals(5, thirdTurn.size)
            assertTrue(thirdTurn[0].isSystem())
            assertTrue(thirdTurn[1].isUser())
            assertTrue(thirdTurn[2].isAssistant())
            assertTrue(thirdTurn[3].isTool())
            assertTrue(thirdTurn[4].isAssistant())

            val userText = thirdTurn[1].asUser().content().asText()
            assertTrue(userText.startsWith("Search for cats"))
            assertTrue(userText.contains("""State ledger JSON: {"turn":1}"""))
            assertTrue(userText.contains("""State ledger JSON: {"turn":2}"""))
            assertTrue(userText.contains("""State ledger JSON: {"turn":3}"""))
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
            assertEquals(4, second.size)
            assertTrue(second[0].isSystem())
            assertTrue(second[1].isUser())
            assertTrue(second[2].isAssistant())
            assertTrue(second[3].isTool())

            val userText = second[1].asUser().content().asText()
            assertTrue(userText.contains("""State ledger JSON: {"turn":1}"""))
            assertTrue(userText.contains("""State ledger JSON: {"turn":2}"""))
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
            assertEquals(2, messages.size)
            assertTrue(messages[0].isSystem())
            assertTrue(messages[1].isUser())
            val userText = messages[1].asUser().content().asText()
            assertTrue(userText.startsWith("Hello"))
            assertTrue(userText.contains("[Sidecar-"))
            assertTrue(userText.contains("""State ledger JSON: {"key":"value"}"""))
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
            assertEquals(4, messages.size)
            val u1Text = messages[1].asUser().content().asText()
            val u2Text = messages[3].asUser().content().asText()
            assertFalse(u1Text.contains("[Sidecar-"))
            assertTrue(u2Text.contains("[Sidecar-"))
            assertTrue(u2Text.contains("""State ledger JSON: {"k":1}"""))
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
            assertEquals(4, messages.size)
            assertEquals("You are a helpful assistant.", messages[0].asSystem().content().asText())
            val userText = messages[1].asUser().content().asText()
            assertTrue(userText.startsWith("Search for cats"))
            assertTrue(userText.contains("[Sidecar-"))
            assertTrue(userText.contains(sidecars))
            assertTrue(messages[2].isAssistant())
            assertTrue(messages[3].isTool())
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

            assertEquals(4, second.size)
            assertEquals(5, third.size)
            assertEquals(second[0].asWireText(), third[0].asWireText())
            assertEquals(second[2].asWireText(), third[2].asWireText())
            assertEquals(second[3].asWireText(), third[3].asWireText())

            val secondUserClean = SidecarUserContentEmbedder.stripSidecarBlocks(second[1].asUser().content().asText())
            val thirdUserClean = SidecarUserContentEmbedder.stripSidecarBlocks(third[1].asUser().content().asText())
            assertEquals(secondUserClean, thirdUserClean)
            assertEquals("Search for cats", secondUserClean)
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
            val userTextSecond = second[1].asUser().content().asText()
            assertTrue(userTextSecond.contains("""State ledger JSON: {"turn":1}"""))
            assertTrue(userTextSecond.contains("""State ledger JSON: {"turn":2}"""))

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
            val userTextThird = third[1].asUser().content().asText()
            assertTrue(userTextThird.contains("""State ledger JSON: {"turn":1}"""))
            assertTrue(userTextThird.contains("""State ledger JSON: {"turn":2}"""))
            assertTrue(userTextThird.contains("""State ledger JSON: {"turn":3}"""))
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
            assertEquals(5, messages.size)
            assertTrue(messages[1].asUser().content().asText().contains("[Sidecar-"))
            assertTrue(messages[1].asUser().content().asText().contains(sidecars))
            assertTrue(messages[3].isTool())
            assertTrue(messages[4].isSystem())
            assertTrue(messages[4].asSystem().content().asText().contains("Tool budget"))
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
            assertEquals(3, messages.size)
            val helloParts = messages[1].asUser().content().asArrayOfContentParts()
            assertTrue(helloParts.any { it.isText() })
            assertEquals("Hello", helloParts.first { it.isText() }.asText().text())
            val followUpText = messages[2].asUser().content().asText()
            assertTrue(followUpText.startsWith("Follow up"))
            assertTrue(followUpText.contains("[Sidecar-"))
            assertTrue(followUpText.contains("""State ledger JSON: {"key":"value"}"""))
        }

    @Test
    fun AOC_23_openrouter_frozen_sidecar_preserves_cache_control_on_prior_user_part() =
        runTest {
            val captured =
                captureOpenAiParams(
                    provider = "OPENROUTER",
                    modelId = "anthropic/claude-3.5-sonnet",
                )
            val firstSidecar = """State ledger JSON: {"turn":1}"""
            captured.service.completeSingleTurn(
                sessionMessages =
                    listOf(
                        AgentLoopMessage(id = "u1", role = Role.USER, content = "Hello"),
                    ),
                systemPrompt = "You are a helpful assistant.",
                contextSidecars = firstSidecar,
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
                contextSidecars = """State ledger JSON: {"turn":2}""",
                availableTools = emptyList(),
                allowToolCall = false,
                onTextToken = {},
                onThinkingToken = {},
                options = GenerationOptions(reasoningBudget = 0),
            )

            val messages = captured.params.last().messages()
            assertEquals(4, messages.size)
            val priorUserParts = messages[1].asUser().content().asArrayOfContentParts()
            val priorUserText = priorUserParts.first { it.isText() }.asText()
            assertEquals("Hello", priorUserText.text().substringBefore("\n\n[Sidecar-"))
            assertTrue(priorUserText.text().contains(firstSidecar))
            assertTrue(priorUserText._additionalProperties().containsKey("cache_control"))

            val followUpText = messages[3].asUser().content().asText()
            assertTrue(followUpText.contains("""State ledger JSON: {"turn":2}"""))
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
            assertEquals(3, hop2Messages.size)
            assertTrue(hop2Messages[1].asUser().content().asText().contains(identicalSidecar))

            val hop3Messages = captured.params[2].messages()
            assertEquals(4, hop3Messages.size)
            assertTrue(hop3Messages[1].asUser().content().asText().contains(identicalSidecar))
            assertFalse(hop3Messages[3].asUser().content().asText().contains("[Sidecar-"))
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
                    message.isUser() && message.asUser().content().asText().contains(sidecar)
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
