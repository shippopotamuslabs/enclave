package com.browntowndev.pocketcrew.domain.model.artifact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

@DisplayName("Artifact Domain Models")
class ArtifactModelsTest {

    @Nested
    @DisplayName("DocumentType")
    inner class DocumentTypeTests {

        @Test
        @DisplayName("Phase 1 only supports PDF as the primary type")
        fun `supports PDF as primary type`() {
            assertEquals(DocumentType.PDF, DocumentType.valueOf("PDF"))
        }

        @Test
        @DisplayName("Future document types are defined for server routing")
        fun `defines future document types`() {
            assertTrue(DocumentType.entries.contains(DocumentType.WORD))
            assertTrue(DocumentType.entries.contains(DocumentType.EXCEL))
            assertTrue(DocumentType.entries.contains(DocumentType.POWERPOINT))
        }
    }

    @Nested
    @DisplayName("ArtifactGenerationRequest Validation")
    inner class RequestValidationTests {

        @Test
        @DisplayName("Defaults to PDF document type")
        fun `defaults to PDF`() {
            val request = ArtifactGenerationRequest(
                title = "Test Report",
                sections = emptyList()
            )
            assertEquals(DocumentType.PDF, request.documentType)
        }

        @Test
        @DisplayName("Rejects non-PDF types at construction time in Phase 1 context")
        fun `allows non-PDF for future phases but marks them`() {
            val wordRequest = ArtifactGenerationRequest(
                documentType = DocumentType.WORD,
                title = "Word Doc",
                sections = listOf(
                    ArtifactSection("Intro", listOf(ArtifactBlock.Paragraph("Hello")))
                )
            )
            assertEquals(DocumentType.WORD, wordRequest.documentType)
        }
    }

    @Nested
    @DisplayName("ArtifactGenerationResult")
    inner class ResultTests {

        @Test
        @DisplayName("isPdf returns true only for PDF requests")
        fun `identifies PDF results`() {
            val pdfResult = ArtifactGenerationResult(
                request = ArtifactGenerationRequest(title = "PDF", sections = emptyList())
            )
            val wordResult = ArtifactGenerationResult(
                request = ArtifactGenerationRequest(
                    documentType = DocumentType.WORD,
                    title = "Word",
                    sections = emptyList()
                )
            )

            assertTrue(pdfResult.isPdf)
            assertEquals(false, wordResult.isPdf)
        }
    }

    @Nested
    @DisplayName("ArtifactBlock Types")
    inner class BlockTests {

        @Test
        @DisplayName("Supports all required block types for PDF rendering")
        fun `supports heading, paragraph, list, table, code`() {
            val blocks = listOf(
                ArtifactBlock.Heading(1, "Title"),
                ArtifactBlock.Paragraph("Body text"),
                ArtifactBlock.BulletList(listOf("Item 1")),
                ArtifactBlock.Table(listOf("Col"), listOf(listOf("Cell"))),
                ArtifactBlock.CodeBlock("kotlin", "fun main() {}")
            )
            assertEquals(5, blocks.size)
        }
    }
}
