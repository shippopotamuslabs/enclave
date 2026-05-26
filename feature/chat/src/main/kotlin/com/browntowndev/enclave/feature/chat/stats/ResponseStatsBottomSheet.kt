package com.browntowndev.enclave.feature.chat.stats

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricPanel
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryMetricRow
import com.browntowndev.enclave.core.ui.component.neo.NeoTelemetryStatsSheet

@Composable
fun ResponseStatsBottomSheet(
    state: ResponseStatsSheetUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    NeoTelemetryStatsSheet(
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
            NeoTelemetryMetricPanel {
                state.metricRows.forEach { row ->
                    NeoTelemetryMetricRow(
                        label = row.label,
                        value = row.value,
                        testTagSuffix = row.label.lowercase().replace(' ', '_'),
                    )
                }
            }
        }
    }
}
