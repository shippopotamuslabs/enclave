package com.browntowndev.pocketcrew.domain.model.artifact

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import kotlinx.serialization.json.JsonClassDiscriminator

/**
 * Represents the type of document to generate.
 * Phase 1 supports only "PDF". Other values are reserved for future server routing.
 */
@Serializable
enum class DocumentType {
    PDF,
    WORD,
    EXCEL,
    POWERPOINT
}

/**
 * A block within an artifact section.
 */
@Serializable
@JsonClassDiscriminator("type")
sealed class ArtifactBlock {
    @Serializable
    @SerialName("heading")
    data class Heading(val level: Int, val text: String) : ArtifactBlock()
    
    @Serializable
    @SerialName("paragraph")
    data class Paragraph(val text: String) : ArtifactBlock()
    
    @Serializable
    @SerialName("bullet_list")
    data class BulletList(val items: List<String>) : ArtifactBlock()
    
    @Serializable
    @SerialName("numbered_list")
    data class NumberedList(val items: List<String>) : ArtifactBlock()
    
    @Serializable
    @SerialName("table")
    data class Table(val headers: List<String>, val rows: List<List<String>>) : ArtifactBlock()
    
    @Serializable
    @SerialName("code_block")
    data class CodeBlock(val language: String?, val code: String) : ArtifactBlock()
}

/**
 * A section of the artifact document.
 */
@Serializable
data class ArtifactSection(
    val title: String,
    val blocks: List<ArtifactBlock>
)

/**
 * The request sent to the generate_artifact tool.
 */
@Serializable
data class ArtifactGenerationRequest(
    val documentType: DocumentType = DocumentType.PDF,
    val title: String,
    val sections: List<ArtifactSection>
)

/**
 * The successful result of artifact generation.
 */
@Serializable
data class ArtifactGenerationResult(
    val request: ArtifactGenerationRequest,
    val generatedAt: Long = System.currentTimeMillis()
) {
    val isPdf: Boolean get() = request.documentType == DocumentType.PDF
}
