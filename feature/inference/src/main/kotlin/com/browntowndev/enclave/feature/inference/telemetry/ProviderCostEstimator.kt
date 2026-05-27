package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import java.math.BigDecimal
import javax.inject.Inject

data class CostEstimate(
    val costUsd: BigDecimal?,
    val costStatus: TelemetryCostStatus,
    val costSource: TelemetryCostSource,
)

class ProviderCostEstimator
    @Inject
    constructor(
        private val savedPricingEstimator: SavedApiModelPricingEstimator,
    ) {
        fun estimateAnthropic(
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheCreationInputTokens: Int?,
            cacheReadInputTokens: Int? = null,
            savedPricing: ApiModelSavedPricing? = null,
        ): CostEstimate =
            estimateFromSaved(
                savedPricing = savedPricing,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                cacheCreationInputTokens = cacheCreationInputTokens,
                inputTokensExcludeCacheReads = true,
            )

        fun estimateOpenAiCompatible(
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
            savedPricing: ApiModelSavedPricing? = null,
        ): CostEstimate =
            estimateFromSaved(
                savedPricing = savedPricing,
                inputTokens = inputTokens,
                outputTokens = outputTokens,
                reasoningTokens = reasoningTokens,
                cacheReadInputTokens = cacheReadInputTokens,
                cacheCreationInputTokens = null,
            )

        private fun estimateFromSaved(
            savedPricing: ApiModelSavedPricing?,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
            cacheCreationInputTokens: Int?,
            inputTokensExcludeCacheReads: Boolean = false,
        ): CostEstimate {
            val pricing = savedPricing?.takeIf { it.hasPromptAndCompletionRates() } ?: return unavailable()
            val cost =
                savedPricingEstimator.estimateCostUsd(
                    pricing = pricing,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheReadInputTokens = cacheReadInputTokens,
                    cacheCreationInputTokens = cacheCreationInputTokens,
                    inputTokensExcludeCacheReads = inputTokensExcludeCacheReads,
                )
            return if (cost == null) {
                unavailable()
            } else {
                CostEstimate(
                    costUsd = cost,
                    costStatus = TelemetryCostStatus.ESTIMATED,
                    costSource = TelemetryCostSource.SAVED_MODEL_PRICING,
                )
            }
        }

        private fun unavailable(): CostEstimate =
            CostEstimate(
                costUsd = null,
                costStatus = TelemetryCostStatus.UNAVAILABLE,
                costSource = TelemetryCostSource.NOT_AVAILABLE,
            )
    }
