package com.browntowndev.enclave.feature.inference.telemetry

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SavedApiModelPricingEstimatorTest {
    private val estimator = SavedApiModelPricingEstimator()

    @Test
    fun `returns null when prompt and completion rates missing`() {
        assertNull(
            estimator.estimateCostUsd(
                pricing = ApiModelSavedPricing(promptUsdPerMillion = 1.0),
                inputTokens = 100,
                outputTokens = 10,
                reasoningTokens = null,
                cacheReadInputTokens = null,
            ),
        )
    }

    @Test
    fun `estimates cost from saved per-million rates`() {
        val cost =
            estimator.estimateCostUsd(
                pricing =
                    ApiModelSavedPricing(
                        promptUsdPerMillion = 1.0,
                        completionUsdPerMillion = 2.0,
                        cachedPromptUsdPerMillion = 0.1,
                    ),
                inputTokens = 1_000_000,
                outputTokens = 500_000,
                reasoningTokens = 0,
                cacheReadInputTokens = 200_000,
            )

        assertNotNull(cost)
        // (800k * $1) + (200k * $0.1) + (500k * $2) = $0.8 + $0.02 + $1.0 = $1.82
        assertEquals(BigDecimal("1.820000"), cost)
    }

    @Test
    fun `Anthropic style input tokens are not reduced by cache read`() {
        val cost =
            estimator.estimateCostUsd(
                pricing =
                    ApiModelSavedPricing(
                        promptUsdPerMillion = 3.0,
                        completionUsdPerMillion = 15.0,
                        cachedPromptUsdPerMillion = 0.3,
                    ),
                inputTokens = 7_787,
                outputTokens = 1_515,
                reasoningTokens = 0,
                cacheReadInputTokens = 16_000,
                inputTokensExcludeCacheReads = true,
            )

        assertNotNull(cost)
        // (7787 * $3) + (16000 * $0.3) + (1515 * $15) per million
        assertEquals(BigDecimal("0.050886"), cost)
    }
}
