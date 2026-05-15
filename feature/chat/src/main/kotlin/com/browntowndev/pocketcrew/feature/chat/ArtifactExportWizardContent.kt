@file:Suppress("MaxLineLength", "MagicNumber", "UnusedPrivateProperty", "UnusedParameter", "TooManyFunctions", "ComplexCondition", "LargeClass", "CyclomaticComplexMethod", "SwallowedException", "InstanceOfCheckForException", "ThrowsCount", "NestedBlockDepth", "ReturnCount", "LoopWithTooManyJumpStatements", "FunctionNaming", "LongMethod", "MatchingDeclarationName", "UnusedPrivateMember", "LongParameterList")
package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

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
                    enabled =
                        !state.isExporting &&
                            state.selectedDestination != null,
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
}

@Composable
private fun WizardSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun WizardSelectableCard(
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String,
) {
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        }
    val borderWidth = if (selected) 2.dp else 1.dp
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "Export - format")
@Composable
private fun ArtifactExportWizardContentPreviewFormat() {
    PocketCrewTheme {
        ArtifactExportWizardContent(
            state = ArtifactExportSheetState.Visible(
                messageId = MessageId("m1"),
                artifactId = "a1",
                title = "Quarterly summary",
                step = ArtifactExportWizardStep.CHOOSE_FORMAT,
                selectedFormat = WizardExportFormat.PDF,
                selectedDestination = null,
                isExporting = false,
                documentJson = """{"blocks":[]}""",
            ),
            onDismiss = {},
            onSelectFormat = {},
            onSelectDestination = {},
            onBack = {},
            onNext = {},
            onConfirmExport = {}
        )
    }
}

@Preview(showBackground = true, name = "Export - destination")
@Composable
private fun ArtifactExportWizardContentPreviewDestination() {
    PocketCrewTheme {
        ArtifactExportWizardContent(
            state = ArtifactExportSheetState.Visible(
                messageId = MessageId("m1"),
                artifactId = "a1",
                title = "Quarterly summary",
                step = ArtifactExportWizardStep.CHOOSE_DESTINATION,
                selectedFormat = WizardExportFormat.BOTH,
                selectedDestination = WizardExportDestination.SHARE,
                isExporting = false,
                documentJson = """{"blocks":[]}""",
            ),
            onDismiss = {},
            onSelectFormat = {},
            onSelectDestination = {},
            onBack = {},
            onNext = {},
            onConfirmExport = {}
        )
    }
}
