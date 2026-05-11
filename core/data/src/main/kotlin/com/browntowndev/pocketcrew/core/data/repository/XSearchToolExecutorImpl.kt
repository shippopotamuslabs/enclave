package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.inference.XSearchParams
import com.browntowndev.pocketcrew.domain.usecase.inference.BuildXQueryUseCase
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
class XSearchToolExecutorImpl @Inject constructor(
    private val loggingPort: LoggingPort,
    private val settingsRepository: SettingsRepository,
    private val xSearchRepository: XSearchRepository,
    private val buildXQueryUseCase: BuildXQueryUseCase,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "XSearchToolExecutor"
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        requireSupportedTool(request.toolName)
        val params = request.parameters as XSearchParams
        
        val query = buildXQueryUseCase(params)
        
        if (query.isEmpty()) {
            require(false) { "At least one search parameter (keywords, phrases, hashtags, etc.) is required" }
        }
        
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.xSearchEnabled) {
            loggingPort.warning(TAG, "X Search tool invoked but X search is disabled in settings")
            throw IllegalStateException("X Search is disabled in settings")
        }

        loggingPort.info(
            TAG,
            "Executing X search tool provider=${request.provider} " +
                "modelType=${request.modelType} tool=${request.toolName} " +
                "query=$query sortOrder=${params.sort_order.name}"
        )
        
        val resultJson = xSearchRepository.search(query, params.sort_order.name)
        
        loggingPort.info(
            TAG,
            "X search tool execution complete provider=${request.provider} " +
                "modelType=${request.modelType} tool=${request.toolName} " +
                "resultChars=${resultJson.length}"
        )
        
        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.X_SEARCH.name) {
            "Unsupported tool: $toolName"
        }
    }
}
