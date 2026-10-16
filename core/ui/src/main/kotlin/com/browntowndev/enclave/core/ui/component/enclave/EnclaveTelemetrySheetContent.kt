package com.browntowndev.enclave.core.ui.component.enclave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclaveTelemetryStatsSheet(
    title: String,
    onDismiss: () -> Unit,
    sheetTestTag: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    bannerMessage: String? = null,
    bannerTestTag: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    EnclaveModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag(sheetTestTag),
        ) {
            EnclaveSheetHeader(
                title = title,
                subtitle = subtitle,
                onDismiss = onDismiss,
            )
            bannerMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .padding(bottom = EnclaveNeoTokens.rowSpacing)
                            .then(
                                bannerTestTag?.let { Modifier.testTag(it) } ?: Modifier,
                            ),
                )
            }
            content()
        }
    }
}

@Composable
fun EnclaveTelemetrySectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .padding(top = 4.dp, bottom = 8.dp)
                .then(testTag?.let { Modifier.testTag(it) } ?: Modifier),
    )
}

@Composable
fun EnclaveTelemetryMetricPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    EnclavePanel(modifier = modifier.padding(bottom = EnclaveNeoTokens.rowSpacing)) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun EnclaveTelemetryMetricRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    testTagSuffix: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier =
                Modifier
                    .weight(1f)
                    .then(
                        testTagSuffix?.let {
                            Modifier.testTag("telemetry_metric_$it")
                        } ?: Modifier,
                    ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EnclaveTelemetryBreakdownRow(
    primaryLabel: String,
    costLabel: String,
    secondaryLabel: String,
    modifier: Modifier = Modifier,
    testTagSuffix: String? = null,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .then(
                    testTagSuffix?.let { Modifier.testTag("telemetry_breakdown_$it") }
                        ?: Modifier,
                ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = primaryLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = costLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
        Text(
            text = secondaryLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
