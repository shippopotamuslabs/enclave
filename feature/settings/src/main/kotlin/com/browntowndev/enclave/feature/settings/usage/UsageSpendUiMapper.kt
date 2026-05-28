package com.browntowndev.enclave.feature.settings.usage

import com.browntowndev.enclave.domain.model.telemetry.UsagePeriod
import com.browntowndev.enclave.domain.model.telemetry.UsageSpendSummary
import com.browntowndev.enclave.feature.settings.UsageSpendProviderRowUi
import com.browntowndev.enclave.feature.settings.UsageSpendSheetUiState
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

class UsageSpendUiMapper
    @Inject
    constructor() {
        fun mapUsageSpend(
            summary: UsageSpendSummary,
            isVisible: Boolean,
        ): UsageSpendSheetUiState {
            val hasProviders = summary.providerBreakdown.isNotEmpty()
            return UsageSpendSheetUiState(
                isVisible = isVisible,
                selectedPeriod = summary.period.toUi(),
                totalCostLabel = formatTotal(summary.totalCostUsd),
                providerRows =
                    summary.providerBreakdown.map { row ->
                        UsageSpendProviderRowUi(
                            providerLabel = row.providerLabel,
                            costLabel = formatProviderCost(row.totalCostUsd),
                            turnCount = row.turnCount,
                        )
                    },
                emptyMessage = if (!hasProviders && summary.telemetryBackedTurnCount == 0) "No usage recorded" else null,
                missingCostTurnsLabel =
                    if (summary.missingCostTurnCount > 0) {
                        summary.missingCostTurnCount.toString()
                    } else {
                        null
                    },
            )
        }

        fun periodLabel(period: UsagePeriod): String =
            when (period) {
                UsagePeriod.LIFETIME -> "Lifetime"
                UsagePeriod.CURRENT_MONTH -> "Current Month"
            }

        private fun UsagePeriod.toUi() =
            when (this) {
                UsagePeriod.LIFETIME -> com.browntowndev.enclave.feature.settings.UsagePeriodUi.LIFETIME
                UsagePeriod.CURRENT_MONTH -> com.browntowndev.enclave.feature.settings.UsagePeriodUi.CURRENT_MONTH
            }

        private fun formatTotal(value: BigDecimal): String = "$" + value.setScale(6, RoundingMode.HALF_UP).toPlainString()

        private fun formatProviderCost(value: BigDecimal?): String =
            if (value == null) {
                UNKNOWN
            } else {
                "$" + value.setScale(6, RoundingMode.HALF_UP).toPlainString()
            }

        companion object {
            private const val UNKNOWN = "Unknown"
        }
    }
