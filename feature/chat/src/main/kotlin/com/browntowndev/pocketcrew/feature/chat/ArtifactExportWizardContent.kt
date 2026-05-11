package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ArtifactExportWizardContent(
    state: ArtifactExportSheetState.Visible,
    onDismiss: () -> Unit,
    onSelectFormat: (WizardExportFormat) -> Unit,
    onSelectDestination: (WizardExportDestination) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onConfirmExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.artifact_export_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )
            IconButton(
                onClick = onDismiss,
                enabled = !state.isExporting,
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.artifact_export_close),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        when (state.step) {
            ArtifactExportWizardStep.CHOOSE_FORMAT -> {
                Text(
                    text = stringResource(R.string.artifact_export_step_format),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedFormat == WizardExportFormat.PDF,
                        onClick = { onSelectFormat(WizardExportFormat.PDF) },
                        label = { Text(stringResource(R.string.artifact_export_format_pdf)) },
                        enabled = !state.isExporting,
                    )
                    FilterChip(
                        selected = state.selectedFormat == WizardExportFormat.WORD,
                        onClick = { onSelectFormat(WizardExportFormat.WORD) },
                        label = { Text(stringResource(R.string.artifact_export_format_word)) },
                        enabled = !state.isExporting,
                    )
                    FilterChip(
                        selected = state.selectedFormat == WizardExportFormat.BOTH,
                        onClick = { onSelectFormat(WizardExportFormat.BOTH) },
                        label = { Text(stringResource(R.string.artifact_export_format_both)) },
                        enabled = !state.isExporting,
                    )
                }
            }
            ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
                Text(
                    text = stringResource(R.string.artifact_export_step_destination),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedDestination == WizardExportDestination.SHARE,
                        onClick = { onSelectDestination(WizardExportDestination.SHARE) },
                        label = { Text(stringResource(R.string.artifact_export_dest_share)) },
                        enabled = !state.isExporting,
                    )
                    FilterChip(
                        selected = state.selectedDestination == WizardExportDestination.DOWNLOAD,
                        onClick = { onSelectDestination(WizardExportDestination.DOWNLOAD) },
                        label = { Text(stringResource(R.string.artifact_export_dest_download)) },
                        enabled = !state.isExporting,
                    )
                }
            }
        }
        if (state.isExporting) {
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.artifact_export_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onBack,
                enabled = !state.isExporting,
            ) {
                Text(stringResource(R.string.artifact_export_back))
            }
            when (state.step) {
                ArtifactExportWizardStep.CHOOSE_FORMAT -> {
                    Button(
                        onClick = onNext,
                        enabled = !state.isExporting && state.selectedFormat != null,
                    ) {
                        Text(stringResource(R.string.artifact_export_continue))
                    }
                }
                ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
                    Button(
                        onClick = onConfirmExport,
                        enabled =
                            !state.isExporting &&
                                state.selectedDestination != null,
                    ) {
                        Text(stringResource(R.string.artifact_export_action))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ArtifactExportWizardContentPreview() {
    PocketCrewTheme {
        ArtifactExportWizardContent(
            state =
                ArtifactExportSheetState.Visible(
                    messageId = MessageId("m1"),
                    artifactId = "a1",
                    title = "Quarterly summary",
                    step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                    selectedFormat = WizardExportFormat.PDF,
                    selectedDestination = null,
                    isExporting = false,
                ),
            onDismiss = {},
            onSelectFormat = {},
            onSelectDestination = {},
            onBack = {},
            onNext = {},
            onConfirmExport = {},
        )
    }
}
