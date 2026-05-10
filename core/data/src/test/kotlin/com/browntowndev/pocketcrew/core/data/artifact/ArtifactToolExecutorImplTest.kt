package com.browntowndev.pocketcrew.core.data.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.MimeType
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactToolExecutorImplTest {
    private val executor = ArtifactToolExecutor()

    @Test
    fun `execute returns success for PDF`() = runTest {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.PDF,
            title = "Report",
            content = "# Content"
        )
        val result = executor.execute(params)
        assertTrue(result.resultJson.contains("\"status\":\"success\""))
        assertTrue(result.resultJson.contains("\"document_type\":\"PDF\""))
    }

    @Test
    fun `execute returns success for Word`() = runTest {
        val params = GenerateArtifactParams(
            targetFormat = MimeType.WORD,
            title = "Report",
            content = "# Content"
        )
        val result = executor.execute(params)
        assertTrue(result.resultJson.contains("\"status\":\"success\""))
        assertTrue(result.resultJson.contains("\"document_type\":\"WORD\""))
    }
}
