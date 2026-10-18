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
open class XUserTweetsToolExecutor
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val xSearchRepository: XSearchRepository,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "XUserTweetsToolExecutor"
            private const val MIN_MAX_RESULTS = 5
            private const val MAX_MAX_RESULTS = 100
            private val json = Json { ignoreUnknownKeys = true }
        }

        open override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.xSearchEnabled) {
                loggingPort.warning(TAG, "X User Tweets tool invoked but X search is disabled in settings")
                throw IllegalStateException("X Search is disabled in settings")
            }

            val params = request.parameters as XUserTweetsParams
            val cleanUser = params.username.removePrefix("@").trim()
            val searchText = params.search_text?.trim()?.takeIf { it.isNotEmpty() }
            val maxResults = params.max_results.coerceIn(MIN_MAX_RESULTS, MAX_MAX_RESULTS)

            val nextToken = params.next_token?.trim()?.takeIf { it.isNotEmpty() }
            val pageSource = params.pagination_source?.trim()?.lowercase()

            val result =
                try {
                    when {
                        pageSource == "search" || searchText != null -> {
                            val query =
                                if (searchText.isNullOrBlank()) {
                                    "from:${cleanUser.lowercase()}"
                                } else {
                                    "from:${cleanUser.lowercase()} $searchText"
                                }
                            loggingPort.info(
                                TAG,
                                "Executing X user tweets search query=$query maxResults=$maxResults",
                            )
                            xSearchRepository.searchPaginated(
                                query = query,
                                sortOrder = "recency",
                                maxPostsPerAuthor = maxResults,
                                maxTotalPosts = maxResults,
                                nextToken = nextToken,
                                ordering = XPostOrdering.RECENCY,
                                skipDiscoveryFilters = true,
                            )
                        }
                        else -> {
                            loggingPort.info(
                                TAG,
                                "Executing X user tweets timeline username=$cleanUser maxResults=$maxResults",
                            )
                            xSearchRepository.fetchUserTimeline(
                                username = cleanUser,
                                maxResults = maxResults,
                                paginationToken = nextToken,
                            )
                        }
                    }
                } catch (e: java.io.IOException) {
                    loggingPort.warning(TAG, "X User Tweets failed with network error: ${e.message}")
                    return ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = "{\"success\":false,\"error\":\"Network error: ${e.message}\"}",
                    )
                }

            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = json.encodeToString(result),
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.X_USER_TWEETS.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
