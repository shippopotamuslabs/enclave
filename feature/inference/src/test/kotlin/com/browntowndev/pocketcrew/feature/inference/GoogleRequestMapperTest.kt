package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import com.browntowndev.pocketcrew.domain.model.chat.Role as DomainRole

class GoogleRequestMapperTest {
    @Test
    fun `mapToGenerateContentRequest maps system prompt history and reasoning budget`() {
        val history =
            listOf(
                ChatMessage(DomainRole.SYSTEM, "You are a helpful assistant."),
                ChatMessage(DomainRole.USER, "Hello"),
                ChatMessage(DomainRole.ASSISTANT, "Hi there!"),
            )
        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = "How are you?",
                history = history,
                options =
                    GenerationOptions(
                        reasoningBudget = 1024,
                        systemPrompt = "Stay concise.",
                        temperature = 0.7f,
                        topP = 0.9f,
                        topK = 32,
                        maxTokens = 512,
                        apiProvider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.GOOGLE,
                        modelId = "gemini-1.5-flash",
                    ),
            )

        assertEquals(3, request.contents.size)
        assertEquals("user", request.contents[0].role().orElseThrow())
        assertEquals("model", request.contents[1].role().orElseThrow())
        assertEquals("user", request.contents[2].role().orElseThrow())
        assertEquals(
            "Hello",
            request.contents[0]
                .parts()
                .orElseThrow()
                .single()
                .text()
                .orElseThrow(),
        )
        assertEquals(
            "Hi there!",
            request.contents[1]
                .parts()
                .orElseThrow()
                .single()
                .text()
                .orElseThrow(),
        )
        assertEquals(
            "How are you?",
            request.contents[2]
                .parts()
                .orElseThrow()
                .single()
                .text()
                .orElseThrow(),
        )
        assertEquals(
            "Stay concise.\n\nYou are a helpful assistant.",
            request.config
                .systemInstruction()
                .orElseThrow()
                .parts()
                .orElseThrow()
                .single()
                .text()
                .orElseThrow(),
        )
        assertEquals(
            1024,
            request.config
                .thinkingConfig()
                .orElseThrow()
                .thinkingBudget()
                .orElseThrow(),
        )
        assertTrue(
            request.config
                .thinkingConfig()
                .orElseThrow()
                .includeThoughts()
                .orElse(false),
        )
        assertEquals(0.7f, request.config.temperature().orElseThrow())
        assertEquals(0.9f, request.config.topP().orElseThrow())
        assertEquals(32f, request.config.topK().orElseThrow())
        assertEquals(512, request.config.maxOutputTokens().orElseThrow())
        assertFalse(
            request.contents.any { content ->
                content.parts().orElse(emptyList()).any { part ->
                    part.text().orElse("").startsWith("Error: API Error")
                }
            },
        )
    }

    @Test
    fun `mapToGenerateContentRequest omits thinking config when reasoning budget is disabled`() {
        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = "Hello",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertTrue(request.config.thinkingConfig().isEmpty)
        assertEquals(1, request.contents.size)
        assertEquals(
            "user",
            request.contents
                .single()
                .role()
                .orElseThrow(),
        )
    }

    @Test
    fun `mapToGenerateContentRequest adds function declarations when tooling is enabled`() {
        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = "Search news",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        availableTools = listOf(ToolDefinition.WEB_SEARCH),
                        apiProvider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.GOOGLE,
                        modelId = "gemini-1.5-flash",
                    ),
            )

        val tool =
            request.config
                .tools()
                .orElseThrow()
                .single()
        val functionDeclaration = tool.functionDeclarations().orElseThrow().single()
        val parameters = functionDeclaration.parameters().orElseThrow()

        assertEquals("web_search", functionDeclaration.name().orElseThrow())
        assertTrue(request.config.toolConfig().isPresent)
        assertTrue(
            request.config
                .toolConfig()
                .orElseThrow()
                .functionCallingConfig()
                .isPresent,
        )
        assertTrue(parameters.properties().orElseThrow().containsKey("query"))
        assertEquals(listOf("query"), parameters.required().orElseThrow())
    }

    @Test
    fun `mapToGenerateContentRequest serializes attached image inspect with question parameter`() {
        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = "Describe this",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        availableTools = listOf(ToolDefinition.ATTACHED_IMAGE_INSPECT),
                        apiProvider = com.browntowndev.pocketcrew.domain.model.inference.ApiProvider.GOOGLE,
                        modelId = "gemini-1.5-flash",
                    ),
            )

        val functionDeclaration =
            request.config
                .tools()
                .orElseThrow()
                .single()
                .functionDeclarations()
                .orElseThrow()
                .single()
        val parameters = functionDeclaration.parameters().orElseThrow()

        assertEquals("attached_image_inspect", functionDeclaration.name().orElseThrow())
        assertTrue(parameters.properties().orElseThrow().containsKey("question"))
        assertEquals(listOf("question"), parameters.required().orElseThrow())
    }

    @Test
    fun `mapToGenerateContentRequest includes inline image parts when image uris are present`() {
        val request =
            GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = "Describe this",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(createTempImageUri()),
                    ),
            )

        val parts =
            request.contents
                .single()
                .parts()
                .orElseThrow()
        assertEquals(2, parts.size)
        assertTrue(parts[1].inlineData().isPresent)
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("google-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
