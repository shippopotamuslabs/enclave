package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LlamaGenerationOptionsMapperTest {
    private val baseSampling =
        LlamaSamplingConfig(
            contextWindow = 4096,
            thinkingEnabled = false,
            temperature = 0.7f,
            topK = 40,
            topP = 0.9f,
            minP = 0.05f,
            maxTokens = 100,
            batchSize = 10,
            gpuLayers = 0,
            repeatPenalty = 1.0f,
        )

    private val baseConfig =
        LlamaModelConfig(
            modelPath = "test",
            systemPrompt = "test",
            sampling = baseSampling,
        )

    @Test
    fun `penaltiesForReasoningBudget returns zero penalties when thinking`() {
        val (freq, present) = LlamaGenerationOptionsMapper.penaltiesForReasoningBudget(2048)
        assertEquals(0.0f, freq)
        assertEquals(0.0f, present)
    }

    @Test
    fun `penaltiesForReasoningBudget returns default penalties when not thinking`() {
        val (freq, present) = LlamaGenerationOptionsMapper.penaltiesForReasoningBudget(0)
        assertEquals(LlamaGenerationOptionsMapper.DEFAULT_PENALTY_FREQ, freq)
        assertEquals(LlamaGenerationOptionsMapper.DEFAULT_PENALTY_PRESENT, present)
    }

    @Test
    fun `timeoutSecondsForReasoningBudget uses thinking timeout when budget positive`() {
        assertEquals(
            LlamaGenerationOptionsMapper.GENERATION_TIMEOUT_SECONDS_THINKING,
            LlamaGenerationOptionsMapper.timeoutSecondsForReasoningBudget(1),
        )
    }

    @Test
    fun `timeoutSecondsForReasoningBudget uses regular timeout when budget zero`() {
        assertEquals(
            LlamaGenerationOptionsMapper.GENERATION_TIMEOUT_SECONDS_REGULAR,
            LlamaGenerationOptionsMapper.timeoutSecondsForReasoningBudget(0),
        )
    }

    @Test
    fun `resolveFromSampling maps thinkingEnabled to reasoning budget and params`() {
        val params =
            LlamaGenerationOptionsMapper.resolveFromSampling(
                baseSampling.copy(thinkingEnabled = true),
            )

        assertEquals(LlamaGenerationOptionsMapper.THINKING_REASONING_BUDGET, params.reasoningBudget)
        assertEquals(0.0f, params.penaltyFreq)
        assertEquals(0.0f, params.penaltyPresent)
        assertEquals(LlamaGenerationOptionsMapper.GENERATION_TIMEOUT_SECONDS_THINKING, params.timeoutSeconds)
    }

    @Test
    fun `resolveFromOptions applies overrides and reasoning budget`() {
        val params =
            LlamaGenerationOptionsMapper.resolveFromOptions(
                GenerationOptions(
                    reasoningBudget = 0,
                    temperature = 0.5f,
                    topK = 10,
                    topP = 0.8f,
                    minP = 0.1f,
                    maxTokens = 50,
                ),
                baseConfig,
            )

        assertEquals(0, params.reasoningBudget)
        assertEquals(LlamaGenerationOptionsMapper.DEFAULT_PENALTY_FREQ, params.penaltyFreq)
        assertEquals(LlamaGenerationOptionsMapper.GENERATION_TIMEOUT_SECONDS_REGULAR, params.timeoutSeconds)
        assertEquals(0.5f, params.temperature)
        assertEquals(10, params.topK)
        assertEquals(0.8f, params.topP)
        assertEquals(0.1f, params.minP)
        assertEquals(50, params.maxTokens)
    }
}
