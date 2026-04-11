package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiRequestMapperTest {

    @Test
    fun `mapToChatCompletionParams correctly maps model, prompt, and history`() {
        val history = listOf(
            ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        val options = GenerationOptions(
            reasoningBudget = 0,
            temperature = 0.7f,
            maxTokens = 100
        )

        val params = OpenAiRequestMapper.mapToChatCompletionParams(
            modelId = "gpt-4o",
            prompt = "How are you?",
            history = history,
            options = options
        )

        assertTrue(params.model().toString().contains("gpt-4o"))
        assertEquals(4, params.messages().size)
        assertTrue(params.messages().first().toString().contains("system="))
    }

    @Test
    fun `mapToResponseParams correctly maps model, prompt, and history`() {
        val history = listOf(
            ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
            ChatMessage(Role.USER, "Hello"),
            ChatMessage(Role.ASSISTANT, "Hi there!")
        )
        val options = GenerationOptions(
            reasoningBudget = 0,
            temperature = 0.7f,
            maxTokens = 100
        )

        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-4o",
            prompt = "How are you?",
            history = history,
            options = options
        )

        assertTrue(params.model().get().toString().contains("gpt-4o"))
        assertTrue(params.input().isPresent)
        assertTrue(params.instructions().isPresent)
        assertEquals("You are a helpful assistant.", params.instructions().get())
        assertFalse(params.input().get().toString().contains("role=system"))
    }

    @Test
    fun `mapToResponseParams includes reasoning effort when configured`() {
        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-5",
            prompt = "How are you?",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                reasoningEffort = ApiReasoningEffort.XHIGH
            )
        )

        assertTrue(params.reasoning().isPresent)
        assertTrue(params.reasoning().get().toString().contains("xhigh"))
        assertTrue(params.reasoning().get().toString().contains("concise"))
    }

    @Test
    fun `mapToResponseParams includes max output tokens for generic openai models`() {
        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-4o",
            prompt = "How are you?",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 100
            )
        )

        assertTrue(params.maxOutputTokens().isPresent)
        assertEquals(100L, params.maxOutputTokens().get())
    }

    @Test
    fun `mapToChatCompletionParams excludes synthetic assistant error history`() {
        val params = OpenAiRequestMapper.mapToChatCompletionParams(
            modelId = "gpt-4o",
            prompt = "retry",
            history = listOf(
                ChatMessage(Role.USER, "hi"),
                ChatMessage(Role.ASSISTANT, "Error: API Error (OPENAI): 400: null")
            ),
            options = GenerationOptions(reasoningBudget = 0)
        )

        assertEquals(2, params.messages().size)
    }

    @Test
    fun `mapToResponseParams serializes tavily_web_search when tooling is enabled`() {
        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-4o",
            prompt = "Find recent Android agent news",
            history = listOf(
                ChatMessage(Role.SYSTEM, "Be concise."),
                ChatMessage(Role.USER, "Hello"),
            ),
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            )
        )

        assertTrue(params.toString().contains("tavily_web_search"))
        assertTrue(params.toString().contains("query"))
    }

    @Test
    fun `mapToResponseParams includes input image content when image uris are present`() {
        val imageUri = createTempImageUri()

        val params = OpenAiRequestMapper.mapToResponseParams(
            modelId = "gpt-4o",
            prompt = "Describe this",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                imageUris = listOf(imageUri),
            )
        )

        assertTrue(params.input().orElseThrow().toString().contains("data:image/jpeg;base64"))
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("openai-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
