package com.browntowndev.enclave.feature.inference

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageParam
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContextFactory
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Direct coverage for [AnthropicRequestMapper.mapSessionToMessageParams], the agent-loop request
 * builder extracted from AnthropicInferenceServiceImpl. Previously only exercised transitively
 * through AnthropicInferenceServiceImplTest.
 */
class AnthropicRequestMapperSessionTest {
    private val objectMapper = ObjectMapper()

    private fun parseInput(json: String): Map<String, JsonValue> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {}).entries
            .associate { (key, value) -> key to JsonValue.from(value) }

    private fun appendOnly() = ProviderAppendOnlyContextFactory.create()

    private fun baseOptions(
        modelId: String = "claude-3-5-sonnet-20241022",
        allowToolCall: Boolean = true,
        reasoningEffort: ApiReasoningEffort? = null,
        maxTokens: Int? = 4096,
    ) = GenerationOptions(
        reasoningBudget = 0,
        reasoningEffort = reasoningEffort,
        maxTokens = maxTokens,
        modelId = modelId,
        toolingEnabled = allowToolCall,
    )

    private fun sampleTranscript(): List<AgentLoopMessage> = listOf(
        AgentLoopMessage(id = "u1", role = Role.USER, content = "Search the web for cats"),
        AgentLoopMessage(
            id = "a1",
            role = Role.ASSISTANT,
            content = "",
            metadataJson = """{"raw_tool_calls":[{"id":"call_1","name":"web_search","arguments_json":"{\"q\":\"cats\"}"}]}""",
        ),
        AgentLoopMessage(
            id = "t1",
            role = Role.USER,
            content = """{"results":["cat"]}""",
            metadataJson = """{"call_id":"call_1"}""",
        ),
        AgentLoopMessage(id = "u2", role = Role.USER, content = "Summarize what you found"),
    )

    @Test
    fun `sets model and max tokens on the params`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = sampleTranscript(),
            systemPrompt = "You are helpful",
            availableTools = listOf(ToolDefinition.WEB_SEARCH),
            allowToolCall = true,
            options = baseOptions(maxTokens = 2048),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        assertEquals("claude-3-5-sonnet-20241022", params.model().toString())
        assertEquals(2048L, params.maxTokens().toLong())
    }

    @Test
    fun `prepends system prompt as a text block when non-blank`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "You are helpful",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        val systemBlocks = params.system().orElseThrow().asTextBlockParams()
        assertEquals(1, systemBlocks.size)
        assertEquals("You are helpful", systemBlocks[0].text())
    }

    @Test
    fun `omits system block when prompt is blank`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        assertTrue(params.system().isEmpty, "system should be absent when prompt is blank")
    }

    @Test
    fun `maps transcript roles to user and assistant messages`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = sampleTranscript(),
            systemPrompt = "You are helpful",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        val messages = params.messages()
        // 4 transcript rows: user, assistant(tool call), user(tool result), user. None dropped.
        assertEquals(4, messages.size)
        assertEquals(MessageParam.Role.USER, messages[0].role())
        // Assistant tool-call row maps to assistant role.
        assertEquals(MessageParam.Role.ASSISTANT, messages[1].role())
    }

    @Test
    fun `maps tool result row as user role with tool_result block`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = sampleTranscript(),
            systemPrompt = "You are helpful",
            availableTools = listOf(ToolDefinition.WEB_SEARCH),
            allowToolCall = true,
            options = baseOptions(),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        val messages = params.messages()
        // messages[2] is the tool result (third transcript row).
        val toolResultMessage = messages[2]
        assertEquals(MessageParam.Role.USER, toolResultMessage.role())
        val blocks = toolResultMessage.content().asBlockParams()
        assertTrue(blocks[0].isToolResult())
    }

    @Test
    fun `adds tools and sets tool_choice none when allowToolCall is false`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "You are helpful",
            availableTools = listOf(ToolDefinition.WEB_SEARCH),
            allowToolCall = false,
            options = baseOptions(allowToolCall = false),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        val tools = params.tools().orElseThrow()
        assertEquals(1, tools.size)
        val serialized = tools[0].toString()
        assertTrue(serialized.contains("web_search"))
        assertNotNull(params.toolChoice().orElseThrow())
        assertFalse(params.toolChoice().get().isAuto())
    }

    @Test
    fun `enables thinking config when reasoning effort is set`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "You are helpful",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(reasoningEffort = ApiReasoningEffort.MEDIUM, maxTokens = 8192),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        assertTrue(params.thinking().isPresent)
    }

    @Test
    fun `omits thinking config when reasoning effort is none`() {
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "You are helpful",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(reasoningEffort = ApiReasoningEffort.NONE),
            appendOnly = appendOnly(),
            wireSidecar = null,
            toolInputProperties = ::parseInput,
        )
        assertTrue(params.thinking().isEmpty)
    }

    @Test
    fun `embeds live sidecar text into the tail user message`() {
        val appendOnly = appendOnly()
        val params = AnthropicRequestMapper.mapSessionToMessageParams(
            modelId = "claude-3-5-sonnet-20241022",
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            systemPrompt = "You are helpful",
            availableTools = emptyList(),
            allowToolCall = false,
            options = baseOptions(),
            appendOnly = appendOnly,
            wireSidecar = ResolvedSidecar(
                text = "extra retrieval context",
                markerId = "sidecar-uuid-1",
            ),
            toolInputProperties = ::parseInput,
        )
        val messages = params.messages()
        val tail = messages.last()
        val text = tail.content().asBlockParams()[0].asText().text()
        assertTrue(text.contains("extra retrieval context"))
        assertTrue(text.contains("hi"))
    }
}
