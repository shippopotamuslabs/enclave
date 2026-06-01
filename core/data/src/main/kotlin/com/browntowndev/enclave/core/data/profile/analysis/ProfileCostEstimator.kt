package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.profile.ProfileCostEstimate
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileCostEstimator
    @Inject
    constructor() {
        /**
         * Rough pre-run estimate. Assumes:
         * - 4 analysis passes
         * - Per-pass system prompt overhead: [SYSTEM_PROMPT_TOKENS] tokens
         * - Per-pass tool call overhead factor: [TOOL_OVERHEAD_FACTOR]
         * - Output per pass: [OUTPUT_TOKENS_PER_PASS] tokens (findings + profile)
         */
        fun estimate(
            selectedCorpusInputTokens: Int,
            selectedConversationCount: Int,
            totalConversationCount: Int,
            pricing: ApiModelSavedPricing?,
        ): ProfileCostEstimate {
            val inputPerPass = selectedCorpusInputTokens + SYSTEM_PROMPT_TOKENS
            val totalInput = (inputPerPass * TOOL_OVERHEAD_FACTOR * NUM_PASSES).toInt()
            val totalOutput = OUTPUT_TOKENS_PER_PASS * NUM_PASSES

            val promptRate = pricing?.promptUsdPerMillion
            val completionRate = pricing?.completionUsdPerMillion
            val costUsd = if (promptRate != null && completionRate != null) {
                val inputCost = totalInput * promptRate / TOKENS_PER_MILLION
                val outputCost = totalOutput * completionRate / TOKENS_PER_MILLION
                BigDecimal.valueOf(inputCost + outputCost).setScale(COST_SCALE, RoundingMode.HALF_UP)
            } else null

            return ProfileCostEstimate(
                estimatedInputTokens = totalInput,
                estimatedOutputTokens = totalOutput,
                estimatedCostUsd = costUsd,
                selectedConversationCount = selectedConversationCount,
                totalConversationCount = totalConversationCount,
            )
        }

        private companion object {
            const val NUM_PASSES = 4
            const val SYSTEM_PROMPT_TOKENS = 2_000
            const val TOOL_OVERHEAD_FACTOR = 1.4
            const val OUTPUT_TOKENS_PER_PASS = 800
            const val TOKENS_PER_MILLION = 1_000_000.0
            const val COST_SCALE = 4
        }
    }
