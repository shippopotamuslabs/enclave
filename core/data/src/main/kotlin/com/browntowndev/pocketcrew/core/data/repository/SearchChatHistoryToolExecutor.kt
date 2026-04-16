package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.usecase.chat.SearchChatsUseCase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchChatHistoryToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val searchChatsUseCase: SearchChatsUseCase,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "SearchChatHistoryTool"
        private const val MAX_RESULTS = 10
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.SEARCH_CHAT_HISTORY.name) {
            "Unsupported tool: ${request.toolName}"
        }
        val query = extractRequiredQuery(request.argumentsJson)

        loggingPort.info(
            TAG,
            "Executing search_chat_history provider=${request.provider} modelType=${request.modelType} query=$query"
        )

        val chats = searchChatsUseCase(query).first()

        val resultJson = buildChatHistoryResultJson(query, chats)

        loggingPort.info(
            TAG,
            "search_chat_history complete provider=${request.provider} modelType=${request.modelType} resultCount=${chats.size} resultChars=${resultJson.length}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun extractRequiredQuery(argumentsJson: String): String {
        try {
            return JSONObject(argumentsJson)
                .optString("query", "")
                .trim()
                .takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Tool argument 'query' is required")
        } catch (e: JSONException) {
            throw IllegalArgumentException("Tool argument 'query' is required", e)
        }
    }

    private fun buildChatHistoryResultJson(query: String, chats: List<com.browntowndev.pocketcrew.domain.model.chat.Chat>): String {
        val resultsArray = JSONArray()
        chats.take(MAX_RESULTS).forEach { chat ->
            val chatObj = JSONObject().apply {
                put("chat_id", chat.id.value)
                put("name", chat.name)
                put("last_modified", chat.lastModified.time)
            }
            resultsArray.put(chatObj)
        }

        return JSONObject().apply {
            put("query", query)
            put("total_results", chats.size)
            put("returned_results", minOf(chats.size, MAX_RESULTS))
            put("chats", resultsArray)
        }.toString()
    }
}
