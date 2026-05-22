package com.browntowndev.enclave.feature.chat.artifactexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ArtifactExportWizardStep
import com.browntowndev.enclave.feature.chat.R
import com.browntowndev.enclave.feature.chat.WizardExportDestination
import com.browntowndev.enclave.feature.chat.WizardExportFormat

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.artifact_export_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
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

        Spacer(modifier = Modifier.height(20.dp))

        when (state.step) {
            ArtifactExportWizardStep.CHOOSE_FORMAT -> {
                WizardSectionHeader(text = stringResource(R.string.artifact_export_step_format))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardSelectableCard(
                        selected = state.selectedFormat == WizardExportFormat.PDF,
                        onClick = { onSelectFormat(WizardExportFormat.PDF) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.PictureAsPdf,
                        title = stringResource(R.string.artifact_export_format_pdf),
                        subtitle = stringResource(R.string.artifact_export_format_pdf_subtitle),
                    )
                    WizardSelectableCard(
                        selected = state.selectedFormat == WizardExportFormat.WORD,
                        onClick = { onSelectFormat(WizardExportFormat.WORD) },
                        enabled = !state.isExporting,
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = stringResource(R.string.artifact_export_format_word),
                        subtitle = stringResource(R.string.artifact_export_format_word_subtitle),
                    )
                    WizardSelectableCard(
                        selected = state.selectedFormat == WizardExportFormat.BOTH,
                        onClick = { onSelectFormat(WizardExportFormat.BOTH) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.Layers,
                        title = stringResource(R.string.artifact_export_format_both),
                        subtitle = stringResource(R.string.artifact_export_format_both_subtitle),
                    )
                }
            }
            ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
                WizardSectionHeader(text = stringResource(R.string.artifact_export_step_destination))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardSelectableCard(
                        selected = state.selectedDestination == WizardExportDestination.SHARE,
                        onClick = { onSelectDestination(WizardExportDestination.SHARE) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.artifact_export_dest_share),
                        subtitle = stringResource(R.string.artifact_export_dest_share_subtitle),
                    )
                    WizardSelectableCard(
                        selected = state.selectedDestination == WizardExportDestination.DOWNLOAD,
                        onClick = { onSelectDestination(WizardExportDestination.DOWNLOAD) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.artifact_export_dest_download),
                        subtitle = stringResource(R.string.artifact_export_dest_download_subtitle),
                    )
                }
            }
        }

        if (state.isExporting) {
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.artifact_export_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(20.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        ArtifactExportWizardFooter(
            state = state,
            onBack = onBack,
            onNext = onNext,
            onConfirmExport = onConfirmExport,
        )
    }
}

@Composable
private fun ArtifactExportWizardFooter(
    state: ArtifactExportSheetState.Visible,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onConfirmExport: () -> Unit,
) {
    when (state.step) {
        ArtifactExportWizardStep.CHOOSE_FORMAT -> {
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
                TextButton(
                    onClick = onNext,
                    enabled = !state.isExporting && state.selectedFormat != null,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.artifact_export_next),
                            fontWeight = FontWeight.SemiBold,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
        ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
            TextButton(
                onClick = onBack,
                enabled = !state.isExporting,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Text(stringResource(R.string.artifact_export_back))
            }
            Button(
                onClick = onConfirmExport,
                enabled = !state.isExporting && state.selectedDestination != null,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.artifact_export_action),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
