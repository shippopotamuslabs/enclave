package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Composite tool executor that dispatches tool execution requests
 * to the appropriate executor based on the tool name.
 *
 * Routes:
 * - [ToolDefinition.TAVILY_WEB_SEARCH] → [SearchToolExecutorImpl]
 * - [ToolDefinition.ATTACHED_IMAGE_INSPECT] → [ImageInspectToolExecutor]
 */
@Singleton
class CompositeToolExecutor @Inject constructor(
    private val searchToolExecutor: SearchToolExecutorImpl,
    private val imageInspectToolExecutor: ImageInspectToolExecutor,
) : ToolExecutorPort {

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        return when (request.toolName) {
            ToolDefinition.TAVILY_WEB_SEARCH.name -> searchToolExecutor.execute(request)
            ToolDefinition.ATTACHED_IMAGE_INSPECT.name -> imageInspectToolExecutor.execute(request)
            else -> throw IllegalArgumentException("Unsupported tool: ${request.toolName}")
        }
    }
}