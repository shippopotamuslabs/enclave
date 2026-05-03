package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties

@DisplayName("GenerateArtifactParams")
class GenerateArtifactParamsTest {

    @Test
    @DisplayName("Defaults to PDF document type")
    fun `defaults to PDF`() {
        val params = GenerateArtifactParams(title = "Report", content = "# Title")
        assertEquals(DocumentType.PDF, params.documentType)
    }

    @Test
    @DisplayName("Converts to ArtifactGenerationRequest")
    fun `converts to request`() {
        val params = GenerateArtifactParams(
            documentType = DocumentType.PDF,
            title = "Q2 Status",
            content = "## Subtitle\nBody"
        )

        val request = params.toRequest()
        assertEquals(DocumentType.PDF, request.documentType)
        assertEquals("Q2 Status", request.title)
        assertEquals("## Subtitle\nBody", request.content)
    }

    @Test
    @DisplayName("Converts WORD document type correctly")
    fun `converts word document type`() {
        val params = GenerateArtifactParams(
            documentType = DocumentType.WORD,
            title = "Word Doc",
            content = "Content"
        )

        val request = params.toRequest()
        assertEquals(DocumentType.WORD, request.documentType)
    }

    @Test
    @DisplayName("Generates correct schema with markdown string")
    fun `generates correct schema`() {
        val schema = ToolSchemaGenerator.generateSchema(GenerateArtifactParams::class)
        val properties = schema.properties
        
        // Verify it contains content property and description
        assertTrue(properties.containsKey("content"), "Schema missing 'content'. Keys: ${properties.keys}")
        val markdownProp = properties["content"]?.jsonObject!!
        assertEquals("string", markdownProp["type"]?.jsonPrimitive?.content)
        assertTrue(markdownProp["description"]?.jsonPrimitive?.content?.contains("Markdown") == true)
        
        // Verify it no longer contains the old structured elements
        assertFalse(properties.containsKey("sections"))
        val schemaString = schema.toString()
        assertFalse(schemaString.contains("oneOf"))
        assertFalse(schemaString.contains("bullet_list"))
    }
}
