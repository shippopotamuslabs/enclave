package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.XTrendingParams
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class XTrendingToolExecutor
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val xSearchRepository: XSearchRepository,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "XTrendingToolExecutor"
        }

        open override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.xSearchEnabled) {
                loggingPort.warning(TAG, "X Trending tool invoked but X search is disabled in settings")
                throw IllegalStateException("X Search is disabled in settings")
            }

            val params = request.parameters as XTrendingParams
            loggingPort.info(TAG, "Executing X trending tool location=${params.location}")

            val resultJson =
                try {
                    xSearchRepository.getTrends(params.location)
                } catch (e: java.io.IOException) {
                    loggingPort.warning(TAG, "X Trending failed with network error: ${e.message}")
                    return ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = "{\"success\":false,\"error\":\"Network error: ${e.message}\"}",
                    )
                }
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = resultJson,
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.X_TRENDING.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
