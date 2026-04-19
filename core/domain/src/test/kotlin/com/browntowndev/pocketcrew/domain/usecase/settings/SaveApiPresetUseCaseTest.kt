package com.browntowndev.pocketcrew.domain.usecase.settings

import com.browntowndev.pocketcrew.domain.model.config.ApiCredentialsId
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfiguration
import com.browntowndev.pocketcrew.domain.model.config.ApiModelConfigurationId
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterRoutingConfiguration
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterDataCollectionPolicy
import com.browntowndev.pocketcrew.domain.model.config.OpenRouterProviderSort
import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.ApiReasoningEffort
import com.browntowndev.pocketcrew.domain.usecase.byok.SaveApiModelConfigurationUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SaveApiPresetUseCaseTest {

    private val saveApiModelConfigurationUseCase = mockk<SaveApiModelConfigurationUseCase>()
    private val saveApiPresetUseCase = SaveApiPresetUseCase(saveApiModelConfigurationUseCase)

    @Test
    fun `maps draft to configuration correctly with openrouter routing`() = runTest {
        val draft = ApiPresetDraft(
            id = ApiModelConfigurationId("preset-1"),
            credentialsId = ApiCredentialsId("cred-1"),
            displayName = "My Preset",
            maxTokens = "1000",
            contextWindow = "2000",
            temperature = 0.5,
            topP = 0.8,
            topK = "50",
            minP = 0.1,
            frequencyPenalty = 0.2,
            presencePenalty = 0.3,
            systemPrompt = "System Prompt",
            reasoningEffort = ApiReasoningEffort.HIGH,
            customHeaders = listOf("X-Custom" to "Value"),
            openRouterRouting = OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.PRICE,
                allowFallbacks = false,
                requireParameters = true,
                dataCollectionPolicy = OpenRouterDataCollectionPolicy.ALLOW,
                zeroDataRetention = true,
            )
        )

        val configSlot = slot<ApiModelConfiguration>()
        coEvery { saveApiModelConfigurationUseCase(capture(configSlot)) } returns Result.success(draft.id)

        val result = saveApiPresetUseCase(
            provider = ApiProvider.OPENROUTER,
            parentCredentialsId = ApiCredentialsId("parent-cred-1"),
            defaultReasoningEffort = null,
            draft = draft
        ).getOrThrow()

        assertEquals(draft.id, result)
        val config = configSlot.captured
        assertEquals(draft.id, config.id)
        assertEquals(draft.credentialsId, config.apiCredentialsId)
        assertEquals(draft.displayName, config.displayName)
        assertEquals(1000, config.maxTokens)
        assertEquals(2000, config.contextWindow)
        assertEquals(draft.temperature, config.temperature)
        assertEquals(draft.topP, config.topP)
        assertEquals(50, config.topK)
        assertEquals(draft.minP, config.minP)
        assertEquals(draft.frequencyPenalty, config.frequencyPenalty)
        assertEquals(draft.presencePenalty, config.presencePenalty)
        assertEquals(draft.systemPrompt, config.systemPrompt)
        assertEquals(draft.reasoningEffort, config.reasoningEffort)
        assertEquals(mapOf("X-Custom" to "Value"), config.customHeaders)
        assertEquals(draft.openRouterRouting, config.openRouterRouting)
    }

    @Test
    fun `applies default values for invalid or empty fields`() = runTest {
        val parentCredentialsId = ApiCredentialsId("parent-cred")
        val draft = ApiPresetDraft(
            id = ApiModelConfigurationId("preset-1"),
            credentialsId = ApiCredentialsId(""), // Empty credentialsId
            displayName = "Default Test",
            maxTokens = "invalid", // Should default to 4096
            contextWindow = "invalid", // Should default to 4096
            temperature = 0.7,
            topP = 0.95,
            topK = "invalid", // Should default to 40
            minP = 0.0,
            frequencyPenalty = 0.0,
            presencePenalty = 0.0,
            systemPrompt = "",
            reasoningEffort = null, // Should fallback to defaultReasoningEffort
            customHeaders = emptyList(),
            openRouterRouting = OpenRouterRoutingConfiguration()
        )

        val defaultReasoningEffort = ApiReasoningEffort.MEDIUM

        val configSlot = slot<ApiModelConfiguration>()
        coEvery { saveApiModelConfigurationUseCase(capture(configSlot)) } returns Result.success(draft.id)

        saveApiPresetUseCase(
            provider = ApiProvider.OPENAI,
            parentCredentialsId = parentCredentialsId,
            defaultReasoningEffort = defaultReasoningEffort,
            draft = draft
        ).getOrThrow()

        val config = configSlot.captured
        assertEquals(parentCredentialsId, config.apiCredentialsId)
        assertEquals(4096, config.maxTokens)
        assertEquals(4096, config.contextWindow)
        assertEquals(40, config.topK)
        assertEquals(defaultReasoningEffort, config.reasoningEffort)
    }

    @Test
    fun `ignores openrouter routing config for non-openrouter providers`() = runTest {
        val draft = ApiPresetDraft(
            openRouterRouting = OpenRouterRoutingConfiguration(
                providerSort = OpenRouterProviderSort.PRICE, // Custom value
                allowFallbacks = false
            )
        )

        val configSlot = slot<ApiModelConfiguration>()
        coEvery { saveApiModelConfigurationUseCase(capture(configSlot)) } returns Result.success(draft.id)

        saveApiPresetUseCase(
            provider = ApiProvider.ANTHROPIC, // Not OpenRouter
            parentCredentialsId = ApiCredentialsId("cred"),
            defaultReasoningEffort = null,
            draft = draft
        )

        val config = configSlot.captured
        assertEquals(OpenRouterRoutingConfiguration(), config.openRouterRouting) // Should be default
    }

    @Test
    fun `filters out invalid custom headers`() = runTest {
        val draft = ApiPresetDraft(
            customHeaders = listOf(
                "Valid" to "Header",
                "" to "Missing Key",
                "   " to "Blank Key",
                "Missing Value" to "",
                "Blank Value" to "  ",
                "Valid2" to "Header2"
            )
        )

        val configSlot = slot<ApiModelConfiguration>()
        coEvery { saveApiModelConfigurationUseCase(capture(configSlot)) } returns Result.success(draft.id)

        saveApiPresetUseCase(
            provider = ApiProvider.OPENAI,
            parentCredentialsId = ApiCredentialsId("cred"),
            defaultReasoningEffort = null,
            draft = draft
        )

        val config = configSlot.captured
        assertEquals(
            mapOf("Valid" to "Header", "Valid2" to "Header2"),
            config.customHeaders
        )
    }
}
