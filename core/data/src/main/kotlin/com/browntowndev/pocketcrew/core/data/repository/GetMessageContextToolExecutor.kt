package com.browntowndev.pocketcrew.core.data.repository

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.GetMessageContextParams
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
private data class ContextMessageResult(
    val message_id: String,
    val role: String,
    val content: String,
    val created_at: Long?,
    val is_anchor: Boolean
)

@Serializable
private data class GetContextResult(
    val anchor_message_id: String,
    val chat_id: String,
    val total_context_messages: Int,
    val messages: List<ContextMessageResult>
)

@Serializable
private data class GetContextError(
    val anchor_message_id: String,
    val error: String,
    val messages: List<ContextMessageResult> = emptyList()
)

@Singleton
class GetMessageContextToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val messageRepository: MessageRepository,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "GetMessageContextTool"
        private const val MAX_BEFORE_AFTER = 20
        private const val MAX_CONTENT_LENGTH = 500
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        require(request.toolName == ToolDefinition.GET_MESSAGE_CONTEXT.name) {
            "Unsupported tool: ${request.toolName}"
        }
        return try {
            executeInternal(request)
        } catch (e: Exception) {
            loggingPort.error(TAG, "get_message_context FAILED provider=${request.provider} modelType=${request.modelType} arguments=${request.argumentsJson} error=${e.message}", e)
            throw e
        }
    }

    private suspend fun executeInternal(request: ToolCallRequest): ToolExecutionResult {
        val params = request.parameters as GetMessageContextParams
        val messageId = params.message_id
        val before = params.before.coerceIn(0, MAX_BEFORE_AFTER)
        val after = params.after.coerceIn(0, MAX_BEFORE_AFTER)

        loggingPort.info(
            TAG,
            "Executing get_message_context provider=${request.provider} modelType=${request.modelType} messageId=$messageId before=$before after=$after"
        )

        // First find the anchor message to get its chat ID and timestamp
        val anchorMessage = messageRepository.getMessageById(MessageId(messageId))
        if (anchorMessage == null) {
            loggingPort.warning(TAG, "get_message_context anchor message not found messageId=$messageId")
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = buildErrorResponse(messageId, "Message not found")
            )
        }

        val timestamp = anchorMessage.createdAt
        if (timestamp == null) {
            loggingPort.warning(TAG, "get_message_context anchor message has no timestamp messageId=$messageId")
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = buildErrorResponse(messageId, "Message has no timestamp")
            )
        }

        // Get surrounding context
        val contextMessages = messageRepository.getMessagesAround(
            chatId = anchorMessage.chatId,
            timestamp = timestamp,
            before = before,
            after = after,
        )

        val resultJson = buildContextResultJson(messageId, anchorMessage, contextMessages)

        loggingPort.info(
            TAG,
            "get_message_context complete provider=${request.provider} modelType=${request.modelType} messageId=$messageId returned=${contextMessages.size}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = resultJson,
        )
    }

    private fun buildContextResultJson(
        messageId: String,
        anchorMessage: com.browntowndev.pocketcrew.domain.model.chat.Message,
        contextMessages: List<com.browntowndev.pocketcrew.domain.model.chat.Message>,
    ): String {
        // Build a combined list and sort chronologically. 
        // The contextMessages usually includes the anchor message if implemented that way, 
        // but let's be safe and distinctBy id.
        val allMessages = (contextMessages + anchorMessage)
            .distinctBy { it.id }
            .sortedBy { it.createdAt ?: 0L }

        val mappedMessages = allMessages.map { message ->
            val contentText = message.content.text
            val truncatedContent = if (contentText.length > MAX_CONTENT_LENGTH) {
                contentText.take(MAX_CONTENT_LENGTH) + "..."
            } else {
                contentText
            }
            ContextMessageResult(
                message_id = message.id.value,
                role = message.role.name,
                content = truncatedContent,
                created_at = message.createdAt,
                is_anchor = message.id.value == messageId
            )
        }

        return Json.encodeToString(
            GetContextResult(
                anchor_message_id = messageId,
                chat_id = anchorMessage.chatId.value,
                total_context_messages = mappedMessages.size,
                messages = mappedMessages
            )
        )
    }

    private fun buildErrorResponse(messageId: String, error: String): String {
        return Json.encodeToString(
            GetContextError(
                anchor_message_id = messageId,
                error = error
            )
        )
    }
}
