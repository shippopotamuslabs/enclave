package com.browntowndev.enclave.domain.model.artifact

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
        fun `defaults target format to null until user chooses export preference`() {
            val request =
                ArtifactGenerationRequest(
                    artifactId = "artifact-1",
                    title = "Title",
                    content = "Content",
                )
            assertEquals(null, request.targetFormat)
        }

        @Test
        fun `can create with specific target format`() {
            val wordRequest =
                ArtifactGenerationRequest(
                    artifactId = "artifact-2",
                    targetFormat = MimeType.WORD,
                    title = "Report",
                    content = "Content",
                )
            assertEquals(MimeType.WORD, wordRequest.targetFormat)
        }
    }

    @Nested
    @DisplayName("mergeArtifactGenerationRequests")
    inner class MergeArtifactGenerationRequestsTests {
        @Test
        fun `replaces empty placeholder with richer same title entry`() {
            val placeholder =
                ArtifactGenerationRequest(
                    artifactId = "artifact-same",
                    title = "T",
                    content = "",
                )
            val full =
                ArtifactGenerationRequest(
                    artifactId = "artifact-same",
                    title = "T",
                    content = """{"version":"1.0"}""",
                )
            val merged = mergeArtifactGenerationRequests(listOf(placeholder), listOf(full))
            assertEquals(1, merged.size)
            assertEquals(full.content, merged[0].content)
        }

        @Test
        fun `FAAL-AR-02 mergeArtifactGenerationRequests prefers non blank content over placeholder`() {
            val placeholder =
                ArtifactGenerationRequest(
                    artifactId = "doc-merge-1",
                    title = "Report",
                    content = "",
                )
            val full =
                ArtifactGenerationRequest(
                    artifactId = "doc-merge-1",
                    title = "Report",
                    content = """{"version":"1.0","blocks":[]}""",
                )

            val merged = mergeArtifactGenerationRequests(listOf(placeholder), listOf(full))

            assertEquals(1, merged.size, "FAAL-AR-02 merged size")
            assertEquals("""{"version":"1.0","blocks":[]}""", merged.single().content, "FAAL-AR-02 content")
        }

        @Test
        fun `keeps first when second is also empty`() {
            val a =
                ArtifactGenerationRequest(
                    artifactId = "artifact-same-empty",
                    title = "T",
                    content = "",
                )
            val b =
                ArtifactGenerationRequest(
                    artifactId = "artifact-same-empty",
                    title = "T",
                    content = "",
                )
            val merged = mergeArtifactGenerationRequests(listOf(a), listOf(b))
            assertEquals(1, merged.size)
            assertEquals(a, merged[0])
        }

        @Test
        fun `appends distinct titles`() {
            val a = ArtifactGenerationRequest(artifactId = "artifact-a", title = "A", content = "x")
            val b = ArtifactGenerationRequest(artifactId = "artifact-b", title = "B", content = "y")
            val merged = mergeArtifactGenerationRequests(listOf(a), listOf(b))
            assertEquals(2, merged.size)
        }
    }

    @Nested
    @DisplayName("ArtifactGenerationResult")
    inner class ArtifactGenerationResultTests {
        @Test
        fun `isPdf returns true for PDF format`() {
            val pdfResult =
                ArtifactGenerationResult(
                    request =
                        ArtifactGenerationRequest(
                            artifactId = "artifact-pdf",
                            targetFormat = MimeType.PDF,
                            title = "T",
                            content = "C",
                        ),
                )
            assertTrue(pdfResult.isPdf)
        }

        @Test
        fun `isPdf returns false for Word format`() {
            val wordResult =
                ArtifactGenerationResult(
                    request =
                        ArtifactGenerationRequest(
                            artifactId = "artifact-word",
                            targetFormat = MimeType.WORD,
                            title = "T",
                            content = "C",
                        ),
                )
            assertTrue(!wordResult.isPdf)
        }
    }
}
