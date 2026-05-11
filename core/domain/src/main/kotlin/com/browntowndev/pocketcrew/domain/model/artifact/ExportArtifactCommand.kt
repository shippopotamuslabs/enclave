package com.browntowndev.pocketcrew.domain.model.artifact

/**
 * Which file types to produce from the structured document JSON.
 */
enum class ArtifactExportFormat {
    PDF,
    WORD,
    BOTH,
}

/**
 * Whether to open the Android share sheet or write into public Downloads.
 */
enum class ArtifactExportDestination {
    SHARE,
    DOWNLOAD,
}

/**
 * Pure inputs for [com.browntowndev.pocketcrew.domain.usecase.artifact.ExportArtifactDocumentsUseCase].
 */
data class ExportArtifactDocumentsCommand(
    val documentJson: String,
    val baseTitle: String,
    val format: ArtifactExportFormat,
    val destination: ArtifactExportDestination,
)

sealed class ArtifactExportOutcome {
    data class Shared(val fileCount: Int) : ArtifactExportOutcome()

    data class Downloaded(val savedDisplayNames: List<String>) : ArtifactExportOutcome()
}
