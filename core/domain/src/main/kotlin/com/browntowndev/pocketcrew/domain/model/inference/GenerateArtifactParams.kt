package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters for the generate_artifact tool call.
 * The 'content' field carries the structured JSON representation of the document.
 */
@Serializable
data class GenerateArtifactParams(
    @property:ToolParam(description = "The type of document to generate. Phase 1 only supports 'PDF'.")
    val targetFormat: MimeType = MimeType.PDF,

    @property:ToolParam(description = "The title of the generated document.")
    val title: String,

    @SerialName("content")
    @property:ToolParam(description = "The full content of the document in standard Markdown format.")
    val content: String
) : ToolParameters {
    fun toRequest(): ArtifactGenerationRequest {
        return ArtifactGenerationRequest(
            targetFormat = targetFormat,
            title = title,
            content = content
        )
    }
}
