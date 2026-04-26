package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.SearchChatHistoryParams
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.EmbeddingEnginePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class SearchHistoryMessageResult(
    val message_id: String,
    val chat_id: String,
    val chat_name: String,
    val role: String,
    val content: String,
    val created_at: Long?,
    val match_type: String
)

@Serializable
private data class SearchHistoryResult(
    val queries: List<String>,
    val total_matched: Int,
    val returned_results: Int,
    val messages: List<SearchHistoryMessageResult>
)

@Singleton
class SearchChatHistoryToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val messageRepository: MessageRepository,
    private val chatRepository: ChatRepository,
    private val embeddingEngine: EmbeddingEnginePort,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "SearchChatHistoryTool"
        private const val MAX_RESULTS = 20
        private const val MAX_CONTENT_LENGTH = 500
        private const val CONTEXT_BEFORE = 2
        private const val CONTEXT_AFTER = 2
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.SEARCH_CHAT_HISTORY.name) {
            "Unsupported tool: ${request.toolName}"
        }
        return try {
            executeInternal(request)
        } catch (e: Exception) {
            loggingPort.error(TAG, "search_chat_history FAILED provider=${request.provider} modelType=${request.modelType} arguments=${request.argumentsJson} error=${e.message}", e)
            throw e
        }
    }

    private suspend fun executeInternal(request: ToolCallRequest): ToolExecutionResult {
        val params = request.parameters as SearchChatHistoryParams
        val queries = params.queries.filter { it.isNotBlank() }
            .ifEmpty { params.query?.takeIf { it.isNotBlank() }?.let { listOf(it) } }
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Tool argument 'queries' is required")

        loggingPort.info(
            TAG,
            "Executing search_chat_history provider=${request.provider} modelType=${request.modelType} queries=$queries"
        )

        // Find initial matches using semantic search for each query
        val allMatches = mutableListOf<Message>()
        for (query in queries) {
            val queryVector = embeddingEngine.getEmbedding(query)
            allMatches.addAll(messageRepository.searchMessagesAcrossChats(queryVector))
        }
        val initialMatches = allMatches.distinctBy { it.id }
        val totalMatchCount = initialMatches.size

        // For each match, get surrounding context
        val allMessages = mutableMapOf<com.browntowndev.pocketcrew.domain.model.chat.MessageId, Pair<Message, String>>()
        initialMatches.forEach { msg ->
            allMessages[msg.id] = msg to "direct"
        }

        // Add context for top matches
        initialMatches.take(5).forEach { match ->
            val context = messageRepository.getMessagesAround(
                chatId = match.chatId,
                timestamp = match.createdAt ?: 0L,
                before = CONTEXT_BEFORE,
                after = CONTEXT_AFTER,
            )
            context.forEach { msg ->
                if (msg.id !in allMessages) {
                    allMessages[msg.id] = msg to "context"
                }
            }
        }

        val sortedMessages = allMessages.values.sortedWith(compareBy<Pair<Message, String>> { it.first.chatId.value }
            .thenBy { it.first.createdAt ?: Long.MIN_VALUE })

        // Get chat names for results
        val chatIds = sortedMessages.map { it.first.chatId }.distinct()
        val chatNames = chatRepository.getChatsByIds(chatIds).mapValues { it.value.name }

        val resultJson = buildChatHistoryResultJson(queries, totalMatchCount, sortedMessages, chatNames)

        loggingPort.info(
            TAG,
            "search_chat_history complete provider=${request.provider} modelType=${request.modelType} queries=$queries totalMatched=$totalMatchCount returned=${sortedMessages.size}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun buildChatHistoryResultJson(
        queries: List<String>,
        totalMatchCount: Int,
        messages: List<Pair<Message, String>>,
        chatNames: Map<ChatId, String>,
    ): String {
        val mappedMessages = messages.take(MAX_RESULTS).map { (message, matchType) ->
            val contentText = message.content.text
            val truncatedContent = if (contentText.length > MAX_CONTENT_LENGTH) {
                contentText.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                contentText
            }
            SearchHistoryMessageResult(
                message_id = message.id.value,
                chat_id = message.chatId.value,
                chat_name = chatNames[message.chatId] ?: "Unknown",
                role = message.role.name,
                content = truncatedContent,
                created_at = message.createdAt,
                match_type = matchType
            )
        }

        return Json.encodeToString(
            SearchHistoryResult(
                queries = queries,
                total_matched = totalMatchCount,
                returned_results = minOf(messages.size, MAX_RESULTS),
                messages = mappedMessages
            )
        )
    }
}
