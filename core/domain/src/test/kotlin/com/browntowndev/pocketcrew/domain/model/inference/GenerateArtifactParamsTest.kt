package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GenerateArtifactParams")
class GenerateArtifactParamsTest {

    @Test
    @DisplayName("Defaults to PDF document type")
    fun `defaults to PDF`() {
        val params = GenerateArtifactParams(title = "Report", sections = emptyList())
        assertEquals(DocumentType.PDF, params.documentType)
    }

    @Test
    @DisplayName("Converts to ArtifactGenerationRequest")
    fun `converts to request`() {
        val params = GenerateArtifactParams(
            documentType = DocumentType.PDF,
            title = "Q2 Status",
            sections = emptyList()
        )

        val request = params.toRequest()
        assertEquals(DocumentType.PDF, request.documentType)
        assertEquals("Q2 Status", request.title)
    }

    @Test
    @DisplayName("Converts WORD document type correctly")
    fun `converts word document type`() {
        val params = GenerateArtifactParams(
            documentType = DocumentType.WORD,
            title = "Word Doc",
            sections = emptyList()
        )

        val request = params.toRequest()
        assertEquals(DocumentType.WORD, request.documentType)
    }

    @Test
    @DisplayName("Generates correct schema with sealed blocks")
    fun `generates correct schema`() {
        val schema = ToolSchemaGenerator.generateSchema(GenerateArtifactParams::class)
        val schemaString = schema.toString()
        
        // Verify it contains oneOf for the blocks
        assert(schemaString.contains("oneOf"))
        assert(schemaString.contains("heading"))
        assert(schemaString.contains("paragraph"))
        assert(schemaString.contains("bullet_list"))
        assert(schemaString.contains("\"type\":")) // Discriminator
    }
}
