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
 * The request sent to the generate_artifact tool.
 */
@Serializable
data class ArtifactGenerationRequest(
    val documentType: DocumentType = DocumentType.PDF,
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
    val isPdf: Boolean get() = request.documentType == DocumentType.PDF
}
