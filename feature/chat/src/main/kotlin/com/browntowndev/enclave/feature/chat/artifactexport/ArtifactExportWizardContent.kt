package com.browntowndev.enclave.feature.chat.artifactexport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveIconButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclavePrimaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSecondaryButton
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSectionHeader
import com.browntowndev.enclave.core.ui.component.enclave.EnclaveSheetHeader
import com.browntowndev.enclave.core.ui.component.enclave.enclaveSurface
import com.browntowndev.enclave.core.ui.theme.EnclaveNeoTokens
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
        modifier = modifier.fillMaxWidth(),
    ) {
        EnclaveSheetHeader(
            title = stringResource(R.string.artifact_export_sheet_title),
            subtitle = state.title,
            onDismiss = onDismiss,
            trailingContent = {
                EnclaveIconButton(
                    onClick = onDismiss,
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(R.string.artifact_export_close),
                    enabled = !state.isExporting,
                )
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        when (state.step) {
            ArtifactExportWizardStep.CHOOSE_FORMAT -> {
                EnclaveSectionHeader(text = stringResource(R.string.artifact_export_step_format))
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
                EnclaveSectionHeader(text = stringResource(R.string.artifact_export_step_destination))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WizardSelectableCard(
                        selected = state.selectedDestination == WizardExportDestination.SHARE,
                        onClick = { onSelectDestination(WizardExportDestination.SHARE) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.Share,
                        title = stringResource(R.string.artifact_export_dest_share),
                        subtitle = stringResource(R.string.artifact_export_dest_share_subtitle),
                        showNavigationChevron = false,
                    )
                    WizardSelectableCard(
                        selected = state.selectedDestination == WizardExportDestination.DOWNLOAD,
                        onClick = { onSelectDestination(WizardExportDestination.DOWNLOAD) },
                        enabled = !state.isExporting,
                        icon = Icons.Default.FileDownload,
                        title = stringResource(R.string.artifact_export_dest_download),
                        subtitle = stringResource(R.string.artifact_export_dest_download_subtitle),
                        showNavigationChevron = false,
                    )
                }
            }
        }

        if (state.isExporting) {
            Spacer(modifier = Modifier.height(20.dp))
            LinearProgressIndicator(
                strokeCap = StrokeCap.Butt,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(EnclaveNeoTokens.shape),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.artifact_export_in_progress),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        state.errorMessage?.let { message ->
            Spacer(modifier = Modifier.height(20.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .enclaveSurface(
                            backgroundColor = MaterialTheme.colorScheme.errorContainer,
                            borderColor = MaterialTheme.colorScheme.error,
                            withShadow = false,
                        ).padding(12.dp),
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EnclaveSecondaryButton(
                    text = stringResource(R.string.artifact_export_back),
                    onClick = onBack,
                    enabled = !state.isExporting,
                    modifier = Modifier.weight(1f),
                )
                EnclavePrimaryButton(
                    text = stringResource(R.string.artifact_export_next),
                    onClick = onNext,
                    enabled = !state.isExporting && state.selectedFormat != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        ArtifactExportWizardStep.CHOOSE_DESTINATION -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EnclaveSecondaryButton(
                    text = stringResource(R.string.artifact_export_back),
                    onClick = onBack,
                    enabled = !state.isExporting,
                    modifier = Modifier.weight(1f),
                )
                EnclavePrimaryButton(
                    text = stringResource(R.string.artifact_export_action),
                    onClick = onConfirmExport,
                    enabled = !state.isExporting && state.selectedDestination != null,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
