package com.browntowndev.pocketcrew.feature.inference

import com.anthropic.models.messages.MessageParam
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role as DomainRole
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AnthropicRequestMapperTest {

    @Test
    fun `mapToMessageParams maps system prompt history and reasoning budget`() {
        val params = AnthropicRequestMapper.mapToMessageParams(
            modelId = "claude-sonnet-4-20250514",
            prompt = "How are you?",
            history = listOf(
                ChatMessage(DomainRole.SYSTEM, "You are a concise assistant."),
                ChatMessage(DomainRole.USER, "Hello"),
                ChatMessage(DomainRole.ASSISTANT, "Hi there!"),
                ChatMessage(DomainRole.ASSISTANT, "Error: API Error (ANTHROPIC): 400: null"),
            ),
            options = GenerationOptions(
                reasoningBudget = 0,
                systemPrompt = "Use short answers.",
                reasoningEffort = ApiReasoningEffort.HIGH,
                temperature = 0.7f,
                topP = 0.9f,
                topK = 32,
                maxTokens = 512,
            )
        )

        assertEquals("claude-sonnet-4-20250514", params.model().toString())
        assertEquals(
            listOf(MessageParam.Role.USER, MessageParam.Role.ASSISTANT, MessageParam.Role.USER),
            params.messages().map { message -> message.role() }
        )
        assertEquals(
            "Use short answers.\n\nYou are a concise assistant.",
            params.system().get().asString()
        )
        assertTrue(params.thinking().get().isEnabled())
        assertEquals(2048L, params.thinking().get().asEnabled().budgetTokens())
        assertEquals(0.7, params.temperature().get(), 0.0001)
        assertEquals(0.9, params.topP().get(), 0.0001)
        assertEquals(32L, params.topK().get())
        assertEquals(512L, params.maxTokens())
        assertFalse(params.toString().contains("Error: API Error"))
    }

    @Test
    fun `mapToMessageParams prefers explicit reasoning budget over effort mapping`() {
        val params = AnthropicRequestMapper.mapToMessageParams(
            modelId = "claude-sonnet-4-20250514",
            prompt = "Explain the plan.",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 4096,
                reasoningEffort = ApiReasoningEffort.LOW,
                maxTokens = 1024,
            )
        )

        assertTrue(params.thinking().get().isEnabled())
        assertEquals(4096L, params.thinking().get().asEnabled().budgetTokens())
        assertEquals(1, params.messages().size)
        assertEquals(MessageParam.Role.USER, params.messages().first().role())
    }

    @Test
    fun `mapToMessageParams serializes tavily_web_search when tooling is enabled`() {
        val params = AnthropicRequestMapper.mapToMessageParams(
            modelId = "claude-sonnet-4-20250514",
            prompt = "Find recent Android agent news",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            )
        )

        assertTrue(params.toString().contains("tavily_web_search"))
        assertTrue(params.toString().contains("query"))
    }
}
