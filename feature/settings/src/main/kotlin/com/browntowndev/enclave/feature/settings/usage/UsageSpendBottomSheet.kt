package com.browntowndev.enclave.feature.settings.usage

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.neo.NeoGroupDivider
import com.browntowndev.enclave.core.ui.component.neo.NeoSegmentedControl
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryBreakdownRow
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricRow
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetrySectionTitle
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryStatsSheet
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
import com.browntowndev.enclave.feature.settings.UsagePeriodUi
import com.browntowndev.enclave.feature.settings.UsageSpendSheetUiState

@Composable
fun UsageSpendBottomSheet(
    state: UsageSpendSheetUiState,
    onDismiss: () -> Unit,
    onPeriodChange: (UsagePeriodUi) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    val periodOptions = listOf("Lifetime", "Current month")
    val selectedPeriodIndex =
        when (state.selectedPeriod) {
            UsagePeriodUi.LIFETIME -> 0
            UsagePeriodUi.CURRENT_MONTH -> 1
        }
    NeoTelemetryStatsSheet(
        title = "Usage & spend",
        subtitle = "API inference costs across providers",
        onDismiss = onDismiss,
        sheetTestTag = "usage_spend_sheet",
        modifier = modifier,
    ) {
        NeoSegmentedControl(
            options = periodOptions,
            selectedIndex = selectedPeriodIndex,
            onOptionSelected = { index ->
                onPeriodChange(
                    when (index) {
                        0 -> UsagePeriodUi.LIFETIME
                        else -> UsagePeriodUi.CURRENT_MONTH
                    },
                )
            },
            modifier =
                Modifier
                    .padding(bottom = EnclaveNeoTokens.rowSpacing)
                    .testTag("usage_spend_period_control"),
        )
        Text(
            text =
                when (state.selectedPeriod) {
                    UsagePeriodUi.LIFETIME -> "Lifetime"
                    UsagePeriodUi.CURRENT_MONTH -> "Current Month"
                },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("usage_spend_period"),
        )
        NeoTelemetryMetricPanel(
            modifier =
                Modifier
                    .padding(top = EnclaveNeoTokens.rowSpacing)
                    .testTag("usage_spend_total"),
        ) {
            NeoTelemetryMetricRow(
                label = "Total spend",
                value = state.totalCostLabel,
                testTagSuffix = "total",
            )
        }
        state.emptyMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier =
                    Modifier
                        .padding(top = EnclaveNeoTokens.rowSpacing)
                        .testTag("usage_spend_empty"),
            )
        }
        if (state.providerRows.isNotEmpty()) {
            NeoTelemetrySectionTitle(title = "By provider")
            NeoTelemetryMetricPanel {
                state.providerRows.forEach { row ->
                    NeoTelemetryBreakdownRow(
                        primaryLabel = row.providerLabel,
                        costLabel = row.costLabel,
                        secondaryLabel = "${row.turnCount} turn${if (row.turnCount == 1) "" else "s"}",
                        testTagSuffix = row.providerLabel,
                    )
                    if (row != state.providerRows.last()) {
                        NeoGroupDivider()
                    }
                }
            }
        }
        state.missingCostTurnsLabel?.let { label ->
            NeoTelemetryMetricPanel(modifier = Modifier.padding(top = EnclaveNeoTokens.rowSpacing)) {
                NeoTelemetryMetricRow(
                    label = "Turns without cost",
                    value = label,
                    testTagSuffix = "missing_cost",
                )
            }
        }
    }
}
