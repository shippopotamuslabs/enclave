package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GenerateArtifactParamsTest {

    @Test
    fun `default values`() {
        val params = GenerateArtifactParams(title = "Title", content = "Content")
        assertEquals(MimeType.PDF, params.targetFormat)
        assertEquals("Title", params.title)
        assertEquals("Content", params.content)
    }

    @Test
    fun `toRequest maps fields correctly for PDF`() {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.PDF,
            title = "Title",
            content = "Content"
        )
        val request = params.toRequest()
        assertEquals(MimeType.PDF, request.targetFormat)
        assertEquals("Title", request.title)
        assertEquals("Content", request.content)
    }

    @Test
    fun `toRequest maps fields correctly for Word`() {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.WORD,
            title = "Report",
            content = "Content"
        )
        val request = params.toRequest()
        assertEquals(MimeType.WORD, request.targetFormat)
        assertEquals("Report", request.title)
        assertEquals("Content", request.content)
    }
}
