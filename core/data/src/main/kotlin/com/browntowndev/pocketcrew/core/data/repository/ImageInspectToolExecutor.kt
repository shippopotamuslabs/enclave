package com.browntowndev.pocketcrew.core.data.repository
import com.browntowndev.pocketcrew.domain.model.chat.ResolvedImageTarget
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.model.inference.ToolExecutionResult
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.usecase.chat.AnalyzeImageUseCase
import org.json.JSONException
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageInspectToolExecutor @Inject constructor(
    private val loggingPort: LoggingPort,
    private val messageRepository: MessageRepository,
    private val analyzeImageUseCase: AnalyzeImageUseCase,
) : ToolExecutorPort {

    companion object {
        private const val TAG = "ImageInspectTool"
    }

    override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
        requireSupportedTool(request.toolName)
        val question = extractRequiredQuestion(request.argumentsJson)

        loggingPort.info(
            TAG,
            "Executing image inspect tool provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} question=$question"
        )

        val chatId = request.chatId
        if (chatId == null) {
            loggingPort.warning(TAG, "Image inspect tool invoked without chat context (chatId=$chatId, userMessageId=${request.userMessageId})")
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = JSONObject()
                    .put("error", "no context")
                    .put("message", "Image inspection requires chat context that is not available in this execution path.")
                    .toString(),
            )
        }

        val resolvedTarget = resolveTarget(
            chatId = chatId,
            userMessageId = request.userMessageId,
        )

        if (resolvedTarget == null) {
            loggingPort.warning(TAG, "Image inspect tool invoked but no resolvable image exists")
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = JSONObject()
                    .put("error", "no image")
                    .put("message", "No previously attached image is available in this chat.")
                    .toString(),
            )
        }

        loggingPort.info(
            TAG,
            "Image inspect tool resolved target chatId=${chatId.value} userMessageId=${resolvedTarget.userMessageId.value} imageUri=${resolvedTarget.imageUri}"
        )

        val analysis = try {
            analyzeImageUseCase(resolvedTarget.imageUri, question)
        } catch (t: Throwable) {
            val rootCause = t.rootCause()
            loggingPort.error(
                TAG,
                "Image inspect tool execution failed provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                rootCause
            )
            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = buildErrorJson(resolvedTarget, question, rootCause),
            )
        }

        loggingPort.info(
            TAG,
            "Image inspect tool execution complete provider=${request.provider} modelType=${request.modelType} tool=${request.toolName} resultChars=${analysis.length}"
        )

        return ToolExecutionResult(
            toolName = request.toolName,
            resultJson = buildResultJson(resolvedTarget, question, analysis),
        )
    }

    private suspend fun resolveTarget(
        chatId: com.browntowndev.pocketcrew.domain.model.chat.ChatId,
        userMessageId: com.browntowndev.pocketcrew.domain.model.chat.MessageId?,
    ): ResolvedImageTarget? {
        if (userMessageId != null) {
            val resolved = messageRepository.resolveLatestImageBearingUserMessage(
                chatId = chatId,
                currentUserMessageId = userMessageId,
            )
            if (resolved != null) {
                return resolved
            }
        }

        val latestImageMessage = messageRepository.getMessagesForChat(chatId)
            .filter { message ->
                message.role == Role.USER && message.content.imageUri != null
            }
            .maxByOrNull { it.createdAt }

        return latestImageMessage?.let { message ->
            ResolvedImageTarget(
                userMessageId = message.id,
                imageUri = requireNotNull(message.content.imageUri),
            )
        }
    }

    private fun requireSupportedTool(toolName: String) {
        require(toolName == ToolDefinition.ATTACHED_IMAGE_INSPECT.name) {
            "Unsupported tool: $toolName"
        }
    }

    private fun extractRequiredQuestion(argumentsJson: String): String {
        try {
            return JSONObject(argumentsJson)
                .optString("question", "")
                .trim()
                .takeIf(String::isNotEmpty)
                ?: throw IllegalArgumentException("Tool argument 'question' is required")
        } catch (error: JSONException) {
            throw IllegalArgumentException("Tool argument 'question' is required", error)
        }
    }

    private fun buildResultJson(
        target: ResolvedImageTarget,
        question: String,
        analysis: String,
    ): String = JSONObject()
        .put("resolved_message_id", target.userMessageId.value)
        .put("image_uri", target.imageUri)
        .put("question", question)
        .put("analysis", analysis)
        .toString()

    private fun buildErrorJson(
        target: ResolvedImageTarget,
        question: String,
        throwable: Throwable,
    ): String = JSONObject()
        .put("error", "vision_tool_failed")
        .put("resolved_message_id", target.userMessageId.value)
        .put("image_uri", target.imageUri)
        .put("question", question)
        .put("exception", throwable::class.java.simpleName)
        .put("message", throwable.message ?: "Unknown error")
        .toString()

    private fun Throwable.rootCause(): Throwable {
        val cause = when (this) {
            is InvocationTargetException -> targetException ?: cause
            else -> cause
        }
        return if (cause == null || cause === this) this else cause.rootCause()
    }
}
