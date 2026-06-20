package com.browntowndev.enclave.domain.model.inference

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
    fun `supports model discovery for openai anthropic google openrouter xai fal and zai`() {
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.OPENAI))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.ANTHROPIC))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.GOOGLE))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.OPENROUTER))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.XAI))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.FAL))
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.ZAI))
    }

    @Test
    fun `fal tts and music model candidates exclude cross-capability ids`() {
        assertTrue(ApiProviderModelPolicy.isFalTtsModelCandidate("fal-ai/minimax/speech-02-hd"))
        assertFalse(ApiProviderModelPolicy.isFalTtsModelCandidate("fal-ai/elevenlabs/tts/turbo-v2.5"))
        assertFalse(ApiProviderModelPolicy.isFalTtsModelCandidate("fal-ai/stable-audio"))
        assertTrue(ApiProviderModelPolicy.isFalMusicModelCandidate("fal-ai/stable-audio"))
        assertFalse(ApiProviderModelPolicy.isFalMusicModelCandidate("fal-ai/minimax/speech-02-hd"))
    }

    @Test
    fun `provider defaults include hosted endpoints for google openrouter xai fal and zai`() {
        assertEquals("https://generativelanguage.googleapis.com", ApiProvider.GOOGLE.defaultBaseUrl())
        assertEquals("https://openrouter.ai/api/v1", ApiProvider.OPENROUTER.defaultBaseUrl())
        assertEquals("https://api.x.ai/v1", ApiProvider.XAI.defaultBaseUrl())
        assertEquals("https://queue.fal.run", ApiProvider.FAL.defaultBaseUrl())
        assertEquals("https://api.z.ai/api/paas/v4", ApiProvider.ZAI.defaultBaseUrl())
        assertEquals(null, ApiProvider.OPENAI.defaultBaseUrl())
        assertEquals(null, ApiProvider.ANTHROPIC.defaultBaseUrl())
    }

    @Test
    fun `fal is media-only and not chat selectable`() {
        assertFalse(ApiProvider.FAL.supportsChat)
        assertTrue(ApiProvider.OPENAI.supportsChat)
    }

    @Test
    fun `supportsAnthropicPromptCaching matches documented Claude families`() {
        assertTrue(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-sonnet-4-20250514"))
        assertTrue(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-3-5-sonnet-20241022"))
        assertTrue(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-3-7-sonnet-20250219"))
        assertTrue(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-haiku-4-5-20251001"))
        assertTrue(ApiProviderModelPolicy.supportsAnthropicPromptCaching("anthropic/claude-opus-4-20250514"))
        assertFalse(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-2.1"))
        assertFalse(ApiProviderModelPolicy.supportsAnthropicPromptCaching("claude-instant-1.2"))
        assertFalse(ApiProviderModelPolicy.supportsAnthropicPromptCaching("gpt-4o"))
        // Z.ai GLM models use implicit provider caching via OpenAI usage fields, not explicit
        // Anthropic cache_control breakpoints — so they must return false here.
        assertFalse(ApiProviderModelPolicy.supportsAnthropicPromptCaching("glm-4.6"))
        assertFalse(ApiProviderModelPolicy.supportsAnthropicPromptCaching("glm-4.5-air"))
    }

    @Test
    fun `anthropic parameter support keeps the standard preset controls but hides unsupported sliders`() {
        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.ANTHROPIC,
                modelId = "claude-sonnet-4-20250514",
            )

        assertTrue(support.supportsReasoningEffort)
        assertTrue(support.supportsMaxTokens)
        assertFalse(support.supportsTemperature)
        assertFalse(support.supportsTopP)
        assertFalse(support.supportsTopK)
        assertFalse(support.supportsMinP)
        assertFalse(support.supportsFrequencyPenalty)
        assertFalse(support.supportsPresencePenalty)
    }

    @Test
    fun `zai exposes temperature and top_p but not other openai-style sampling controls`() {
        // Z.ai speaks the OpenAI Chat Completions protocol with OpenAI-style sampling:
        // temperature and top_p are supported core parameters (GLM-4.6 defaults temp=1.0, top_p=0.95).
        // It does NOT expose top_k, min_p, frequency_penalty, or presence_penalty.
        // See https://docs.z.ai/guides/overview/concept-param.
        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.ZAI,
                modelId = "glm-4.6",
            )

        assertTrue(support.supportsReasoningEffort)
        assertTrue(support.supportsMaxTokens)
        assertTrue(support.supportsTemperature)
        assertTrue(support.supportsTopP)
        assertFalse(support.supportsTopK)
        assertFalse(support.supportsMinP)
        assertFalse(support.supportsFrequencyPenalty)
        assertFalse(support.supportsPresencePenalty)
    }

    @Test
    fun `google parameter support exposes top-k but hides unsupported controls`() {
        val support =
            ApiProviderModelPolicy.parameterSupport(
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
    fun `xai multi-agent policy exposes documented agent-count-backed reasoning options`() {
        val policy =
            ApiProviderModelPolicy.reasoningPolicy(
                provider = ApiProvider.XAI,
                modelId = "grok-4.20-multi-agent-0309",
            )

        assertEquals(ApiReasoningControlStyle.XAI_MULTI_AGENT, policy.controlStyle)
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
    fun `openai nano models are treated as reasoning models for sampling restrictions`() {
        assertTrue(ApiProviderModelPolicy.isOpenAiReasoningModel("gpt-5.4-nano"))
        assertTrue(ApiProviderModelPolicy.isOpenAiReasoningModel("openai/gpt-5.4-nano"))

        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.OPENAI,
                modelId = "gpt-5.4-nano",
            )

        assertFalse(support.supportsTemperature)
        assertFalse(support.supportsTopP)
    }

    @Test
    fun `standard policy exposes low through xhigh reasoning`() {
        val policy =
            ApiProviderModelPolicy.reasoningPolicy(
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
        val cases =
            listOf(
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
            assertEquals(
                expectation.isGrok420Reasoning,
                ApiProviderModelPolicy.isXaiGrok420ReasoningModel(expectation.modelId),
                expectation.modelId,
            )
            assertEquals(
                expectation.isGrok4ReasoningFamily,
                ApiProviderModelPolicy.isXaiGrok4ReasoningFamily(expectation.modelId),
                expectation.modelId,
            )
            assertEquals(
                expectation.isGrok4NonReasoningFamily,
                ApiProviderModelPolicy.isXaiGrok4NonReasoningFamily(expectation.modelId),
                expectation.modelId,
            )
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
    fun `SCN-POLICY-001 deepseek-reasoner classified as reasoner model`() {
        val modelId = "deepseek-reasoner"
        assertTrue(ApiProviderModelPolicy.isDeepSeekReasonerModel(modelId))
        assertFalse(ApiProviderModelPolicy.supportsDeepSeekClientTools(modelId))
        assertTrue(ApiProviderModelPolicy.shouldStripReasoningContentOnAssistantReplay(modelId))
    }

    @Test
    fun `SCN-POLICY-002 deepseek-chat classified as chat model`() {
        val modelId = "deepseek-chat"
        assertTrue(ApiProviderModelPolicy.isDeepSeekChatModel(modelId))
        assertTrue(ApiProviderModelPolicy.supportsDeepSeekClientTools(modelId))
        assertFalse(ApiProviderModelPolicy.shouldStripReasoningContentOnAssistantReplay(modelId))
    }

    @Test
    fun `SCN-POLICY-003 deepseek-v4-pro classified as V4 family`() {
        val modelId = "deepseek-v4-pro"
        assertTrue(ApiProviderModelPolicy.isDeepSeekV4FamilyModel(modelId))
        assertTrue(ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget = 2048))
        assertFalse(ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget = 0))
    }

    @Test
    fun `SCN-POLICY-004 reasoner parameter support hides temperature and top_p`() {
        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.DEEPSEEK,
                modelId = "deepseek-reasoner",
            )
        assertFalse(support.supportsTemperature)
        assertFalse(support.supportsTopP)
        assertFalse(support.supportsReasoningEffort)
        assertTrue(support.supportsMaxTokens)
    }

    @Test
    fun `SCN-POLICY-005 chat parameter support exposes temperature and top_p`() {
        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.DEEPSEEK,
                modelId = "deepseek-chat",
            )
        assertTrue(support.supportsTemperature)
        assertTrue(support.supportsTopP)
        assertTrue(support.supportsMaxTokens)
    }

    @Test
    fun `SCN-POLICY-006 V4 reasoning engaged parameter support hides temperature and top_p`() {
        val support =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.DEEPSEEK,
                modelId = "deepseek-v4-pro",
                reasoningBudget = 2048,
            )
        assertFalse(support.supportsTemperature)
        assertFalse(support.supportsTopP)
        assertTrue(support.supportsMaxTokens)
    }

    @Test
    fun `SCN-POLICY-007 model discovery enabled for DEEPSEEK`() {
        assertTrue(ApiProviderModelPolicy.supportsModelDiscovery(ApiProvider.DEEPSEEK))
    }

    @Test
    fun `SCN-POLICY-008 deepseek reasoning effort maps LOW to high and XHIGH to max`() {
        val modelId = "deepseek-v4-pro"
        assertEquals("high", ApiProviderModelPolicy.deepSeekReasoningEffortForApi(ApiReasoningEffort.LOW))
        assertEquals("max", ApiProviderModelPolicy.deepSeekReasoningEffortForApi(ApiReasoningEffort.XHIGH))
        assertTrue(ApiProviderModelPolicy.isDeepSeekDirectReasoningEngaged(modelId, reasoningBudget = 1024))
    }

    @Test
    fun `SCN-INFER-EDGE-001 openrouter prefixed model ids normalize without false reasoner match`() {
        val modelId = "openrouter/deepseek/deepseek-r1"
        assertFalse(ApiProviderModelPolicy.isDeepSeekReasonerModel(modelId))
    }

    @Test
    fun `xai parameter support reflects model family rules`() {
        val multiAgent =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4.20-multi-agent-0309",
            )
        assertFalse(multiAgent.supportsMaxTokens)
        assertFalse(multiAgent.supportsTopK)
        assertFalse(multiAgent.supportsMinP)
        assertFalse(multiAgent.supportsFrequencyPenalty)
        assertFalse(multiAgent.supportsPresencePenalty)
        assertTrue(multiAgent.supportsReasoningEffort)

        val grok420Reasoning =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4.20-reasoning",
            )
        assertFalse(grok420Reasoning.supportsReasoningEffort)

        val grok420 =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4.20",
            )
        assertFalse(grok420.supportsReasoningEffort)

        val grok41Fast =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4-1-fast",
            )
        assertFalse(grok41Fast.supportsReasoningEffort)

        val grok4Reasoning =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-reasoning",
            )
        assertTrue(grok4Reasoning.supportsMaxTokens)
        assertFalse(grok4Reasoning.supportsReasoningEffort)
        assertFalse(grok4Reasoning.supportsFrequencyPenalty)
        assertFalse(grok4Reasoning.supportsPresencePenalty)

        val grok3Mini =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-3-mini",
            )
        assertTrue(grok3Mini.supportsReasoningEffort)
        assertFalse(grok3Mini.supportsFrequencyPenalty)
        assertFalse(grok3Mini.supportsPresencePenalty)

        val grok4NonReasoning =
            ApiProviderModelPolicy.parameterSupport(
                provider = ApiProvider.XAI,
                modelId = "grok-4-fast-non-reasoning",
            )
        assertFalse(grok4NonReasoning.supportsReasoningEffort)
        assertTrue(grok4NonReasoning.supportsFrequencyPenalty)
        assertTrue(grok4NonReasoning.supportsPresencePenalty)
    }
}
