package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
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
}
