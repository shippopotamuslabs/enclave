package com.browntowndev.pocketcrew.domain.model.artifact

import kotlinx.serialization.Serializable

/**
 * Represents the standard MIME types supported by the system.
 */
@Serializable
enum class MimeType(
    val value: String,
) {
    PDF("application/pdf"),
    MARKDOWN("text/markdown"),
    PLAIN_TEXT("text/plain"),
    WORD("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    EXCEL("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    POWERPOINT("application/vnd.openxmlformats-officedocument.presentationml.presentation"),
}

/**
 * Represents the source/role of a file record.
 */
@Serializable
enum class FileSource {
    GENERATED,
    ATTACHMENT,
}

/**
 * Payload for opening a generated document from chat.
 *
 * [content] is the structured document JSON (Rust `Document` schema).
 * [targetFormat] is the **user's saved preferred export format** (PDF or Word). `null` means they
 * have not chosen yet — the chat UI should prompt before navigating to the preview screen.
 */
@Serializable
data class ArtifactGenerationRequest(
    /**
     * Stable identifier for the artifact row in storage.
     * This should equal the canonical document `id` used by `documents`.
     */
    val artifactId: String,
    val targetFormat: MimeType? = null,
    val title: String,
    val content: String,
)

/**
 * Merges artifact rows for a single assistant message. Duplicate [ArtifactGenerationRequest.title]
 * values are collapsed: a later entry with non-blank document JSON replaces an earlier empty
 * placeholder (e.g. `start_artifact` UI chip vs final `append_blocks` payload).
 */
fun mergeArtifactGenerationRequests(
    base: List<ArtifactGenerationRequest>,
    additions: List<ArtifactGenerationRequest>,
): List<ArtifactGenerationRequest> {
    val merged = base.toMutableList()
    for (candidate in additions) {
        val index = merged.indexOfFirst { it.artifactId == candidate.artifactId }
        if (index < 0) {
            merged.add(candidate)
        } else if (shouldPreferArtifactContent(merged[index], candidate)) {
            merged[index] =
                candidate.copy(
                    targetFormat = candidate.targetFormat ?: merged[index].targetFormat,
                )
        }
    }
    return merged
}

private fun shouldPreferArtifactContent(
    current: ArtifactGenerationRequest,
    candidate: ArtifactGenerationRequest,
): Boolean {
    if (candidate.content.isBlank()) return false
    if (current.content.isBlank()) return true
    return candidate.content.length > current.content.length
}

/**
 * The successful result of artifact generation.
 */
@Serializable
data class ArtifactGenerationResult(
    val request: ArtifactGenerationRequest,
    val generatedAt: Long = System.currentTimeMillis(),
) {
    val isPdf: Boolean get() = request.targetFormat == MimeType.PDF
    val isWord: Boolean get() = request.targetFormat == MimeType.WORD
    val hasExportPreference: Boolean get() = request.targetFormat != null
}
