package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenRouterRequestMapperTest {

    @Test
    fun `mapToResponseParams adds openrouter fallback routing defaults`() {
        val params = OpenRouterRequestMapper.mapToResponseParams(
            modelId = "openai/gpt-5.2",
            prompt = "hello",
            history = listOf(ChatMessage(Role.SYSTEM, "You are concise.")),
            options = GenerationOptions(reasoningBudget = 0, maxTokens = 128)
        )

        assertTrue(params.model().get().toString().contains("openai/gpt-5.2"))
        assertTrue(params._additionalBodyProperties().containsKey("provider"))
        val providerBody = params._additionalBodyProperties()["provider"].toString()
        assertTrue(providerBody.contains("allow_fallbacks"))
        assertTrue(providerBody.contains("sort"))
        assertTrue(providerBody.contains("require_parameters"))
        assertEquals(128L, params.maxOutputTokens().get())
        assertEquals("You are concise.", params.instructions().get())
    }

    @Test
    fun `mapToChatCompletionParams adds openrouter fallback routing defaults`() {
        val params = OpenRouterRequestMapper.mapToChatCompletionParams(
            modelId = "openai/gpt-5.2",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(reasoningBudget = 0, maxTokens = 128)
        )

        assertEquals("openai/gpt-5.2", params.model().toString())
        assertTrue(params._additionalBodyProperties().containsKey("provider"))
        assertTrue(params._additionalBodyProperties()["provider"].toString().contains("allow_fallbacks"))
        assertEquals(128L, params.maxCompletionTokens().get())
    }

    @Test
    fun `mapToResponseParams applies custom openrouter routing settings`() {
        val params = OpenRouterRequestMapper.mapToResponseParams(
            modelId = "openai/gpt-5.2",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(reasoningBudget = 0),
            routing = OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.LATENCY,
                allowFallbacks = false,
                requireParameters = true,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                zeroDataRetention = true
            )
        )

        val providerBody = params._additionalBodyProperties()["provider"].toString()
        assertTrue(providerBody.contains("latency"))
        assertTrue(providerBody.contains("allow_fallbacks"))
        assertTrue(providerBody.contains("false"))
        assertTrue(providerBody.contains("require_parameters"))
        assertTrue(providerBody.contains("allow"))
        assertTrue(providerBody.contains("zdr"))
    }

    @Test
    fun `mapToResponseParams omits max output tokens when configured max equals context window`() {
        val params = OpenRouterRequestMapper.mapToResponseParams(
            modelId = "openai/gpt-5.2",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 262_144,
                contextWindow = 262_144,
            )
        )

        assertFalse(params.maxOutputTokens().isPresent)
    }

    @Test
    fun `mapToChatCompletionParams omits max completion tokens when configured max equals context window`() {
        val params = OpenRouterRequestMapper.mapToChatCompletionParams(
            modelId = "openai/gpt-5.2",
            prompt = "hello",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 262_144,
                contextWindow = 262_144,
            )
        )

        assertFalse(params.maxCompletionTokens().isPresent)
    }

    @Test
    fun `mapToResponseParams includes image uris in user message`() {
        val tempFile = java.io.File.createTempFile("test", ".png").apply {
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
            deleteOnExit()
        }
        val params = OpenRouterRequestMapper.mapToResponseParams(
            modelId = "openai/gpt-5.2",
            prompt = "what is in this image?",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                imageUris = listOf(tempFile.toURI().toString())
            )
        )

        // The image_url check in Responses API is through ResponseInputImage
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
        val params = OpenRouterRequestMapper.mapToChatCompletionParams(
            modelId = "openai/gpt-5.2",
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
        val params = OpenRouterRequestMapper.mapToResponseParams(
            modelId = "openai/gpt-5.2",
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
