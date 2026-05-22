package com.browntowndev.enclave.domain.port.inference

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult

interface ToolExecutorPort {
    suspend fun execute(request: ToolCallRequest): ToolExecutionResult
}
