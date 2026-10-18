package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.WebSearchParams
import com.browntowndev.enclave.domain.model.settings.SearchProvider
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import com.browntowndev.enclave.domain.port.telemetry.TelemetryEventPort
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class SearchToolExecutorImpl
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val tavilySearchRepository: TavilySearchRepository,
        private val exaSearchRepository: ExaSearchRepository,
        private val telemetryEventPort: TelemetryEventPort? = null,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "SearchToolExecutor"
        }

        open override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            val startedAtMs = System.currentTimeMillis()
            requireSupportedTool(request.toolName)
            val params = request.parameters as WebSearchParams
            val query = params.query.trim()

            if (query.isEmpty()) {
                require(false) { "Tool argument 'query' is required" }
            }

            val settings = settingsRepository.settingsFlow.first()
            if (!settings.searchEnabled) {
                loggingPort.warning(TAG, "Search tool invoked but search is disabled in settings")
                throw IllegalStateException("Search is disabled in settings")
            }

            loggingPort.info(
                TAG,
                "Executing search tool provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} " +
                    "searchProvider=${settings.searchProvider} query=$query",
            )

            val resultJson =
                try {
                    when (settings.searchProvider) {
                        SearchProvider.TAVILY -> tavilySearchRepository.search(query)
                        SearchProvider.EXA -> exaSearchRepository.search(query)
                    }
                } catch (e: java.io.IOException) {
                    loggingPort.warning(TAG, "Search failed with network error: ${e.message}")
                    telemetryEventPort?.emitToolExecutionOutcome(
                        startedAtMs = startedAtMs,
                        toolName = request.toolName,
                        success = false,
                        errorMessage = e.message,
                    )
                    return ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = "{\"success\":false,\"error\":\"Network error: ${e.message}\"}",
                    )
                }

            loggingPort.info(
                TAG,
                "Search tool execution complete provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} " +
                    "resultChars=${resultJson.length}",
            )

            telemetryEventPort?.emitToolExecutionOutcome(
                startedAtMs = startedAtMs,
                toolName = request.toolName,
                success = true,
            )

            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = resultJson,
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.WEB_SEARCH.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
