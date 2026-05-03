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
                content = ""
            )
            assertEquals(DocumentType.PDF, request.documentType)
        }

        @Test
        @DisplayName("Allows non-PDF for future phases but marks them")
        fun `allows non-PDF for future phases but marks them`() {
            val wordRequest = ArtifactGenerationRequest(
                documentType = DocumentType.WORD,
                title = "Word Doc",
                content = "# Intro\nHello"
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
                request = ArtifactGenerationRequest(title = "PDF", content = "")
            )
            val wordResult = ArtifactGenerationResult(
                request = ArtifactGenerationRequest(
                    documentType = DocumentType.WORD,
                    title = "Word",
                    content = ""
                )
            )


            assertTrue(pdfResult.isPdf)
            assertEquals(false, wordResult.isPdf)
        }
    }
}
