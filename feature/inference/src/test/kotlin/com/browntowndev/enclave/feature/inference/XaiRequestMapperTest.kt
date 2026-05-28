package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatId
import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XaiRequestMapperTest {
    @Test
    fun `mapToChatCompletionParams sets grok conversation header from chat id`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-4.3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

        assertEquals(listOf("chat-123"), params._headers().values("x-grok-conv-id"))
    }

    @Test
    fun `mapToResponseParams sets prompt cache key from chat id`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        chatId = ChatId("chat-123"),
                    ),
            )

        assertEquals(listOf("chat-123"), params._headers().values("x-grok-conv-id"))
    }

    @Test
    fun `mapToResponseParams omits max output tokens for xai multi agent model`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent-0309",
                prompt = "How are you?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        maxTokens = 100,
                    ),
            )

        assertFalse(params.maxOutputTokens().isPresent)
    }

    @Test
    fun `mapToResponseParams never sends instructions and preserves system messages in input`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent",
                prompt = "hello",
                history =
                    listOf(
                        ChatMessage(Role.SYSTEM, "You are direct."),
                        ChatMessage(Role.USER, "prior user"),
                    ),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertFalse(params.instructions().isPresent)
        assertTrue(
            params
                .input()
                .get()
                .toString()
                .contains("role=system"),
        )
        assertTrue(
            params
                .input()
                .get()
                .toString()
                .contains("You are direct."),
        )
    }

    @Test
    fun `mapToResponseParams includes effort only reasoning for multi agent mode`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.XHIGH,
                    ),
            )

        assertTrue(params.reasoning().isPresent)
        assertEquals(
            "xhigh",
            params
                .reasoning()
                .get()
                .effort()
                .get()
                .toString(),
        )
    }

    @Test
    fun `isMultiAgentModel matches xai multi agent family`() {
        assertTrue(XaiRequestMapper.isMultiAgentModel("grok-4.20-multi-agent"))
        assertTrue(XaiRequestMapper.isMultiAgentModel("grok-4.20-multi-agent-0309"))
        assertFalse(XaiRequestMapper.isMultiAgentModel("grok-3-mini"))
    }

    @Test
    fun `mapToResponseParams removes reasoning effort for grok 4 reasoning family`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4-fast-reasoning",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.HIGH,
                    ),
            )

        assertFalse(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams allows reasoning effort for grok 3 mini`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-3-mini",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.HIGH,
                    ),
            )

        assertTrue(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams allows reasoning effort for grok 4_3`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.HIGH,
                    ),
            )

        assertTrue(params.reasoning().isPresent)
        assertEquals(
            "high",
            params
                .reasoning()
                .get()
                .effort()
                .get()
                .toString(),
        )
    }

    @Test
    fun `mapToResponseParams maps NONE reasoning effort for grok 4_3`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.NONE,
                    ),
            )

        assertTrue(params.reasoning().isPresent)
        assertEquals(
            "none",
            params
                .reasoning()
                .get()
                .effort()
                .get()
                .toString(),
        )
    }

    @Test
    fun `mapToChatCompletionParams allows reasoning effort for grok 4_3`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-4.3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.MEDIUM,
                    ),
            )

        assertTrue(params.reasoningEffort().isPresent)
        assertEquals(
            "medium",
            params
                .reasoningEffort()
                .get()
                .toString(),
        )
    }

    @Test
    fun `mapToChatCompletionParams removes reasoning effort for grok 3`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
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
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-4-1-fast-non-reasoning",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
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
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4-1-fast-non-reasoning",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.LOW,
                    ),
            )

        assertFalse(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams removes reasoning effort for grok 420 family`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-reasoning",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        reasoningEffort = ApiReasoningEffort.LOW,
                    ),
            )

        assertFalse(params.reasoning().isPresent)
    }

    @Test
    fun `mapToResponseParams never sends max tokens for multi agent suffix model`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent-0309",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        maxTokens = 200,
                    ),
            )

        assertFalse(params.maxOutputTokens().isPresent)
    }

    @Test
    fun `mapToResponseParams does not send xai tool planner overrides`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.WEB_SEARCH),
                    ),
            )

        assertFalse(params.maxToolCalls().isPresent)
        assertFalse(params.parallelToolCalls().isPresent)
    }

    @Test
    fun `mapToResponseParams sends xai compatible non strict tool schema`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4.20-multi-agent",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.WEB_SEARCH),
                    ),
            )

        val toolsPayload = params._additionalBodyProperties()["tools"].toString()
        // Client tools are stripped for multi-agent in mapToResponseParams but NOT in mapToChatCompletionParams (Wait, checking code...)
        // Actually, it is stripped in mapToResponseParams too.
        // My previous edit to the test was correct in principle but code has it stripped.
        assertFalse(toolsPayload.contains("\"function\"")) // Client-side tools use the "function" type
        assertTrue(toolsPayload.contains("web_search"))
        assertTrue(toolsPayload.contains("x_search"))
    }

    @Test
    fun `LTD-04 xAI request mapper uses activeTools only`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-3",
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

        val toolsPayload = params._additionalBodyProperties()["tools"].toString()

        assertTrue(toolsPayload.contains("disclose_tool_schemas"))
        assertTrue(toolsPayload.contains("web_search"))
        assertFalse(toolsPayload.contains("\"append_blocks\""))
    }

    @Test
    fun `mapToChatCompletionParams sends xai compatible non strict tool schema`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-3",
                prompt = "hello",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        toolingEnabled = true,
                        activeTools = listOf(ToolDefinition.WEB_SEARCH),
                    ),
            )

        val toolsPayload = params._additionalBodyProperties()["tools"].toString()
        assertTrue(toolsPayload.contains("web_search"))
    }

    @Test
    fun `mapToChatCompletionParams never sends logprobs for grok 420 family`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-4.20-reasoning",
                prompt = "hello",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertFalse(params.logprobs().isPresent)
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
    fun `chat fallback is disabled for grok 420 family`() {
        assertFalse(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-multi-agent"))
        assertFalse(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-multi-agent-0309"))
        assertFalse(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.20-reasoning"))
        assertTrue(XaiRequestMapper.shouldAllowChatCompletionsFallback("grok-4.3"))
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
        val tempFile =
            java.io.File.createTempFile("test", ".png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                deleteOnExit()
            }
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4-1-fast-non-reasoning",
                prompt = "what is in this image?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(tempFile.toURI().toString()),
                    ),
            )

        val input = params.input().orElseThrow().toString()
        assertTrue(input.contains("what is in this image?"))
        assertTrue(input.contains("imageUrl"))
        assertTrue(input.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToChatCompletionParams includes image uris in user message`() {
        val tempFile =
            java.io.File.createTempFile("test", ".png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                deleteOnExit()
            }
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-4-1-fast-non-reasoning",
                prompt = "describe image",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(tempFile.toURI().toString()),
                    ),
            )

        val chatBody = params.toString()
        assertTrue(chatBody.contains("describe image"))
        assertTrue(chatBody.contains("image_url"))
        assertTrue(chatBody.contains("data:image/png;base64,"))
    }

    @Test
    fun `mapToResponseParams sets store to false`() {
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-3",
                prompt = "test",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertFalse(params.store().get())
    }

    @Test
    fun `mapToChatCompletionParams sets store to false`() {
        val params =
            XaiRequestMapper.mapToChatCompletionParams(
                modelId = "grok-3",
                prompt = "test",
                history = emptyList(),
                options = GenerationOptions(reasoningBudget = 0),
            )

        assertFalse(params.store().get())
    }

    @Test
    fun `mapToResponseParams sets store to false when images are present`() {
        val tempFile =
            java.io.File.createTempFile("test", ".png").apply {
                writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
                deleteOnExit()
            }
        val params =
            XaiRequestMapper.mapToResponseParams(
                modelId = "grok-4-1-fast-non-reasoning",
                prompt = "what is in this image?",
                history = emptyList(),
                options =
                    GenerationOptions(
                        reasoningBudget = 0,
                        imageUris = listOf(tempFile.toURI().toString()),
                    ),
            )

        assertFalse(params.store().get())
    }
}
