package com.browntowndev.enclave.domain.model.telemetry

import java.math.BigDecimal

data class ProviderUsageSpendRow(
    val providerLabel: String,
    val totalCostUsd: BigDecimal?,
    val turnCount: Int,
)

data class UsageSpendSummary(
    val period: UsagePeriod,
    val periodStartUtcMs: Long?,
    val periodEndUtcMs: Long?,
    val totalCostUsd: BigDecimal,
    val telemetryBackedTurnCount: Int,
    val missingCostTurnCount: Int,
    val providerBreakdown: List<ProviderUsageSpendRow>,
)
