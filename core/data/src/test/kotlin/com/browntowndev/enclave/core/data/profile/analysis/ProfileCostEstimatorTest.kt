package com.browntowndev.enclave.core.data.profile.analysis

import com.browntowndev.enclave.domain.model.config.ApiModelSavedPricing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class ProfileCostEstimatorTest {
    private val estimator = ProfileCostEstimator()

    private fun pricing(promptPerM: Double, completionPerM: Double) = ApiModelSavedPricing(
        promptUsdPerMillion = promptPerM,
        completionUsdPerMillion = completionPerM,
    )

    @Test
    fun `PP-120 cost estimate includes input and output tokens across 4 passes`() {
        val estimate = estimator.estimate(
            selectedCorpusInputTokens = 10_000,
            selectedConversationCount = 50,
            totalConversationCount = 200,
            pricing = null,
        )
        // 4 passes × (10_000 + 2_000 system) × 1.4 overhead
        val expectedInput = (10_000 + 2_000) * 1.4 * 4
        assertEquals(expectedInput.toInt(), estimate.estimatedInputTokens)
        // 4 passes × 800 output tokens
        assertEquals(4 * 800, estimate.estimatedOutputTokens)
    }

    @Test
    fun `PP-121 cost estimate with pricing returns non-null dollar amount`() {
        val estimate = estimator.estimate(
            selectedCorpusInputTokens = 10_000,
            selectedConversationCount = 50,
            totalConversationCount = 200,
            pricing = pricing(promptPerM = 3.0, completionPerM = 15.0),
        )
        assertNotNull(estimate.estimatedCostUsd)
        assert(estimate.estimatedCostUsd!! > BigDecimal.ZERO)
    }

    @Test
    fun `PP-122 cost estimate without pricing returns null cost`() {
        val estimate = estimator.estimate(
            selectedCorpusInputTokens = 10_000,
            selectedConversationCount = 50,
            totalConversationCount = 200,
            pricing = null,
        )
        assertNull(estimate.estimatedCostUsd)
    }

    @Test
    fun `PP-123 estimate preserves conversation counts`() {
        val estimate = estimator.estimate(
            selectedCorpusInputTokens = 5_000,
            selectedConversationCount = 30,
            totalConversationCount = 150,
            pricing = null,
        )
        assertEquals(30, estimate.selectedConversationCount)
        assertEquals(150, estimate.totalConversationCount)
    }
}
