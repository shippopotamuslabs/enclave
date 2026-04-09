package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyApiModelMetadataDefaultsUseCaseTest {

    private val useCase = ApplyApiModelMetadataDefaultsUseCase()

    @Test
    fun `applies xai metadata defaults for context window max tokens and reasoning`() {
        val modelId = "grok-4.1-fast-reasoning"

        val result = useCase(
            provider = ApiProvider.XAI,
            modelId = modelId,
            currentReasoningEffort = null,
            currentMaxTokens = 4_096,
            currentContextWindow = 4_096,
            discoveredModel = DiscoveredApiModel(
                id = modelId,
                contextWindowTokens = 131_072,
            ),
        )

        assertEquals(
            useCase.defaultReasoningEffort(ApiProvider.XAI, modelId),
            result.reasoningEffort,
        )
        assertEquals(131_072, result.contextWindow)
        assertEquals(32_768, result.maxTokens)
    }

    @Test
    fun `preserves explicit reasoning effort when draft already chose one`() {
        val explicitEffort = useCase.defaultReasoningEffort(ApiProvider.XAI, "grok-4.1-fast-reasoning")

        val result = useCase(
            provider = ApiProvider.XAI,
            modelId = "grok-4.1-fast-reasoning",
            currentReasoningEffort = explicitEffort,
            currentMaxTokens = 2_000,
            currentContextWindow = 128_000,
            discoveredModel = null,
        )

        assertEquals(explicitEffort, result.reasoningEffort)
        assertEquals(2_000, result.maxTokens)
        assertEquals(128_000, result.contextWindow)
    }
}
