package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.GenerateArtifactParams
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult

/**
 * Port for executing artifact generation tool calls.
 */
interface ArtifactToolExecutorPort {
    suspend fun execute(params: GenerateArtifactParams): ToolExecutionResult
}
