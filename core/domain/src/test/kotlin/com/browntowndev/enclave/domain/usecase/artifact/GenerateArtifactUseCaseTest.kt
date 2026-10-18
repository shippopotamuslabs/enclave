package com.browntowndev.enclave.domain.usecase.artifact

import com.browntowndev.enclave.domain.model.inference.GenerateArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.docgen.DocumentValidationPort
import com.browntowndev.enclave.domain.port.inference.ArtifactToolExecutorPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("GenerateArtifactUseCase")
class GenerateArtifactUseCaseTest {
    private val fakeExecutor = FakeArtifactToolExecutorPort()
    private val fakeValidation = FakeDocumentValidationPort()
    private val useCase = GenerateArtifactUseCase(fakeExecutor, fakeValidation)

    @Test
    @DisplayName("Delegates to executor and returns result")
    fun `delegates to executor`() =
        runTest {
            val params = GenerateArtifactParams(title = "Report", content = "# Content")
            val expectedResult =
                ToolExecutionResult(
                    toolName = "start_artifact",
                    resultJson = """{"artifactId":"123"}""",
                    cached = false,
                    latencyMs = 450,
                )

            fakeValidation.result = Result.success(Unit)
            fakeExecutor.result = expectedResult

            val result = useCase(params)

            assertEquals(expectedResult, result)
        }

    /** Recording [ArtifactToolExecutorPort] fake: returns [result] for every execute call. */
    private class FakeArtifactToolExecutorPort : ArtifactToolExecutorPort {
        var result: ToolExecutionResult? = null
        val executeCalls = mutableListOf<GenerateArtifactParams>()

        override suspend fun execute(params: GenerateArtifactParams): ToolExecutionResult {
            executeCalls += params
            return result ?: error("FakeArtifactToolExecutorPort.result not set")
        }
    }

    /** [DocumentValidationPort] fake: every validation entry point returns [result]. */
    private class FakeDocumentValidationPort : DocumentValidationPort {
        var result: Result<Unit> = Result.success(Unit)

        override fun validate(json: String): Result<Unit> = result

        override fun validateStartArtifactArgs(json: String): Result<Unit> = result

        override fun validateAppendBlocksArgs(json: String): Result<Unit> = result
    }
}
