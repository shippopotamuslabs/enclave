package com.browntowndev.enclave.feature.inference.llama

import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.LlamaModelConfig
import com.browntowndev.enclave.domain.model.inference.LlamaSamplingConfig

/**
 * Maps domain generation options and model sampling config to llama-jni completion parameters.
 */
internal object LlamaGenerationOptionsMapper {
    const val GENERATION_TIMEOUT_SECONDS_THINKING = 1800L
    const val GENERATION_TIMEOUT_SECONDS_REGULAR = 900L

    const val DEFAULT_PENALTY_FREQ = 0.05f
    const val DEFAULT_PENALTY_PRESENT = 0.05f

    const val THINKING_REASONING_BUDGET = 2048

    data class ResolvedCompletionParams(
        val reasoningBudget: Int,
        val penaltyFreq: Float,
        val penaltyPresent: Float,
        val timeoutSeconds: Long,
        val temperature: Float,
        val topK: Int,
        val topP: Float,
        val minP: Float,
        val maxTokens: Int,
    )

    fun resolveFromSampling(sampling: LlamaSamplingConfig): ResolvedCompletionParams {
        val reasoningBudget = if (sampling.thinkingEnabled) THINKING_REASONING_BUDGET else 0
        return ResolvedCompletionParams(
            reasoningBudget = reasoningBudget,
            penaltyFreq = penaltiesForReasoningBudget(reasoningBudget).first,
            penaltyPresent = penaltiesForReasoningBudget(reasoningBudget).second,
            timeoutSeconds = timeoutSecondsForReasoningBudget(reasoningBudget),
            temperature = sampling.temperature,
            topK = sampling.topK,
            topP = sampling.topP,
            minP = sampling.minP,
            maxTokens = sampling.maxTokens,
        )
    }

    fun resolveFromOptions(
        options: GenerationOptions,
        config: LlamaModelConfig,
    ): ResolvedCompletionParams {
        val reasoningBudget = options.reasoningBudget
        val sampling = config.sampling
        return ResolvedCompletionParams(
            reasoningBudget = reasoningBudget,
            penaltyFreq = penaltiesForReasoningBudget(reasoningBudget).first,
            penaltyPresent = penaltiesForReasoningBudget(reasoningBudget).second,
            timeoutSeconds = timeoutSecondsForReasoningBudget(reasoningBudget),
            temperature = options.temperature ?: sampling.temperature,
            topK = options.topK ?: sampling.topK,
            topP = options.topP ?: sampling.topP,
            minP = options.minP ?: sampling.minP,
            maxTokens = options.maxTokens ?: sampling.maxTokens,
        )
    }

    fun penaltiesForReasoningBudget(reasoningBudget: Int): Pair<Float, Float> =
        if (reasoningBudget > 0) {
            0.0f to 0.0f
        } else {
            DEFAULT_PENALTY_FREQ to DEFAULT_PENALTY_PRESENT
        }

    fun timeoutSecondsForReasoningBudget(reasoningBudget: Int): Long =
        if (reasoningBudget > 0) {
            GENERATION_TIMEOUT_SECONDS_THINKING
        } else {
            GENERATION_TIMEOUT_SECONDS_REGULAR
        }
}
