package com.browntowndev.pocketcrew.domain.usecase.artifact

import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ArtifactToolExecutorPort
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GenerateArtifactUseCase")
class GenerateArtifactUseCaseTest {

    private val mockExecutor = mockk<ArtifactToolExecutorPort>()
    private val mockValidation = mockk<com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort>()
    private val useCase = GenerateArtifactUseCase(mockExecutor, mockValidation)

    @Test
    @DisplayName("Delegates to executor and returns result")
    fun `delegates to executor`() = runTest {
        val params = GenerateArtifactParams(title = "Report", content = "# Content")
        val expectedResult = ToolExecutionResult(
            toolName = "start_artifact",
            resultJson = """{"artifactId":"123"}""",
            cached = false,
            latencyMs = 450
        )

        io.mockk.every { mockValidation.validate(any()) } returns Result.success(Unit)
        coEvery { mockExecutor.execute(params) } returns expectedResult

        val result = useCase(params)

        assertEquals(expectedResult, result)
    }
}
