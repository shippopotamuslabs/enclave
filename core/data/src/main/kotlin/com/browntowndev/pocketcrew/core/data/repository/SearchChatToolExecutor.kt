package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchChatToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val messageRepository: MessageRepository,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "SearchChatTool"
        private const val MAX_RESULTS = 20
        private const val MAX_CONTENT_LENGTH = 500
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.SEARCH_CHAT.name) {
            "Unsupported tool: ${request.toolName}"
        }
        val chatId = extractRequiredChatId(request.argumentsJson)
        val query = extractRequiredQuery(request.argumentsJson)

        loggingPort.info(
            TAG,
            "Executing search_chat provider=${request.provider} modelType=${request.modelType} chatId=$chatId query=$query"
        )

        val messages = messageRepository.searchMessagesInChat(ChatId(chatId), query)

        val resultJson = buildSearchChatResultJson(chatId, query, messages)

        loggingPort.info(
            TAG,
            "search_chat complete provider=${request.provider} modelType=${request.modelType} chatId=$chatId resultCount=${messages.size} resultChars=${resultJson.length}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun extractRequiredChatId(argumentsJson: String): String {
        try {
            return JSONObject(argumentsJson)
                .optString("chat_id", "")
                .trim()
                .takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Tool argument 'chat_id' is required")
        } catch (e: JSONException) {
            throw IllegalArgumentException("Tool argument 'chat_id' is required", e)
        }
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

    private fun buildSearchChatResultJson(
        chatId: String,
        query: String,
        messages: List<com.browntowndev.pocketcrew.domain.model.chat.Message>,
    ): String {
        val resultsArray = JSONArray()
        messages.take(MAX_RESULTS).forEach { message ->
            val contentText = message.content.text
            val truncatedContent = if (contentText.length > MAX_CONTENT_LENGTH) {
                contentText.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                contentText
            }
            val messageObj = JSONObject().apply {
                put("role", message.role.name.lowercase())
                put("content", truncatedContent)
                message.createdAt?.let { put("timestamp", it) }
            }
            resultsArray.put(messageObj)
        }

        return JSONObject().apply {
            put("chat_id", chatId)
            put("query", query)
            put("total_results", messages.size)
            put("returned_results", minOf(messages.size, MAX_RESULTS))
            put("messages", resultsArray)
        }.toString()
    }
}
