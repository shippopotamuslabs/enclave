package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.GenerateArtifactParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult

/**
 * Port for executing artifact generation tool calls.
 */
interface ArtifactToolExecutorPort {
    suspend fun execute(params: GenerateArtifactParams): ToolExecutionResult
}
