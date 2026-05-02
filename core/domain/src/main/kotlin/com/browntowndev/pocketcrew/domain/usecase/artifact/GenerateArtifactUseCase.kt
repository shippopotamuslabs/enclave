package com.browntowndev.pocketcrew.domain.usecase.artifact

import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ArtifactToolExecutorPort

/**
 * Use case for generating artifacts via the tool system.
 */
class GenerateArtifactUseCase(
    private val artifactToolExecutor: ArtifactToolExecutorPort
) {
    suspend operator fun invoke(params: GenerateArtifactParams): ToolExecutionResult {
        return artifactToolExecutor.execute(params)
    }
}
