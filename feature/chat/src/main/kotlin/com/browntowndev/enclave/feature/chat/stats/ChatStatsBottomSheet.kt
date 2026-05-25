package com.browntowndev.enclave.feature.chat.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.browntowndev.enclave.core.ui.component.neo.NeoGroupDivider
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryBreakdownRow
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricRow
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetrySectionTitle
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryStatsSheet

@Composable
fun ChatStatsBottomSheet(
    state: ChatStatsSheetUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    NeoTelemetryStatsSheet(
        title = state.title,
        onDismiss = onDismiss,
        sheetTestTag = "chat_stats_sheet",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.testTag("chat_stats_title")) {
            NeoTelemetrySectionTitle(title = "Summary")
            NeoTelemetryMetricPanel {
                state.summaryRows.forEach { row ->
                    NeoTelemetryMetricRow(
                        label = row.label,
                        value = row.value,
                        testTagSuffix = row.label.lowercase().replace(' ', '_'),
                    )
                }
            }
            if (state.providerBreakdownRows.isNotEmpty()) {
                NeoTelemetrySectionTitle(title = "By provider")
                NeoTelemetryMetricPanel {
                    state.providerBreakdownRows.forEach { row ->
                        val requestSuffix = row.requestCount?.let { " · $it requests" }.orEmpty()
                        NeoTelemetryBreakdownRow(
                            primaryLabel = row.label,
                            costLabel = row.costLabel,
                            secondaryLabel = "${row.turnCount} turn${if (row.turnCount == 1) "" else "s"}$requestSuffix",
                            testTagSuffix = "provider_${row.label}",
                        )
                        if (row != state.providerBreakdownRows.last()) {
                            NeoGroupDivider()
                        }
                    }
                }
            }
            if (state.modelBreakdownRows.isNotEmpty()) {
                NeoTelemetrySectionTitle(title = "By model")
                NeoTelemetryMetricPanel {
                    state.modelBreakdownRows.forEach { row ->
                        NeoTelemetryBreakdownRow(
                            primaryLabel = row.label,
                            costLabel = row.costLabel,
                            secondaryLabel = "${row.outputTokens} output tokens",
                            testTagSuffix = "model_${row.label}",
                        )
                        if (row != state.modelBreakdownRows.last()) {
                            NeoGroupDivider()
                        }
                    }
                }
            }
        }
    }
}
