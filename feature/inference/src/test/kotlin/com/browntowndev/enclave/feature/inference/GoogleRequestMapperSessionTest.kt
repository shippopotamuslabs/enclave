package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AgentLoopMessage
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.feature.inference.appendonly.ProviderAppendOnlyContextFactory
import com.browntowndev.enclave.feature.inference.appendonly.ResolvedSidecar
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Direct coverage for [GoogleRequestMapper.mapSessionToGenerateContent], the agent-loop request
 * builder extracted from GoogleInferenceServiceImpl. Previously only exercised transitively
 * through GoogleInferenceServiceImplTest.
 */
class GoogleRequestMapperSessionTest {
    private fun appendOnly() = ProviderAppendOnlyContextFactory.create()

    private fun baseOptions(
        modelId: String = "gemini-1.5-flash",
        allowToolCall: Boolean = true,
        reasoningBudget: Int = 0,
        maxTokens: Int? = 4096,
    ) = GenerationOptions(
        reasoningBudget = reasoningBudget,
        maxTokens = maxTokens,
        modelId = modelId,
        toolingEnabled = allowToolCall,
        apiProvider = ApiProvider.GOOGLE,
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
            role = Role.ASSISTANT,
            content = """{"results":["cat"]}""",
            metadataJson = """{"call_id":"call_1"}""",
        ),
        AgentLoopMessage(id = "u2", role = Role.USER, content = "Summarize what you found"),
    )

    private fun mapSession(
        sessionMessages: List<AgentLoopMessage> = sampleTranscript(),
        systemPrompt: String = "You are helpful",
        availableTools: List<ToolDefinition> = listOf(ToolDefinition.WEB_SEARCH),
        allowToolCall: Boolean = true,
        options: GenerationOptions = baseOptions(),
        activeCacheName: String? = null,
        deltaHistory: List<AgentLoopMessage> = sessionMessages.dropLast(1),
        wireSidecar: ResolvedSidecar? = null,
    ) = GoogleRequestMapper.mapSessionToGenerateContent(
        sessionMessages = sessionMessages,
        systemPrompt = systemPrompt,
        availableTools = availableTools,
        allowToolCall = allowToolCall,
        options = options,
        appendOnly = appendOnly(),
        wireSidecar = wireSidecar,
        activeCacheName = activeCacheName,
        deltaHistory = deltaHistory,
    )

    @Test
    fun `sets system instruction when no cache is active`() {
        val request = mapSession(activeCacheName = null)
        assertTrue(request.config.systemInstruction().isPresent)
    }

    @Test
    fun `omits system instruction when a cache is active`() {
        val request = mapSession(activeCacheName = "cachedContents/abc")
        assertFalse(request.config.systemInstruction().isPresent)
        assertEquals("cachedContents/abc", request.config.cachedContent().orElseThrow())
    }

    @Test
    fun `omits system instruction when prompt is blank and no cache`() {
        val request = mapSession(systemPrompt = "", activeCacheName = null)
        assertFalse(request.config.systemInstruction().isPresent)
    }

    @Test
    fun `applies sampler options to the config`() {
        val request = mapSession(
            options = baseOptions(maxTokens = 1024),
        )
        assertEquals(1024, request.config.maxOutputTokens().orElseThrow())
    }

    @Test
    fun `adds tools and auto tool config when allowToolCall is true`() {
        val request = mapSession(availableTools = listOf(ToolDefinition.WEB_SEARCH), allowToolCall = true)
        val tools = request.config.tools().orElseThrow()
        val functionNames = tools.flatMap { it.functionDeclarations().orElseThrow() }
            .map { it.name().orElseThrow() }
        assertTrue(functionNames.contains("web_search"))
        val mode = request.config.toolConfig().orElseThrow().functionCallingConfig().orElseThrow().mode().orElseThrow()
        assertEquals("AUTO", mode.toString())
    }

    @Test
    fun `sets none tool config when allowToolCall is false`() {
        val request = mapSession(availableTools = listOf(ToolDefinition.WEB_SEARCH), allowToolCall = false)
        val mode = request.config.toolConfig().orElseThrow().functionCallingConfig().orElseThrow().mode().orElseThrow()
        assertEquals("NONE", mode.toString())
    }

    @Test
    fun `enables thinking config when reasoning budget is set`() {
        val request = mapSession(options = baseOptions(reasoningBudget = 2048))
        assertTrue(request.config.thinkingConfig().isPresent)
    }

    @Test
    fun `omits thinking config when reasoning budget is zero`() {
        val request = mapSession(options = baseOptions(reasoningBudget = 0))
        assertFalse(request.config.thinkingConfig().isPresent)
    }

    @Test
    fun `contents include delta history plus latest message`() {
        val transcript = sampleTranscript()
        val request = mapSession(
            sessionMessages = transcript,
            deltaHistory = transcript.dropLast(1),
        )
        // deltaHistory has 3 rows + latest = 4 contents (system rows dropped, but none here).
        assertEquals(4, request.contents.size)
        assertEquals("user", request.contents.last().role().orElseThrow())
    }

    @Test
    fun `contents embed live sidecar into the tail user message`() {
        val request = mapSession(
            sessionMessages = listOf(AgentLoopMessage(id = "u1", role = Role.USER, content = "hi")),
            deltaHistory = emptyList(),
            wireSidecar = ResolvedSidecar(text = "extra retrieval context", markerId = "sidecar-uuid-1"),
        )
        val tail = request.contents.last()
        val text = tail.parts().orElseThrow()[0].text().orElseThrow()
        assertTrue(text.contains("extra retrieval context"))
        assertTrue(text.contains("hi"))
    }

    @Test
    fun `contents route assistant tool result to function response`() {
        val transcript = sampleTranscript()
        val request = mapSession(sessionMessages = transcript, deltaHistory = transcript.dropLast(1))
        // The tool result is the third transcript row (index 2); it rides in deltaHistory.
        val toolResultContent = request.contents[2]
        assertEquals("user", toolResultContent.role().orElseThrow())
        assertTrue(toolResultContent.parts().orElseThrow()[0].functionResponse().isPresent)
    }

    @Test
    fun `throws when session messages is empty`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            mapSession(sessionMessages = emptyList(), deltaHistory = emptyList())
        }
    }
}
