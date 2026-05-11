package com.browntowndev.pocketcrew.domain.usecase.artifact

import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.docgen.DocumentValidationPort
import com.browntowndev.pocketcrew.domain.port.inference.ArtifactToolExecutorPort
import javax.inject.Inject

/**
 * Use case for generating artifacts via the tool system.
 */
class GenerateArtifactUseCase @Inject constructor(
    private val artifactToolExecutor: ArtifactToolExecutorPort,
    private val documentValidationPort: DocumentValidationPort
) {
    suspend operator fun invoke(params: GenerateArtifactParams): ToolExecutionResult {
        // Validate with Rust library first
        documentValidationPort.validate(params.content).onFailure { error ->
            return ToolExecutionResult(
                toolName = ToolDefinition.START_ARTIFACT.name,
                resultJson = "{\"status\":\"error\",\"issues\":[{\"path\":\"/content\",\"message\":\"${error.message}\"}]}"
            )
        }
        return artifactToolExecutor.execute(params)
    }
}
