package com.browntowndev.pocketcrew.domain.model.artifact

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Represents the standard MIME types supported by the system.
 */
@Serializable
enum class MimeType(val value: String) {
    PDF("application/pdf"),
    MARKDOWN("text/markdown"),
    PLAIN_TEXT("text/plain"),
    WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    POWERPOINT("application/vnd.openxmlformats-officedocument.presentationml.presentation")
}

/**
 * Represents the source/role of a file record.
 */
@Serializable
enum class FileSource {
    GENERATED,
    ATTACHMENT
}

/**
 * The request sent to the generate_artifact tool.
 */
@Serializable
data class ArtifactGenerationRequest(
    val targetFormat: MimeType = MimeType.PDF,
    val title: String,
    val content: String
)

/**
 * The successful result of artifact generation.
 */
@Serializable
data class ArtifactGenerationResult(
    val request: ArtifactGenerationRequest,
    val generatedAt: Long = System.currentTimeMillis()
) {
    val isPdf: Boolean get() = request.targetFormat == MimeType.PDF
}
