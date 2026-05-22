package com.browntowndev.enclave.feature.chat.artifactexport

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.browntowndev.enclave.core.ui.theme.EnclaveTheme
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.feature.chat.ArtifactExportSheetState
import com.browntowndev.enclave.feature.chat.ArtifactExportWizardStep
import com.browntowndev.enclave.feature.chat.WizardExportDestination
import com.browntowndev.enclave.feature.chat.WizardExportFormat

@Preview(showBackground = true, name = "Export - format")
@Composable
internal fun PreviewArtifactExportWizardFormat() {
    EnclaveTheme {
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
    EnclaveTheme {
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
