package com.browntowndev.enclave.core.testing

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort

/** [ToolExecutorPort] fake (ENC-28): records requests and returns a canned result per tool. */
class FakeToolExecutor : ToolExecutorPort {
    val executedRequests = mutableListOf<ToolCallRequest>()
    var resultJson: String = "{}"
    var failure: Throwable? = null

    /** When set, fully controls execute (gates, dynamic results). Takes precedence over [failure]/[resultJson]. */
    var behavior: (suspend (ToolCallRequest) -> ToolExecutionResult)? = null

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        executedRequests += request
        behavior?.let { return it(request) }
        failure?.let { throw it }
        return ToolExecutionResult(toolName = request.toolName, resultJson = resultJson)
    }
}
