package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.inference.ApiReasoningEffort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeepSeekRequestPolicyTest {
    @Test
    fun `usesMaxTokensField returns true for deepseek direct api models`() {
        assertTrue(DeepSeekRequestPolicy.usesMaxTokensField("deepseek-v4-pro"))
        assertTrue(DeepSeekRequestPolicy.usesMaxTokensField("deepseek-chat"))
    }

    @Test
    fun `shouldOmitToolChoice when V4 reasoning engaged with tools`() {
        assertTrue(
            DeepSeekRequestPolicy.shouldOmitToolChoice(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 2048,
                toolsAvailable = true,
            ),
        )
        assertFalse(
            DeepSeekRequestPolicy.shouldOmitToolChoice(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 0,
                toolsAvailable = true,
            ),
        )
    }

    @Test
    fun `thinkingExtraBody enabled when V4 reasoning engaged`() {
        assertEquals(
            mapOf("thinking" to mapOf("type" to "enabled")),
            DeepSeekRequestPolicy.thinkingExtraBody(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 1024,
            ),
        )
        assertNull(
            DeepSeekRequestPolicy.thinkingExtraBody(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 0,
            ),
        )
    }

    @Test
    fun `mapReasoningEffort maps effort when reasoning engaged`() {
        assertEquals(
            "high",
            DeepSeekRequestPolicy.mapReasoningEffort(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 1024,
                effort = ApiReasoningEffort.LOW,
            ),
        )
        assertNull(
            DeepSeekRequestPolicy.mapReasoningEffort(
                modelId = "deepseek-v4-pro",
                reasoningBudget = 0,
                effort = ApiReasoningEffort.LOW,
            ),
        )
    }
}