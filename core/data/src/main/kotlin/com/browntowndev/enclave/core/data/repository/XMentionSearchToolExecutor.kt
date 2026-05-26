package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.XMentionSearchParams
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XMentionSearchToolExecutor
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val xSearchRepository: XSearchRepository,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "XMentionSearchToolExecutor"
            private val json = Json { ignoreUnknownKeys = true }
        }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.xSearchEnabled) {
                loggingPort.warning(TAG, "X Mention Search tool invoked but X search is disabled in settings")
                throw IllegalStateException("X Search is disabled in settings")
            }

            val params = request.parameters as XMentionSearchParams
            val cleanUser = params.username.removePrefix("@").trim()
            val query = "@$cleanUser ${params.search_text.trim()}"

            loggingPort.info(TAG, "Executing X mention search tool query=$query")

            val result =
                xSearchRepository.searchPaginated(
                    query = query,
                    sortOrder = "recency",
                    maxPostsPerAuthor = 20,
                    maxTotalPosts = 20,
                    nextToken = params.next_token,
                )

            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = json.encodeToString(result),
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.X_MENTION_SEARCH.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
