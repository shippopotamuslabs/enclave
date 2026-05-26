package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.XGetThreadParams
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XGetThreadToolExecutor
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val xSearchRepository: XSearchRepository,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "XGetThreadToolExecutor"
        }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.xSearchEnabled) {
                loggingPort.warning(TAG, "X Get Thread tool invoked but X search is disabled in settings")
                throw IllegalStateException("X Search is disabled in settings")
            }

            val params = request.parameters as XGetThreadParams
            loggingPort.info(TAG, "Executing X get thread tool tweet_url=${params.tweet_url}")

            val resultJson = xSearchRepository.getThread(params.tweet_url)
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = resultJson,
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.X_GET_THREAD.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
