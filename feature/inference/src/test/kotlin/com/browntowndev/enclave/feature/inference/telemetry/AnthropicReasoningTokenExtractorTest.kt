package com.browntowndev.enclave.feature.inference.telemetry

import com.anthropic.core.JsonValue
import com.anthropic.models.messages.MessageDeltaUsage
import com.anthropic.models.messages.Usage
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Covers [AnthropicReasoningTokenExtractor] across non-standard Anthropic-gateway wire shapes:
 * top-level `reasoning_tokens`, nested `completion_tokens_details.reasoning_tokens`,
 * both present (nested wins), absent (null, the real-Anthropic case), and malformed (null, no throw).
 *
 * MockK stubs `_additionalProperties()` on the SDK types (matching [AnthropicCacheTokenCaptureTest]'s
 * approach) because the Stainless builders enforce required fields that are irrelevant here.
 */
class AnthropicReasoningTokenExtractorTest {
    @Test
    fun `top-level reasoning_tokens on Usage is parsed`() {
        val usage = usageWith(mapOf("reasoning_tokens" to JsonValue.from(128)))

        assertEquals(128, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `nested completion_tokens_details reasoning_tokens on Usage is parsed`() {
        val usage =
            usageWith(
                mapOf(
                    "completion_tokens_details" to JsonValue.from(mapOf("reasoning_tokens" to 2048)),
                ),
            )

        assertEquals(2048, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `nested reasoning_tokens wins over top-level when both are present`() {
        val usage =
            usageWith(
                mapOf(
                    "reasoning_tokens" to JsonValue.from(100),
                    "completion_tokens_details" to JsonValue.from(mapOf("reasoning_tokens" to 999)),
                ),
            )

        assertEquals(999, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `absent reasoning fields return null for standard Anthropic Usage`() {
        val usage = usageWith(emptyMap())

        assertEquals(null, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `malformed reasoning_tokens value returns null without throwing`() {
        val usage = usageWith(mapOf("reasoning_tokens" to JsonValue.from("not-a-number")))

        assertEquals(null, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `string-encoded reasoning_tokens value is parsed`() {
        val usage = usageWith(mapOf("reasoning_tokens" to JsonValue.from("42")))

        assertEquals(42, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `top-level reasoning_tokens on MessageDeltaUsage is parsed`() {
        val usage = deltaUsageWith(mapOf("reasoning_tokens" to JsonValue.from(512)))

        assertEquals(512, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `nested reasoning_tokens on MessageDeltaUsage is parsed`() {
        val usage =
            deltaUsageWith(
                mapOf(
                    "completion_tokens_details" to JsonValue.from(mapOf("reasoning_tokens" to 4096)),
                ),
            )

        assertEquals(4096, AnthropicReasoningTokenExtractor.extract(usage))
    }

    @Test
    fun `absent reasoning fields return null for MessageDeltaUsage`() {
        val usage = deltaUsageWith(emptyMap())

        assertEquals(null, AnthropicReasoningTokenExtractor.extract(usage))
    }

    private fun usageWith(additionalProperties: Map<String, JsonValue>): Usage =
        mockk {
            every { _additionalProperties() } returns additionalProperties
        }

    private fun deltaUsageWith(additionalProperties: Map<String, JsonValue>): MessageDeltaUsage =
        mockk {
            every { _additionalProperties() } returns additionalProperties
        }
}
