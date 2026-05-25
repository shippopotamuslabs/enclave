package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.model.inference.XUserTweetsParams
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XUserTweetsToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val settingsRepository: SettingsRepository,
    private val xSearchRepository: XSearchRepository,
) : ToolExecutorPort {
    companion object {
        private const val TAG = "XUserTweetsToolExecutor"
        private val json = Json { ignoreUnknownKeys = true }
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        requireSupportedTool(request.toolName)
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.xSearchEnabled) {
            loggingPort.warning(TAG, "X User Tweets tool invoked but X search is disabled in settings")
            throw IllegalStateException("X Search is disabled in settings")
        }

        val params = request.parameters as XUserTweetsParams
        val cleanUser = params.username.removePrefix("@").trim()
        val searchText = params.search_text?.trim()?.takeIf { it.isNotEmpty() }

        val result =
            if (searchText == null) {
                loggingPort.info(TAG, "Executing X user tweets timeline username=$cleanUser")
                xSearchRepository.fetchUserTimeline(
                    username = cleanUser,
                    maxResults = 20,
                    paginationToken = params.next_token?.trim()?.takeIf { it.isNotEmpty() },
                )
            } else {
                val query = "from:$cleanUser $searchText"
                loggingPort.info(TAG, "Executing X user tweets search query=$query")
                xSearchRepository.searchPaginated(
                    query = query,
                    sortOrder = "recency",
                    maxPostsPerAuthor = 20,
                    maxTotalPosts = 20,
                    nextToken = params.next_token,
                    ordering = XPostOrdering.RECENCY,
                )
            }

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = json.encodeToString(result)
        )
    }

    private fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.X_USER_TWEETS.name) {
            "Unsupported tool: $toolName"
        }
    }
}
