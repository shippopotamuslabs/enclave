package com.browntowndev.pocketcrew.feature.chat.artifactexport

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.pocketcrew.core.ui.theme.PocketCrewTheme
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.feature.chat.ArtifactExportSheetState
import com.browntowndev.pocketcrew.feature.chat.ArtifactExportWizardStep
import com.browntowndev.pocketcrew.feature.chat.WizardExportDestination
import com.browntowndev.pocketcrew.feature.chat.WizardExportFormat

@Preview(showBackground = true, name = "Export - format")
@Composable
internal fun PreviewArtifactExportWizardFormat() {
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
                    documentJson = """{"blocks":[]}""",
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

@Preview(showBackground = true, name = "Export - destination")
@Composable
internal fun PreviewArtifactExportWizardDestination() {
    PocketCrewTheme {
        ArtifactExportWizardContent(
            state =
                ArtifactExportSheetState.Visible(
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
            onConfirmExport = {},
        )
    }
}
