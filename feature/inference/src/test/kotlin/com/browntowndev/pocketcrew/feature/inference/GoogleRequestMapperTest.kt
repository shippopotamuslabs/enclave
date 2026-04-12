package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role as DomainRole
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GoogleRequestMapperTest {

    @Test
    fun `mapToGenerateContentRequest maps system prompt history and reasoning budget`() {
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = "How are you?",
            history = listOf(
                ChatMessage(DomainRole.SYSTEM, "You are a concise assistant."),
                ChatMessage(DomainRole.USER, "Hello"),
                ChatMessage(DomainRole.ASSISTANT, "Hi there!"),
                ChatMessage(DomainRole.ASSISTANT, "Error: API Error (GOOGLE): 400: invalid"),
            ),
            options = GenerationOptions(
                reasoningBudget = 2048,
                systemPrompt = "Use short answers.",
                temperature = 0.7f,
                topP = 0.9f,
                topK = 32,
                maxTokens = 512,
            )
        )

        assertEquals(3, request.contents.size)
        assertEquals("user", request.contents[0].role().orElseThrow())
        assertEquals("model", request.contents[1].role().orElseThrow())
        assertEquals("user", request.contents[2].role().orElseThrow())
        assertEquals("Hello", request.contents[0].parts().orElseThrow().single().text().orElseThrow())
        assertEquals("Hi there!", request.contents[1].parts().orElseThrow().single().text().orElseThrow())
        assertEquals("How are you?", request.contents[2].parts().orElseThrow().single().text().orElseThrow())
        assertEquals(
            "Use short answers.\n\nYou are a concise assistant.",
            request.config.systemInstruction().orElseThrow().parts().orElseThrow().single().text().orElseThrow()
        )
        assertEquals(2048, request.config.thinkingConfig().orElseThrow().thinkingBudget().orElseThrow())
        assertTrue(request.config.thinkingConfig().orElseThrow().includeThoughts().orElse(false))
        assertEquals(0.7f, request.config.temperature().orElseThrow())
        assertEquals(0.9f, request.config.topP().orElseThrow())
        assertEquals(32f, request.config.topK().orElseThrow())
        assertEquals(512, request.config.maxOutputTokens().orElseThrow())
        assertFalse(request.contents.any { content ->
            content.parts().orElse(emptyList()).any { part ->
                part.text().orElse("").startsWith("Error: API Error")
            }
        })
    }

    @Test
    fun `mapToGenerateContentRequest omits thinking config when reasoning budget is disabled`() {
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = "Explain the plan.",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                maxTokens = 1024,
            )
        )

        assertTrue(request.config.thinkingConfig().isEmpty)
        assertEquals(1, request.contents.size)
        assertEquals("user", request.contents.single().role().orElseThrow())
    }

    @Test
    fun `mapToGenerateContentRequest adds function declarations when tooling is enabled`() {
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = "Find the latest Android tooling news.",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.TAVILY_WEB_SEARCH),
            )
        )

        val tool = request.config.tools().orElseThrow().single()
        val functionDeclaration = tool.functionDeclarations().orElseThrow().single()
        val parameters = functionDeclaration.parameters().orElseThrow()

        assertEquals("tavily_web_search", functionDeclaration.name().orElseThrow())
        assertTrue(request.config.toolConfig().isPresent)
        assertEquals(
            listOf("tavily_web_search"),
            request.config.toolConfig().orElseThrow()
                .functionCallingConfig().orElseThrow()
                .allowedFunctionNames().orElseThrow()
        )
        assertTrue(parameters.properties().orElseThrow().containsKey("query"))
        assertEquals(listOf("query"), parameters.required().orElseThrow())
    }

    @Test
    fun `mapToGenerateContentRequest includes inline image parts when image uris are present`() {
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = "Describe this",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                imageUris = listOf(createTempImageUri()),
            )
        )

        val parts = request.contents.single().parts().orElseThrow()
        assertEquals(2, parts.size)
        assertTrue(parts[1].inlineData().isPresent)
    }

    @Test
    fun `mapToGenerateContentRequest serializes attached image inspect with question parameter`() {
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = "Inspect the image",
            history = emptyList(),
            options = GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                availableTools = listOf(ToolDefinition.ATTACHED_IMAGE_INSPECT),
            )
        )

        val functionDeclaration = request.config.tools().orElseThrow().single()
            .functionDeclarations().orElseThrow().single()
        val parameters = functionDeclaration.parameters().orElseThrow()

        assertEquals("attached_image_inspect", functionDeclaration.name().orElseThrow())
        assertTrue(parameters.properties().orElseThrow().containsKey("question"))
        assertEquals(listOf("question"), parameters.required().orElseThrow())
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("google-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
