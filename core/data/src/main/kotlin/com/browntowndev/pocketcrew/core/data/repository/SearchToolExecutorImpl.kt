package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.TavilyWebSearchParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchToolExecutorImpl @Inject constructor(
    private val loggingPort: LoggingPort,
    private val settingsRepository: SettingsRepository,
    private val tavilySearchRepository: TavilySearchRepository,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "SearchToolExecutor"
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        requireSupportedTool(request.toolName)
        val params = request.parameters as TavilyWebSearchParams
        val query = params.query.trim()
        
        if (query.isEmpty()) {
            throw IllegalArgumentException("Tool argument 'query' is required")
        }
        
        val searchEnabled = settingsRepository.settingsFlow.first().searchEnabled
        if (!searchEnabled) {
            loggingPort.warning(TAG, "Search tool invoked but search is disabled in settings")
            throw IllegalStateException("Search is disabled in settings")
        }

        loggingPort.info(
            TAG,
            "Executing search tool provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} query=$query"
        )
        
        val resultJson = tavilySearchRepository.search(query)
        
        loggingPort.info(
            TAG,
            "Search tool execution complete provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} resultChars=${resultJson.length}"
        )
        
        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.TAVILY_WEB_SEARCH.name) {
            "Unsupported tool: $toolName"
        }
    }
}
