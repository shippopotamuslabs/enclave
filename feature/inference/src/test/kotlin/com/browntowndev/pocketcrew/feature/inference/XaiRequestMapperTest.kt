package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XaiRequestMapperTest {

    @Test
    fun `mapToResponseParams omits max output tokens for xai multi agent model`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4.20-multi-agent-0309",
            prompt = "How are you?",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 100
            )
        )

        assertFalse(params.maxOutputTokens().isPresent)
    }

    @Test
    fun `mapToResponseParams never sends instructions and preserves system messages in input`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4.20-multi-agent",
            prompt = "hello",
            history = listOf(
                ChatMessage(Role.SYSTEM, "You are direct."),
                ChatMessage(Role.USER, "prior user")
            ),
            options = GenerationOptions(reasoningBudget = 0)
        )

        assertFalse(params.instructions().isPresent)
        assertTrue(params.input().get().toString().contains("role=system"))
        assertTrue(params.input().get().toString().contains("You are direct."))
    }

    @Test
    fun `mapToResponseParams includes low reasoning for multi agent mode`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4.20-multi-agent",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.LOW
            )
        )

        assertTrue(params.reasoning().isPresent)
        assertTrue(params.reasoning().get().toString().contains("low"))
        assertTrue(params.reasoning().get().toString().contains("concise"))
    }

    @Test
    fun `isMultiAgentModel matches xai multi agent family`() {
        assertTrue(XaiRequestMapper.isMultiAgentModel("grok-4.20-multi-agent"))
        assertTrue(XaiRequestMapper.isMultiAgentModel("grok-4.20-multi-agent-0309"))
        assertFalse(XaiRequestMapper.isMultiAgentModel("grok-3-mini"))
    }

    @Test
    fun `mapToResponseParams removes reasoning effort for grok 4 reasoning family`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4-fast-reasoning",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.HIGH,
            ),
        )

        assertFalse(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams allows reasoning effort for grok 3 mini`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-3-mini",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.HIGH,
            ),
        )

        assertTrue(params.reasoning().isPresent)
    }

    @Test
    fun `mapToChatCompletionParams removes reasoning effort for grok 3`() {
        val params = XaiRequestMapper.mapToChatCompletionParams(
            modelId = "grok-3",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.HIGH,
                maxTokens = 100,
            ),
        )

        assertFalse(params.reasoningEffort().isPresent)
        assertTrue(params.maxCompletionTokens().isPresent)
        assertEquals(100L, params.maxCompletionTokens().get())
        assertFalse(params.presencePenalty().isPresent)
        assertFalse(params.frequencyPenalty().isPresent)
        assertFalse(params.stop().isPresent)
    }

    @Test
    fun `mapToChatCompletionParams removes reasoning effort for grok 4 non reasoning family`() {
        val params = XaiRequestMapper.mapToChatCompletionParams(
            modelId = "grok-4-1-fast-non-reasoning",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.LOW,
                maxTokens = 100,
            ),
        )

        assertFalse(params.reasoningEffort().isPresent)
        assertTrue(params.maxCompletionTokens().isPresent)
        assertEquals(100L, params.maxCompletionTokens().get())
    }

    @Test
    fun `mapToResponseParams removes reasoning effort for grok 4 non reasoning family`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4-1-fast-non-reasoning",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.LOW,
            ),
        )

        assertFalse(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams never sends max tokens for multi agent suffix model`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4.20-multi-agent-0309",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 200,
            ),
        )

        assertFalse(params.maxOutputTokens().isPresent)
        assertFalse(params.tools().isPresent)
    }

    @Test
    fun `mapToChatCompletionParams never sends logprobs for grok 420 family`() {
        val params = XaiRequestMapper.mapToChatCompletionParams(
            modelId = "grok-4.20-reasoning",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(reasoningBudget = 0),
        )

        assertFalse(params.logprobs().isPresent)
    }

    @Test
    fun `chat fallback is disabled only for multi agent models`() {
        assertFalse(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-multi-agent"))
        assertFalse(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-multi-agent-0309"))
        assertTrue(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-reasoning"))
        assertTrue(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-code-fast-1"))
    }

    @Test
    fun `chat reasoning content classification matches documented legacy models`() {
        assertTrue(XaiRequestMapper.isChatReasoningContentModel("grok-3-mini"))
        assertTrue(XaiRequestMapper.isChatReasoningContentModel("grok-code-fast-1"))
        assertTrue(XaiRequestMapper.isChatReasoningContentModel("grok-code-fast-1-beta"))
        assertFalse(XaiRequestMapper.isChatReasoningContentModel("grok-4"))
    }

    @Test
    fun `mapToResponseParams includes image uris in user message`() {
        val tempFile = java.io.File.createTempFile("test", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            deleteOnExit()
        }
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4-1-fast-non-reasoning",
            prompt = "what is in this image?",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                imageUris = listOf(tempFile.toURI().toString())
            )
        )

        val input = params.input().orElseThrow().toString()
        assertTrue(input.contains("what is in this image?"))
        assertTrue(input.contains("imageUrl"))
        assertTrue(input.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToChatCompletionParams includes image uris in user message`() {
        val tempFile = java.io.File.createTempFile("test", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            deleteOnExit()
        }
        val params = XaiRequestMapper.mapToChatCompletionParams(
            modelId = "grok-4-1-fast-non-reasoning",
            prompt = "describe image",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                imageUris = listOf(tempFile.toURI().toString())
            )
        )

        val chatBody = params.toString()
        assertTrue(chatBody.contains("describe image"))
        assertTrue(chatBody.contains("image_url"))
        assertTrue(chatBody.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToResponseParams serializes tavily_web_search when tooling is enabled`() {
        val params = XaiRequestMapper.mapToResponseParams(
            modelId = "grok-4-1-fast-non-reasoning",
            prompt = "find recent android tool news",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            )
        )

        assertTrue(params.toString().contains("tavily_web_search"))
    }
}
