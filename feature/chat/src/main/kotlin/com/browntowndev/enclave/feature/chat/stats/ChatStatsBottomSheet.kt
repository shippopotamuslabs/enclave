package com.browntowndev.enclave.feature.chat.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveGroupDivider
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryBreakdownRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetrySectionTitle
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryStatsSheet

@Composable
fun ChatStatsBottomSheet(
    state: ChatStatsSheetUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    EnclaveTelemetryStatsSheet(
        title = state.title,
        onDismiss = onDismiss,
        sheetTestTag = "chat_stats_sheet",
        modifier = modifier,
    ) {
        Column(modifier = Modifier.testTag("chat_stats_title")) {
            EnclaveTelemetrySectionTitle(title = "Summary")
            EnclaveTelemetryMetricPanel {
                state.summaryRows.forEach { row ->
                    EnclaveTelemetryMetricRow(
                        label = row.label,
                        value = row.value,
                        testTagSuffix = row.label.lowercase().replace(' ', '_'),
                    )
                }
            }
            if (state.providerBreakdownRows.isNotEmpty()) {
                EnclaveTelemetrySectionTitle(title = "By provider")
                EnclaveTelemetryMetricPanel {
                    state.providerBreakdownRows.forEach { row ->
                        val requestSuffix = row.requestCount?.let { " · $it requests" }.orEmpty()
                        EnclaveTelemetryBreakdownRow(
                            primaryLabel = row.label,
                            costLabel = row.costLabel,
                            secondaryLabel = "${row.turnCount} turn${if (row.turnCount == 1) "" else "s"}$requestSuffix",
                            testTagSuffix = "provider_${row.label}",
                        )
                        if (row != state.providerBreakdownRows.last()) {
                            EnclaveGroupDivider()
                        }
                    }
                }
            }
            if (state.modelBreakdownRows.isNotEmpty()) {
                EnclaveTelemetrySectionTitle(title = "By model")
                EnclaveTelemetryMetricPanel {
                    state.modelBreakdownRows.forEach { row ->
                        EnclaveTelemetryBreakdownRow(
                            primaryLabel = row.label,
                            costLabel = row.costLabel,
                            secondaryLabel = "${row.outputTokens} output tokens",
                            testTagSuffix = "model_${row.label}",
                        )
                        if (row != state.modelBreakdownRows.last()) {
                            EnclaveGroupDivider()
                        }
                    }
                }
            }
        }
    }
}
