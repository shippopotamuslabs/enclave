package com.browntowndev.enclave.feature.inference

import com.anthropic.models.messages.MessageParam
import com.browntowndev.enclave.domain.model.chat.ChatMessage
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
import com.browntowndev.enclave.domain.model.chat.Role as DomainRole

class AnthropicRequestMapperTest {
    @Test
    fun `mapToMessageParams maps system prompt history and reasoning budget`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-2.1",
                prompt = "How are you?",
                history =
                    listOf(
                        ChatMessage(DomainRole.SYSTEM, "You are a concise assistant."),
                        ChatMessage(DomainRole.USER, "Hello"),
                        ChatMessage(DomainRole.ASSISTANT, "Hi there!"),
                        ChatMessage(DomainRole.ASSISTANT, "Error: API Error (ANTHROPIC): 400: null"),
                    ),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        systemPrompt = "Use short answers.",
                        reasoningEffort = ApiReasoningEffort.HIGH,
                        temperature = 0.7f,
                        topP = 0.9f,
                        topK = 32,
                        maxTokens = 512,
                    ),
            )

        assertEquals("claude-2.1", params.model().toString())
        assertEquals(
            listOf(MessageParam.Role.USER, MessageParam.Role.ASSISTANT, MessageParam.Role.USER),
            params.messages().map { message -> message.role() },
        )
        assertEquals(
            "Use short answers.\n\nYou are a concise assistant.",
            params.system().get().asString(),
        )
        assertTrue(params.thinking().get().isEnabled())
        assertEquals(
            2048L,
            params
                .thinking()
                .get()
                .asEnabled()
                .budgetTokens(),
        )
        assertEquals(0.7, params.temperature().get(), 0.0001)
        assertEquals(0.9, params.topP().get(), 0.0001)
        assertEquals(32L, params.topK().get())
        assertEquals(512L, params.maxTokens())
        assertFalse(params.toString().contains("Error: API Error"))
    }

    @Test
    fun `mapToMessageParams prefers explicit reasoning budget over effort mapping`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-sonnet-4-20250514",
                prompt = "Explain the plan.",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 4096,
                        reasoningEffort = ApiReasoningEffort.LOW,
                        maxTokens = 1024,
                    ),
            )

        assertTrue(params.thinking().get().isEnabled())
        assertEquals(
            4096L,
            params
                .thinking()
                .get()
                .asEnabled()
                .budgetTokens(),
        )
        assertEquals(1, params.messages().size)
        assertEquals(MessageParam.Role.USER, params.messages().first().role())
    }

    @Test
    fun `LTD-04 Anthropic request mapper uses activeTools only`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-sonnet-4-20250514",
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
                        activeTools = listOf(ToolDefinition.DISCLOSE_TOOL_SCHEMAS, ToolDefinition.WEB_SEARCH),
                    ),
            )

        val registeredToolNames =
            params
                .tools()
                .orElse(emptyList())
                .mapNotNull { union -> union.tool().orElse(null)?.name() }

        assertEquals(listOf("disclose_tool_schemas", "web_search"), registeredToolNames)
        assertEquals(2, registeredToolNames.size)
    }

    @Test
    fun `mapToMessageParams serializes web_search when tooling is enabled`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-sonnet-4-20250514",
                prompt = "Find recent Android agent news",
                history = emptyList(),
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
    fun `mapToMessageParams serializes attached image inspect with question parameter`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-sonnet-4-20250514",
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
    fun `mapToMessageParams includes image blocks when image uris are present`() {
        val params =
            AnthropicRequestMapper.mapToMessageParams(
                modelId = "claude-sonnet-4-20250514",
                prompt = "Describe this",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(createTempImageUri()),
                    ),
            )

        assertEquals(1, params.messages().size)
        assertTrue(
            params
                .messages()
                .single()
                .toString()
                .contains("image"),
        )
        assertTrue(
            params
                .messages()
                .single()
                .toString()
                .contains("base64"),
        )
    }

    private fun createTempImageUri(): String {
        val file = File.createTempFile("anthropic-image", ".jpg")
        file.writeBytes(byteArrayOf(1, 2, 3, 4))
        return file.toURI().toString()
    }
}
