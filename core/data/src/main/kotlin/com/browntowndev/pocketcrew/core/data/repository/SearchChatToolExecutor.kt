package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SearchChatMessageResult(
    val id: String,
    val role: String,
    val content: String,
    val created_at: Long?
)

@Serializable
private data class SearchChatResult(
    val chat_id: String,
    val query: String,
    val total_results: Int,
    val returned_results: Int,
    val messages: List<SearchChatMessageResult>
)

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
        return try {
            executeInternal(request)
        } catch (e: Exception) {
            loggingPort.error(TAG, "search_chat FAILED provider=${request.provider} modelType=${request.modelType} arguments=${request.argumentsJson} error=${e.message}", e)
            throw e
        }
    }

    private suspend fun executeInternal(request: ToolCallRequest): ToolExecutionResult {
        val params = request.parameters as SearchChatParams
        val chatId = params.chat_id
        val query = params.query

        loggingPort.info(
            TAG,
            "Executing search_chat provider=${request.provider} modelType=${request.modelType} chatId=$chatId query=$query"
        )

        val messages = messageRepository.searchMessagesInChat(ChatId(chatId), query)

        val resultJson = buildSearchChatResultJson(chatId, query, messages)

        loggingPort.info(
            TAG,
            "search_chat complete provider=${request.provider} modelType=${request.modelType} chatId=$chatId query=$query results=${messages.size}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun buildSearchChatResultJson(
        chatId: String,
        query: String,
        messages: List<com.browntowndev.pocketcrew.domain.model.chat.Message>,
    ): String {
        val mappedMessages = messages.take(MAX_RESULTS).map { message ->
            val contentText = message.content.text
            val truncatedContent = if (contentText.length > MAX_CONTENT_LENGTH) {
                contentText.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                contentText
            }
            SearchChatMessageResult(
                id = message.id.value,
                role = message.role.name,
                content = truncatedContent,
                created_at = message.createdAt
            )
        }

        return Json.encodeToString(
            SearchChatResult(
                chat_id = chatId,
                query = query,
                total_results = messages.size,
                returned_results = minOf(messages.size, MAX_RESULTS),
                messages = mappedMessages
            )
        )
    }
}
