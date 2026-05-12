package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.WebSearchSourceDao
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.model.inference.WebExtractParams
import com.browntowndev.pocketcrew.domain.model.settings.SearchProvider
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ExtractedUrlTrackerPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractToolExecutorImpl
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val settingsRepository: SettingsRepository,
        private val tavilySearchRepository: TavilySearchRepository,
        private val exaSearchRepository: ExaSearchRepository,
        private val webSearchSourceDao: WebSearchSourceDao,
        private val eventBus: ToolExecutionEventBus,
        private val extractedUrlTracker: ExtractedUrlTrackerPort,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "ExtractToolExecutor"
        }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val params = request.parameters as WebExtractParams
            val urls = params.urls

            val settings = settingsRepository.settingsFlow.first()
            if (!settings.searchEnabled) {
                loggingPort.warning(TAG, "Extract tool invoked but search is disabled in settings")
                throw IllegalStateException("Search is disabled in settings")
            }

            val extractDepth = params.extract_depth.name
            val format = params.format.name

            // Emit one Extracting event per URL
            for (url in urls) {
                eventBus.emit(
                    ToolExecutionEvent.Extracting(
                        eventId = UUID.randomUUID().toString(),
                        url = url,
                        chatId = request.chatId,
                        userMessageId = request.userMessageId,
                    ),
                )
            }

            loggingPort.info(
                TAG,
                "Executing extract tool provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} " +
                    "searchProvider=${settings.searchProvider} urls=${urls.size} " +
                    "extractDepth=$extractDepth format=$format",
            )

            val resultJson =
                when (settings.searchProvider) {
                    SearchProvider.TAVILY -> {
                        tavilySearchRepository.extract(
                            urls = urls,
                            extractDepth = extractDepth,
                            format = format,
                        )
                    }
                    SearchProvider.EXA -> {
                        exaSearchRepository.extract(urls = urls)
                    }
                }

            // Mark each URL as extracted in the DAO and tracker
            for (url in urls) {
                try {
                    webSearchSourceDao.markExtracted(url)
                } catch (e: Exception) {
                    loggingPort.warning(TAG, "Failed to mark source as extracted for url=$url: ${e.message}")
                }
                extractedUrlTracker.add(url)
            }

            loggingPort.info(
                TAG,
                "Extract tool execution complete provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} " +
                    "resultChars=${resultJson.length}",
            )

            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = resultJson,
            )
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.WEB_EXTRACT.name) {
                "Unsupported tool: $toolName"
            }
        }
    }
