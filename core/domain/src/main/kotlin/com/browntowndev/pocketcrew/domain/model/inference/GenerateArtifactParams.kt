package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactGenerationRequest
import com.browntowndev.pocketcrew.domain.model.artifact.ArtifactSection
import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Parameters for the generate_artifact tool call.
 * The 'content' field carries the structured JSON representation of the document.
 */
@Serializable
data class GenerateArtifactParams(
    @ToolParam(description = "The type of document to generate. Phase 1 only supports 'PDF'.")
    val documentType: DocumentType = DocumentType.PDF,

    @ToolParam(description = "The title of the generated document.")
    val title: String,

    @ToolParam(description = "A structured list of sections for the document. Each section has a title and a list of blocks. Supported block types: heading (level, text), paragraph (text), bullet_list (items), numbered_list (items), table (headers, rows), code_block (language, code).")
    val sections: List<ArtifactSection>
) : ToolParameters {
    fun toRequest(): ArtifactGenerationRequest {
        return ArtifactGenerationRequest(
            documentType = documentType,
            title = title,
            sections = sections
        )
    }
}
