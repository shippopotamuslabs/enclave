package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class OpenAiRequestMapperTest {
    @Test
    fun `LTD-04 provider native request mappers use activeTools only`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "Search and write",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        toolCatalog =
                            listOf(
                                catalogEntry("web_search", ToolDisclosureMode.PRELOADED),
                                catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND),
                            ),
                        activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.WEB_SEARCH),
                    ),
            )

        val toolsPayload = params.tools().orElseThrow().toString()
        assertTrue(toolsPayload.contains("search_tools"))
        assertTrue(toolsPayload.contains("web_search"))
    }

    @Test
    fun `MUT-01 deferred schema never leaks before disclosure`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "Search and write",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        toolCatalog = listOf(catalogEntry("append_blocks", ToolDisclosureMode.DISCLOSE_ON_DEMAND)),
                        activeTools = listOf(ToolDefinition.SEARCH_TOOLS, ToolDefinition.WEB_SEARCH),
                    ),
            )

        val toolsPayload = params.tools().orElseThrow().toString()
        assertTrue(toolsPayload.contains("search_tools"))
        assertTrue(toolsPayload.contains("web_search"))
        assertFalse(
            params
                .tools()
                .orElseThrow()
                .toString()
                .contains("\"append_blocks\""),
        )
    }

    private fun catalogEntry(
        name: String,
        disclosureMode: ToolDisclosureMode,
    ): ToolCatalogEntry =
        ToolCatalogEntry(
            name = name,
            description = "$name description",
            disclosureMode = disclosureMode,
        )

    @Test
    fun `mapToResponseParams correctly maps model, prompt, and history`() {
        val history =
            listOf(
                ChatMessage(Role.SYSTEM, "You are a helpful assistant."),
                ChatMessage(Role.USER, "Hello"),
                ChatMessage(Role.ASSISTANT, "Hi there!"),
            )
        val options =
            GenerationOptions(
                reasoningBudget = 0,
                temperature = 0.7f,
                maxTokens = 100,
            )

        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "How are you?",
                history = history,
                options = options,
            )

        assertTrue(
            params
                .model()
                .get()
                .toString()
                .contains("gpt-4o"),
        )
        assertTrue(params.input().isPresent)
        assertTrue(params.instructions().isPresent)
        assertEquals("You are a helpful assistant.", params.instructions().get())
        assertFalse(
            params
                .input()
                .get()
                .toString()
                .contains("role=system"),
        )
    }

    @Test
    fun `mapToResponseParams sets prompt cache key from chat id`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

        assertEquals("chat-123", params.promptCacheKey().orElse(null))
    }

    @Test
    fun `mapToResponseParams includes reasoning effort when configured`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-5",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.XHIGH,
                    ),
            )

        assertTrue(params.reasoning().isPresent)
        assertTrue(
            params
                .reasoning()
                .get()
                .toString()
                .contains("xhigh"),
        )
        assertTrue(
            params
                .reasoning()
                .get()
                .toString()
                .contains("concise"),
        )
    }

    @Test
    fun `mapToResponseParams omits temperature when excluded for openai reasoning models`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-5.4-nano",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        apiProvider = ApiProvider.OPENAI,
                        modelId = "gpt-5.4-nano",
                        temperature = 0.7f,
                        temperatureDisabled = true,
                    ),
            )

        assertFalse(params.temperature().isPresent)
    }

    @Test
    fun `mapToResponseParams omits unsupported sampling parameters for openai nano models`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-5.4-nano",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        apiProvider = ApiProvider.OPENAI,
                        modelId = "gpt-5.4-nano",
                        temperature = 0.7f,
                        topP = 0.9f,
                    ),
            )

        assertFalse(params.temperature().isPresent)
        assertFalse(params.topP().isPresent)
    }

    @Test
    fun `mapToResponseParams includes max output tokens for generic openai models`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        maxTokens = 100,
                    ),
            )

        assertTrue(params.maxOutputTokens().isPresent)
        assertEquals(100L, params.maxOutputTokens().get())
    }

    @Test
    fun `mapToResponseParams excludes synthetic assistant error history`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "retry",
                history =
                    listOf(
                        ChatMessage(Role.USER, "hi"),
                        ChatMessage(Role.ASSISTANT, "Error: API Error (OPENAI): 400: null"),
                    ),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertEquals(2, params.input().orElseThrow().asResponse().size)
    }

    @Test
    fun `mapToResponseParams serializes web_search when tooling is enabled`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "Find recent Android agent news",
                history =
                    listOf(
                        ChatMessage(Role.SYSTEM, "Be concise."),
                        ChatMessage(Role.USER, "Hello"),
                    ),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.WEB_SEARCH),
                    ),
            )

        assertTrue(params.toString().contains("web_search"))
        assertTrue(params.toString().contains("query"))
    }

    @Test
    fun `mapToResponseParams serializes attached image inspect with question parameter`() {
        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "Inspect the image",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.ATTACHED_IMAGE_INSPECT),
                    ),
            )

        assertTrue(params.toString().contains("attached_image_inspect"))
        assertTrue(params.toString().contains("question"))
        assertFalse(params.toString().contains("\"required\"=[\"query\"]"))
    }

    @Test
    fun `mapToResponseParams includes input image content when image uris are present`() {
        val imageUri = createTempImageUri()

        val params =
            OpenAiRequestMapper.mapToResponseParams(
                modelId = "gpt-4o",
                prompt = "Describe this",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(imageUri),
                    ),
            )

        assertTrue(
            params
                .input()
                .orElseThrow()
                .toString()
                .contains("data:image/jpeg;base64"),
        )
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("openai-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
