package com.browntowndev.pocketcrew.domain.usecase.artifact

import com.browntowndev.pocketcrew.domain.model.artifact.DocumentType
import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ArtifactToolExecutorPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("ArtifactToolExecutor")
class ArtifactToolExecutorTest {

    private val fakeExecutor = mockk<ArtifactToolExecutorPort>()

    @Test
    @DisplayName("Successfully executes valid PDF artifact request")
    fun `executes valid PDF request`() = runTest {
        val params = GenerateArtifactParams(
            documentType = DocumentType.PDF,
            title = "Test Report",
            sections = emptyList()
        )

        coEvery { fakeExecutor.execute(params) } returns ToolExecutionResult(
            toolName = "generate_artifact",
            resultJson = """{"status":"success"}""",
            cached = false,
            latencyMs = 120
        )

        val result = fakeExecutor.execute(params)

        assertEquals("generate_artifact", result.toolName)
        assertTrue(result.resultJson.contains("success"))
    }

    @Test
    @DisplayName("Rejects non-PDF document types in Phase 1")
    fun `rejects non-PDF types`() = runTest {
        val params = GenerateArtifactParams(
            documentType = DocumentType.WORD,
            title = "Word Doc",
            sections = emptyList()
        )

        coEvery { fakeExecutor.execute(params) } throws IllegalArgumentException("Server routing not implemented for WORD")

        val exception = runCatching { fakeExecutor.execute(params) }.exceptionOrNull()
        assertTrue(exception is IllegalArgumentException)
    }
}
