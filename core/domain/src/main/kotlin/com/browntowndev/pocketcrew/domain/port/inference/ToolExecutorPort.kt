package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult

interface ToolExecutorPort {
    suspend fun execute(request: ToolCallRequest): ToolExecutionResult
}
