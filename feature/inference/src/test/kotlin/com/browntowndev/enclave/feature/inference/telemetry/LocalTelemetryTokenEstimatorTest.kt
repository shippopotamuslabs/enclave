package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ToolCatalogEntry
import com.browntowndev.enclave.domain.model.inference.ToolDisclosureMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LocalTelemetryTokenEstimatorTest {
    @Test
    fun `estimateTokenCount returns null for empty text`() {
        assertNull(estimateTokenCount(""))
    }

    @Test
    fun `estimateTokenCount uses length divided by four`() {
        assertEquals(3, estimateTokenCount("abcdefghijkl"))
    }

    @Test
    fun `resolveLiteRtInputTelemetry derives cache read from estimated total minus new prefill`() {
        val telemetry =
            resolveLiteRtInputTelemetry(
                benchmarkPrefillTokens = 44,
                estimatedTotalPromptTokens = 500,
            )

        assertEquals(500, telemetry.inputTokens)
        assertEquals(456, telemetry.cacheReadInputTokens)
    }

    @Test
    fun `resolveLiteRtInputTelemetry leaves cache read null on first turn`() {
        val telemetry =
            resolveLiteRtInputTelemetry(
                benchmarkPrefillTokens = 120,
                estimatedTotalPromptTokens = 120,
            )

        assertEquals(120, telemetry.inputTokens)
        assertNull(telemetry.cacheReadInputTokens)
    }

    @Test
    fun `estimateLiteRtPromptTokens includes history system prompt and current prompt`() {
        val estimate =
            estimateLiteRtPromptTokens(
                history =
                    listOf(
                        ChatMessage(Role.USER, "abcdefghijklmnop"),
                        ChatMessage(Role.ASSISTANT, "qrstuvwxyz"),
                    ),
                currentPrompt = "1234",
                systemPrompt = "system",
            )

        assertEquals(49, estimate)
    }

    @Test
    fun `estimateLiteRtPromptTokens includes tools volatile context and images`() {
        val options =
            GenerationOptions(
                reasoningBudget = 0,
                toolingEnabled = true,
                toolCatalog =
                    listOf(
                        ToolCatalogEntry(
                            name = "test_tool",
                            description = "A description of test_tool",
                            disclosureMode = ToolDisclosureMode.PRELOADED,
                        ),
                    ),
                volatileTurnContext = "volatile_context",
                imageUris = listOf("uri1", "uri2"),
            )

        val estimate =
            estimateLiteRtPromptTokens(
                history =
                    listOf(
                        ChatMessage(Role.USER, "user_msg"),
                    ),
                currentPrompt = "current_prompt",
                systemPrompt = "sys_prompt",
                options = options,
            )

        assertEquals(1222, estimate)
    }
}