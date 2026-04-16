package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.core.data.local.TavilySourceDao
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionEvent
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import kotlinx.coroutines.flow.first
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExtractToolExecutorImpl @Inject constructor(
    private val loggingPort: LoggingPort,
    private val settingsRepository: SettingsRepository,
    private val tavilySearchRepository: TavilySearchRepository,
    private val tavilySourceDao: TavilySourceDao,
    private val eventBus: ToolExecutionEventBus,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "ExtractToolExecutor"
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        requireSupportedTool(request.toolName)
        val urls = ToolEnvelopeParser.extractRequiredUrls(request.argumentsJson)

        val searchEnabled = settingsRepository.settingsFlow.first().searchEnabled
        if (!searchEnabled) {
            loggingPort.warning(TAG, "Extract tool invoked but search is disabled in settings")
            throw IllegalStateException("Search is disabled in settings")
        }

        val extractDepth = extractOptionalString(request.argumentsJson, "extract_depth") ?: "basic"
        val format = extractOptionalString(request.argumentsJson, "format") ?: "markdown"

        // Emit one Extracting event per URL
        for (url in urls) {
            eventBus.emit(
                ToolExecutionEvent.Extracting(
                    eventId = UUID.randomUUID().toString(),
                    url = url,
                    chatId = request.chatId,
                    userMessageId = request.userMessageId,
                )
            )
        }

        loggingPort.info(
            TAG,
            "Executing extract tool provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} urls=${urls.size} extractDepth=$extractDepth format=$format"
        )

        val resultJson = tavilySearchRepository.extract(
            urls = urls,
            extractDepth = extractDepth,
            format = format,
        )

        // Mark each URL as extracted in the DAO
        for (url in urls) {
            try {
                tavilySourceDao.markExtracted(url)
            } catch (e: Exception) {
                loggingPort.warning(TAG, "Failed to mark source as extracted for url=$url: ${e.message}")
            }
        }

        loggingPort.info(
            TAG,
            "Extract tool execution complete provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} resultChars=${resultJson.length}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.TAVILY_EXTRACT.name) {
            "Unsupported tool: $toolName"
        }
    }

    private fun extractOptionalString(argumentsJson: String, fieldName: String): String? {
        try {
            val json = JSONObject(argumentsJson)
            val value = json.optString(fieldName, "")
            if (value.isNotBlank()) {
                return value.trim()
            }
        } catch (e: JSONException) {
            // Return null if parsing fails
        }
        return null
    }
}