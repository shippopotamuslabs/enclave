package com.browntowndev.pocketcrew.domain.model.artifact

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ArtifactModels")
class ArtifactModelsTest {

    @Nested
    @DisplayName("MimeType")
    inner class MimeTypeTests {

        @Test
        fun `valueOf returns correct enum for PDF`() {
            assertEquals(MimeType.PDF, MimeType.valueOf("PDF"))
        }

        @Test
        fun `MimeType values include expected types`() {
            assertTrue(MimeType.entries.contains(MimeType.PDF))
            assertTrue(MimeType.entries.contains(MimeType.WORD))
            assertTrue(MimeType.entries.contains(MimeType.EXCEL))
            assertTrue(MimeType.entries.contains(MimeType.POWERPOINT))
            assertTrue(MimeType.entries.contains(MimeType.MARKDOWN))
            assertTrue(MimeType.entries.contains(MimeType.PLAIN_TEXT))
        }

        @Test
        fun `MimeType has correct string values`() {
            assertEquals("application/pdf", MimeType.PDF.value)
            assertEquals("text/markdown", MimeType.MARKDOWN.value)
        }
    }

    @Nested
    @DisplayName("ArtifactGenerationRequest")
    inner class ArtifactGenerationRequestTests {

        @Test
        fun `defaults to PDF target format`() {
            val request = ArtifactGenerationRequest(title = "Title", content = "Content")
            assertEquals(MimeType.PDF, request.targetFormat)
        }

        @Test
        fun `can create with specific target format`() {
            val wordRequest = ArtifactGenerationRequest(
                targetFormat = MimeType.WORD,
                title = "Report",
                content = "Content"
            )
            assertEquals(MimeType.WORD, wordRequest.targetFormat)
        }
    }

    @Nested
    @DisplayName("ArtifactGenerationResult")
    inner class ArtifactGenerationResultTests {

        @Test
        fun `isPdf returns true for PDF format`() {
            val pdfResult = ArtifactGenerationResult(
                request = ArtifactGenerationRequest(targetFormat = MimeType.PDF, title = "T", content = "C")
            )
            assertTrue(pdfResult.isPdf)
        }

        @Test
        fun `isPdf returns false for Word format`() {
            val wordResult = ArtifactGenerationResult(
                request = ArtifactGenerationRequest(targetFormat = MimeType.WORD, title = "T", content = "C")
            )
            assertTrue(!wordResult.isPdf)
        }
    }
}
