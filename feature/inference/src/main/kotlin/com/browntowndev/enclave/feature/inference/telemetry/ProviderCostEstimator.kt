package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostSource
import com.browntowndev.enclave.domain.model.telemetry.TelemetryCostStatus
import com.browntowndev.enclave.domain.model.telemetry.TelemetryProviderKey
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
        private val catalog: ProviderPricingCatalog,
    ) {
        fun estimateAnthropic(
            modelId: String,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheCreationInputTokens: Int?,
        ): CostEstimate {
            val cost =
                catalog.estimateAnthropicCostUsd(
                    modelId = modelId,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheCreationInputTokens = cacheCreationInputTokens,
                )
            return if (cost == null) {
                CostEstimate(
                    costUsd = null,
                    costStatus = TelemetryCostStatus.UNAVAILABLE,
                    costSource = TelemetryCostSource.NOT_AVAILABLE,
                )
            } else {
                CostEstimate(
                    costUsd = cost,
                    costStatus = TelemetryCostStatus.ESTIMATED,
                    costSource = TelemetryCostSource.LOCAL_PRICING_TABLE,
                )
            }
        }

        fun estimateOpenAiCompatible(
            providerKey: TelemetryProviderKey,
            modelId: String,
            inputTokens: Int?,
            outputTokens: Int?,
            reasoningTokens: Int?,
            cacheReadInputTokens: Int?,
        ): CostEstimate {
            val cost =
                catalog.estimateOpenAiCompatibleCostUsd(
                    providerKey = providerKey,
                    modelId = modelId,
                    inputTokens = inputTokens,
                    outputTokens = outputTokens,
                    reasoningTokens = reasoningTokens,
                    cacheReadInputTokens = cacheReadInputTokens,
                )
            return if (cost == null) {
                CostEstimate(
                    costUsd = null,
                    costStatus = TelemetryCostStatus.UNAVAILABLE,
                    costSource = TelemetryCostSource.NOT_AVAILABLE,
                )
            } else {
                CostEstimate(
                    costUsd = cost,
                    costStatus = TelemetryCostStatus.ESTIMATED,
                    costSource = TelemetryCostSource.LOCAL_PRICING_TABLE,
                )
            }
        }
    }
