package com.browntowndev.pocketcrew.feature.chat

import androidx.compose.runtime.Immutable
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.chat.MessageId

/**
 * Chat row chip for a generated artifact — no domain types in composable parameters.
 */
@Immutable
data class ArtifactCardUiModel(
    val artifactId: String,
    val title: String,
    val documentJson: String,
    val badgeLabel: String,
    val subtitle: String,
    val ctaLabel: String,
)

fun ArtifactGenerationRequest.toArtifactCardUiModel(): ArtifactCardUiModel =
    ArtifactCardUiModel(
        artifactId = artifactId,
        title = title,
        documentJson = content,
        badgeLabel = "Doc",
        subtitle = "Document • Generated",
        ctaLabel = "Export",
    )

enum class ArtifactExportWizardStep {
    CHOOSE_FORMAT,
    CHOOSE_DESTINATION,
}

enum class WizardExportFormat {
    PDF,
    WORD,
    BOTH,
}

enum class WizardExportDestination {
    SHARE,
    DOWNLOAD,
}

sealed class ArtifactExportSheetState {
    data object Hidden : ArtifactExportSheetState()

    data class Visible(
        val messageId: MessageId,
        val artifactId: String,
        val title: String,
        val step: ArtifactExportWizardStep,
        val selectedFormat: WizardExportFormat?,
        val selectedDestination: WizardExportDestination?,
        val isExporting: Boolean,
        val documentJson: String,
        val errorMessage: String? = null,
    ) : ArtifactExportSheetState()
}
