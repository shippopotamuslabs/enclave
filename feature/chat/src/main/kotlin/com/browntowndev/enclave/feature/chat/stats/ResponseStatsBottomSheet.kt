package com.browntowndev.enclave.feature.chat.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryMetricRow
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveTelemetryStatsSheet

@Composable
fun ResponseStatsBottomSheet(
    state: ResponseStatsSheetUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    EnclaveTelemetryStatsSheet(
        title = state.title,
        subtitle =
            listOfNotNull(state.providerLabel, state.modelLabel)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .takeIf { it.isNotBlank() },
        onDismiss = onDismiss,
        sheetTestTag = "response_stats_sheet",
        modifier = modifier,
        bannerMessage = state.availabilityMessage,
        bannerTestTag = "response_stats_availability",
    ) {
        Column(modifier = Modifier.testTag("response_stats_title")) {
            EnclaveTelemetryMetricPanel {
                state.metricRows.forEach { row ->
                    EnclaveTelemetryMetricRow(
                        label = row.label,
                        value = row.value,
                        testTagSuffix = row.label.lowercase().replace(' ', '_'),
                    )
                }
            }
        }
    }
}
