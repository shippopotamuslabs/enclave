package com.browntowndev.pocketcrew.domain.model.inference

import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
    fun `toRequest maps title and content and leaves targetFormat unset for user preference`() {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.PDF,
            title = "Title",
            content = "Content",
        )
        val request = params.toRequest()
        assertNull(request.targetFormat)
        assertEquals("legacy_${params.title.hashCode()}", request.artifactId)
        assertEquals("Title", request.title)
        assertEquals("Content", request.content)
    }

    @Test
    fun `toRequest ignores model Word hint for persisted user preference`() {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.WORD,
            title = "Report",
            content = "Content",
        )
        val request = params.toRequest()
        assertNull(request.targetFormat)
        assertEquals("legacy_${params.title.hashCode()}", request.artifactId)
        assertEquals("Report", request.title)
        assertEquals("Content", request.content)
    }
}
