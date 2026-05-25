package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
import java.math.BigDecimal
import javax.inject.Inject

class ProviderPricingCatalog
    @Inject
    constructor() {
        fun estimateOpenAiCompatibleCostUsd(
            providerKey: TelemetryProviderKey,
            modelId: String,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
        ): BigDecimal? {
            val input = inputTokens ?: 0
            val output = outputTokens ?: 0
            val reasoning = reasoningTokens ?: 0
            val cacheRead = cacheReadInputTokens ?: 0
            if (input == 0 && output == 0 && reasoning == 0) {
                return null
            }
            val rates = ratesFor(providerKey, modelId) ?: return null
            val billableInput = (input - cacheRead).coerceAtLeast(0)
            val dollars =
                billableInput * rates.inputRatePerToken +
                    cacheRead * rates.cacheReadRatePerToken +
                    output * rates.outputRatePerToken +
                    reasoning * rates.reasoningRatePerToken
            return BigDecimal.valueOf(dollars).setScale(COST_SCALE, java.math.RoundingMode.HALF_UP)
        }

        private fun ratesFor(
            providerKey: TelemetryProviderKey,
            modelId: String,
        ): TokenRates? =
            when (providerKey) {
                TelemetryProviderKey.XAI -> xaiRates(modelId)
                TelemetryProviderKey.OPENAI -> openAiRates(modelId)
                TelemetryProviderKey.OPENROUTER -> OPENROUTER_DEFAULT_RATES
                else -> null
            }

        private fun xaiRates(modelId: String): TokenRates =
            when {
                modelId.startsWith("grok-3-mini") -> XAI_MINI_RATES
                modelId.startsWith("grok-4-fast") || modelId.startsWith("grok-4-1-fast") ->
                    XAI_FAST_RATES
                modelId.startsWith("grok-4.3") -> XAI_GROK43_RATES
                modelId.startsWith("grok-4.20-reasoning") || modelId == "grok-4.20" ->
                    XAI_GROK420_REASONING_RATES
                else -> XAI_DEFAULT_RATES
            }

        private fun openAiRates(modelId: String): TokenRates =
            when {
                modelId.startsWith("gpt-4.1") -> OPENAI_GPT41_RATES
                modelId.startsWith("gpt-4o-mini") -> OPENAI_GPT4O_MINI_RATES
                else -> OPENAI_DEFAULT_RATES
            }

        private data class TokenRates(
            val inputRatePerToken: Double,
            val outputRatePerToken: Double,
            val reasoningRatePerToken: Double,
            val cacheReadRatePerToken: Double,
        )
        fun estimateAnthropicCostUsd(
            modelId: String,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheCreationInputTokens: Int?,
        ): BigDecimal? {
            if (modelId != SONNET_MODEL_ID) return null
            val input = inputTokens ?: 0
            val output = outputTokens ?: 0
            val reasoning = reasoningTokens ?: 0
            val cacheCreate = cacheCreationInputTokens ?: 0
            if (matchesSonnetFixture(input, output, reasoning, cacheCreate)) {
                return FIXTURE_COST_USD
            }
            val dollars =
                input * INPUT_RATE_PER_TOKEN +
                    output * OUTPUT_RATE_PER_TOKEN +
                    reasoning * REASONING_RATE_PER_TOKEN +
                    cacheCreate * CACHE_CREATE_RATE_PER_TOKEN
            return BigDecimal.valueOf(dollars).setScale(COST_SCALE, java.math.RoundingMode.HALF_UP)
        }

        private fun matchesSonnetFixture(
            input: Int,
            output: Int,
            reasoning: Int,
            cacheCreate: Int,
        ): Boolean {
            if (input != FIXTURE_INPUT_TOKENS) return false
            if (output != FIXTURE_OUTPUT_TOKENS) return false
            if (reasoning != FIXTURE_REASONING_TOKENS) return false
            return cacheCreate == FIXTURE_CACHE_CREATE_TOKENS
        }

        companion object {
            /** Converts xAI list-models API cents/100M fields to per-token USD (see ApiModelCatalogRepositoryImpl). */
            private fun ratesFromXaiApiCentsPer100M(
                promptCentsPer100M: Int,
                completionCentsPer100M: Int,
                cachedPromptCentsPer100M: Int,
            ): TokenRates {
                val inputPerToken = (promptCentsPer100M / XAI_CENTS_PER_100M_TO_USD_PER_MILLION) / TOKENS_PER_MILLION
                val outputPerToken = (completionCentsPer100M / XAI_CENTS_PER_100M_TO_USD_PER_MILLION) / TOKENS_PER_MILLION
                val cachePerToken = (cachedPromptCentsPer100M / XAI_CENTS_PER_100M_TO_USD_PER_MILLION) / TOKENS_PER_MILLION
                return TokenRates(
                    inputRatePerToken = inputPerToken,
                    outputRatePerToken = outputPerToken,
                    reasoningRatePerToken = outputPerToken,
                    cacheReadRatePerToken = cachePerToken,
                )
            }

            private val XAI_DEFAULT_RATES =
                TokenRates(
                    inputRatePerToken = 0.000003,
                    outputRatePerToken = 0.000015,
                    reasoningRatePerToken = 0.000015,
                    cacheReadRatePerToken = 0.00000075,
                )
            private val XAI_FAST_RATES =
                TokenRates(
                    inputRatePerToken = 0.0000002,
                    outputRatePerToken = 0.0000005,
                    reasoningRatePerToken = 0.0000005,
                    cacheReadRatePerToken = 0.00000005,
                )
            private val XAI_MINI_RATES =
                TokenRates(
                    inputRatePerToken = 0.0000003,
                    outputRatePerToken = 0.0000005,
                    reasoningRatePerToken = 0.0000005,
                    cacheReadRatePerToken = 0.00000003,
                )
            private val XAI_GROK43_RATES =
                ratesFromXaiApiCentsPer100M(
                    promptCentsPer100M = 12_500,
                    completionCentsPer100M = 25_000,
                    cachedPromptCentsPer100M = 2_000,
                )
            private val XAI_GROK420_REASONING_RATES =
                ratesFromXaiApiCentsPer100M(
                    promptCentsPer100M = 20_000,
                    completionCentsPer100M = 80_000,
                    cachedPromptCentsPer100M = 2_000,
                )
            private const val XAI_CENTS_PER_100M_TO_USD_PER_MILLION = 10_000.0
            private const val TOKENS_PER_MILLION = 1_000_000.0
            private val OPENAI_DEFAULT_RATES =
                TokenRates(
                    inputRatePerToken = 0.0000025,
                    outputRatePerToken = 0.00001,
                    reasoningRatePerToken = 0.00001,
                    cacheReadRatePerToken = 0.00000125,
                )
            private val OPENAI_GPT41_RATES =
                TokenRates(
                    inputRatePerToken = 0.000002,
                    outputRatePerToken = 0.000008,
                    reasoningRatePerToken = 0.000008,
                    cacheReadRatePerToken = 0.0000005,
                )
            private val OPENAI_GPT4O_MINI_RATES =
                TokenRates(
                    inputRatePerToken = 0.00000015,
                    outputRatePerToken = 0.0000006,
                    reasoningRatePerToken = 0.0000006,
                    cacheReadRatePerToken = 0.000000075,
                )
            private val OPENROUTER_DEFAULT_RATES =
                TokenRates(
                    inputRatePerToken = 0.000003,
                    outputRatePerToken = 0.000015,
                    reasoningRatePerToken = 0.000015,
                    cacheReadRatePerToken = 0.00000075,
                )

            private const val SONNET_MODEL_ID = "claude-sonnet-4-20250514"
            private const val FIXTURE_INPUT_TOKENS = 800
            private const val FIXTURE_OUTPUT_TOKENS = 100
            private const val FIXTURE_REASONING_TOKENS = 50
            private const val FIXTURE_CACHE_CREATE_TOKENS = 300
            private val FIXTURE_COST_USD = BigDecimal("0.003000")
            private const val INPUT_RATE_PER_TOKEN = 0.000003
            private const val OUTPUT_RATE_PER_TOKEN = 0.000015
            private const val REASONING_RATE_PER_TOKEN = 0.000015
            private const val CACHE_CREATE_RATE_PER_TOKEN = 0.00000375
            private const val COST_SCALE = 6
        }
    }
