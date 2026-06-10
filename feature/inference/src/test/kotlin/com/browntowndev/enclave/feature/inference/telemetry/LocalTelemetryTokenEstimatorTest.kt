package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.chat.Role
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

        assertEquals(9, estimate)
    }
}