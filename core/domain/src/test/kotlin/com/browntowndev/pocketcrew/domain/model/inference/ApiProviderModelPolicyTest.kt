package com.browntowndev.pocketcrew.domain.model.inference

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiProviderModelPolicyTest {

    private data class XaiClassificationExpectation(
        val modelId: String,
        val isMultiAgent: Boolean = false,
        val isGrok420Reasoning: Boolean = false,
        val isGrok4ReasoningFamily: Boolean = false,
        val isGrok4NonReasoningFamily: Boolean = false,
        val isGrok3: Boolean = false,
        val isGrok3Mini: Boolean = false,
        val isReasoningModel: Boolean = false,
        val isGrok420Family: Boolean = false,
        val isChatReasoningContentModel: Boolean = false,
    )

    @Test
    fun `supports model discovery for openai anthropic google openrouter and xai`() {
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.OPENAI))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.ANTHROPIC))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.GOOGLE))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.OPENROUTER))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.XAI))
    }

    @Test
    fun `provider defaults include hosted endpoints for google openrouter and xai`() {
        assertEquals("https://generativelanguage.googleapis.com", ApiProvider.GOOGLE.defaultBaseUrl())
        assertEquals("https://openrouter.ai/api/v1", ApiProvider.OPENROUTER.defaultBaseUrl())
        assertEquals("https://api.x.ai/v1", ApiProvider.XAI.defaultBaseUrl())
        assertEquals(null, ApiProvider.OPENAI.defaultBaseUrl())
        assertEquals(null, ApiProvider.ANTHROPIC.defaultBaseUrl())
    }

    @Test
    fun `anthropic parameter support keeps the standard preset controls but hides unsupported sliders`() {
        val support = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.ANTHROPIC,
            modelId = "claude-sonnet-4-20250514",
        )

        assertTrue(support.supportsReasoningEffort)
        assertTrue(support.supportsTopK)
        assertTrue(support.supportsMaxTokens)
        assertFalse(support.supportsMinP)
        assertFalse(support.supportsFrequencyPenalty)
        assertFalse(support.supportsPresencePenalty)
    }

    @Test
    fun `google parameter support exposes top-k but hides unsupported controls`() {
        val support = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.GOOGLE,
            modelId = "gemini-2.5-flash",
        )

        assertFalse(support.supportsReasoningEffort)
        assertTrue(support.supportsTopK)
        assertTrue(support.supportsMaxTokens)
        assertFalse(support.supportsMinP)
        assertFalse(support.supportsFrequencyPenalty)
        assertFalse(support.supportsPresencePenalty)
    }

    @Test
    fun `xai multi-agent policy exposes two agent-count-backed reasoning options`() {
        val policy = ApiProviderModelPolicy.reasoningPolicy(
            provider = ApiProvider.XAI,
            modelId = "grok-4.20-multi-agent-0309",
        )

        assertEquals(ApiReasoningControlStyle.XAI_MULTI_AGENT, policy.controlStyle)
        assertEquals(listOf(ApiReasoningEffort.LOW, ApiReasoningEffort.HIGH), policy.supportedEfforts)
        assertEquals(ApiReasoningEffort.LOW, policy.defaultEffort)
    }

    @Test
    fun `standard policy exposes low through xhigh reasoning`() {
        val policy = ApiProviderModelPolicy.reasoningPolicy(
            provider = ApiProvider.OPENAI,
            modelId = "gpt-5",
        )

        assertEquals(ApiReasoningControlStyle.STANDARD, policy.controlStyle)
        assertEquals(
            listOf(
                ApiReasoningEffort.LOW,
                ApiReasoningEffort.MEDIUM,
                ApiReasoningEffort.HIGH,
                ApiReasoningEffort.XHIGH,
            ),
            policy.supportedEfforts,
        )
        assertEquals(ApiReasoningEffort.LOW, policy.defaultEffort)
    }

    @Test
    fun `xai model classification is deterministic and suffix safe`() {
        val cases = listOf(
            XaiClassificationExpectation(
                modelId = "grok-4.20-multi-agent",
                isMultiAgent = true,
                isReasoningModel = true,
                isGrok420Family = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4.20-multi-agent-0309",
                isMultiAgent = true,
                isReasoningModel = true,
                isGrok420Family = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4.20-reasoning",
                isGrok420Reasoning = true,
                isReasoningModel = true,
                isGrok420Family = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4",
                isGrok4ReasoningFamily = true,
                isReasoningModel = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4-fast-reasoning",
                isGrok4ReasoningFamily = true,
                isReasoningModel = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4-1-fast-reasoning",
                isGrok4ReasoningFamily = true,
                isReasoningModel = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4-fast-non-reasoning",
                isGrok4NonReasoningFamily = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-4-1-fast-non-reasoning",
                isGrok4NonReasoningFamily = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-3",
                isGrok3 = true,
                isReasoningModel = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-3-mini",
                isGrok3 = true,
                isGrok3Mini = true,
                isReasoningModel = true,
                isChatReasoningContentModel = true,
            ),
            XaiClassificationExpectation(
                modelId = "grok-code-fast-1",
                isChatReasoningContentModel = true,
            ),
        )

        cases.forEach { expectation ->
            assertEquals(expectation.isMultiAgent, ApiProviderModelPolicy.isXaiMultiAgentModel(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok420Reasoning, ApiProviderModelPolicy.isXaiGrok420ReasoningModel(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok4ReasoningFamily, ApiProviderModelPolicy.isXaiGrok4ReasoningFamily(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok4NonReasoningFamily, ApiProviderModelPolicy.isXaiGrok4NonReasoningFamily(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok3, ApiProviderModelPolicy.isXaiGrok3Model(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok3Mini, ApiProviderModelPolicy.isXaiGrok3MiniModel(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isReasoningModel, ApiProviderModelPolicy.isXaiReasoningModel(expectation.modelId), expectation.modelId)
            assertEquals(expectation.isGrok420Family, ApiProviderModelPolicy.isXaiGrok420Family(expectation.modelId), expectation.modelId)
            assertEquals(
                expectation.isChatReasoningContentModel,
                ApiProviderModelPolicy.isXaiChatReasoningContentModel(expectation.modelId),
                expectation.modelId,
            )
        }
    }

    @Test
    fun `xai parameter support reflects model family rules`() {
        val multiAgent = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.XAI,
            modelId = "grok-4.20-multi-agent-0309",
        )
        assertFalse(multiAgent.supportsMaxTokens)
        assertFalse(multiAgent.supportsTopK)
        assertFalse(multiAgent.supportsMinP)
        assertFalse(multiAgent.supportsFrequencyPenalty)
        assertFalse(multiAgent.supportsPresencePenalty)
        assertTrue(multiAgent.supportsReasoningEffort)

        val grok4Reasoning = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-reasoning",
        )
        assertTrue(grok4Reasoning.supportsMaxTokens)
        assertFalse(grok4Reasoning.supportsReasoningEffort)
        assertFalse(grok4Reasoning.supportsFrequencyPenalty)
        assertFalse(grok4Reasoning.supportsPresencePenalty)

        val grok3Mini = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.XAI,
            modelId = "grok-3-mini",
        )
        assertTrue(grok3Mini.supportsReasoningEffort)
        assertFalse(grok3Mini.supportsFrequencyPenalty)
        assertFalse(grok3Mini.supportsPresencePenalty)

        val grok4NonReasoning = ApiProviderModelPolicy.parameterSupport(
            provider = ApiProvider.XAI,
            modelId = "grok-4-fast-non-reasoning",
        )
        assertTrue(grok4NonReasoning.supportsReasoningEffort)
        assertTrue(grok4NonReasoning.supportsFrequencyPenalty)
        assertTrue(grok4NonReasoning.supportsPresencePenalty)
    }
}
