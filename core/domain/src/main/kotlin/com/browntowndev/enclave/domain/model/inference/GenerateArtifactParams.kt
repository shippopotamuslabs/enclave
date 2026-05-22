package com.browntowndev.enclave.domain.model.inference

import com.browntowndev.enclave.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.enclave.domain.model.artifact.MimeType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Parameters for the generate_artifact tool call.
 * The 'content' field carries the structured JSON representation of the document.
 */
@Serializable
data class GenerateArtifactParams(
    @property:ToolParam(description = "The type of document to generate. Currently supports 'PDF' and 'WORD'.")
    val targetFormat: MimeType = MimeType.PDF,
    @property:ToolParam(description = "The title of the generated document.")
    val title: String,
    @SerialName("content")
    @property:ToolParam(description = "The full content of the document in standard Markdown format.")
    val content: String,
) : ToolParameters {
    fun toRequest(): ArtifactGenerationRequest {
        val artifactId = "legacy_${title.hashCode()}"
        return ArtifactGenerationRequest(
            artifactId = artifactId,
            title = title,
            content = content,
        )
    }
}
