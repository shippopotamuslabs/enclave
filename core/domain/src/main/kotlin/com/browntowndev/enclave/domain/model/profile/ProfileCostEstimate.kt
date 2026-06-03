package com.browntowndev.enclave.domain.model.profile

import java.math.BigDecimal

/**
 * Pre-run cost estimate shown to the user before they commit to the analysis.
 *
 * Tokens are rough estimates. [estimatedCostUsd] is null when the model has no stored pricing.
 */
data class ProfileCostEstimate(
    val estimatedInputTokens: Int,
    val estimatedOutputTokens: Int,
    val estimatedCostUsd: BigDecimal?,
    val selectedConversationCount: Int,
    val totalConversationCount: Int,
)
