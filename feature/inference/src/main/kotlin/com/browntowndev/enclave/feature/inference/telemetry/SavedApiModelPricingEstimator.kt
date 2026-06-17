package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import java.math.BigDecimal
import javax.inject.Inject

class SavedApiModelPricingEstimator
    @Inject
    constructor() {
        fun estimateCostUsd(
            pricing: ApiModelSavedPricing,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
            cacheCreationInputTokens: Int? = null,
            /** When true, [inputTokens] is non-cached prompt only (Anthropic). When false, it includes cache reads (OpenAI-compatible). */
            inputTokensExcludeCacheReads: Boolean = false,
        ): BigDecimal? {
            if (!pricing.hasPromptAndCompletionRates()) return null
            val prompt = pricing.promptUsdPerMillion!!
            val completion = pricing.completionUsdPerMillion!!
            val inputRatePerToken = prompt / TOKENS_PER_MILLION
            val outputRatePerToken = completion / TOKENS_PER_MILLION
            val cacheReadRatePerToken = (pricing.cachedPromptUsdPerMillion ?: prompt) / TOKENS_PER_MILLION

            val input = inputTokens ?: 0
            val output = outputTokens ?: 0
            val reasoning = reasoningTokens ?: 0
            val cacheRead = cacheReadInputTokens ?: 0
            val cacheCreate = cacheCreationInputTokens ?: 0
            if (hasNoBillableTokens(input, output, reasoning, cacheRead, cacheCreate)) {
                return null
            }
            val billableInput =
                if (inputTokensExcludeCacheReads) {
                    input
                } else {
                    (input - cacheRead).coerceAtLeast(0)
                }
            // completion_tokens already includes reasoning tokens on OpenAI-compatible providers.
            val dollars =
                billableInput * inputRatePerToken +
                    cacheRead * cacheReadRatePerToken +
                    output * outputRatePerToken +
                    cacheCreate * inputRatePerToken * ANTHROPIC_CACHE_WRITE_MULTIPLIER
            return BigDecimal.valueOf(dollars).setScale(COST_SCALE, java.math.RoundingMode.HALF_UP)
        }

        private fun hasNoBillableTokens(vararg tokenCounts: Int): Boolean = tokenCounts.all { it == 0 }

        private companion object {
            private const val TOKENS_PER_MILLION = 1_000_000.0
            private const val COST_SCALE = 6
            /** Anthropic cache creation is billed at 1.25× the input token rate. */
            private const val ANTHROPIC_CACHE_WRITE_MULTIPLIER = 1.25
        }
    }
