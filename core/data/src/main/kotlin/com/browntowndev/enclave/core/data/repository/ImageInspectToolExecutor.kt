package com.browntowndev.enclave.core.data.repository

import com.browntowndev.enclave.domain.model.chat.ResolvedImageTarget
import com.browntowndev.enclave.domain.model.chat.Role
import com.browntowndev.enclave.domain.model.inference.AttachedImageInspectParams
import com.browntowndev.enclave.domain.model.inference.ToolCallRequest
import com.browntowndev.enclave.domain.model.inference.ToolDefinition
import com.browntowndev.enclave.domain.model.inference.ToolExecutionResult
import com.browntowndev.enclave.domain.port.inference.LoggingPort
import com.browntowndev.enclave.domain.port.inference.ToolExecutorPort
import com.browntowndev.enclave.domain.port.repository.MessageRepository
import com.browntowndev.enclave.domain.usecase.chat.AnalyzeImageUseCase
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.reflect.InvocationTargetException
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class ImageInspectResult(
    val resolved_message_id: String,
    val image_uris: List<String>,
    val question: String,
    val analysis: String,
)

@Serializable
private data class ImageInspectError(
    val error: String,
    val resolved_message_id: String,
    val image_uris: List<String>,
    val question: String,
    val exception: String,
    val message: String,
)

@Singleton
class ImageInspectToolExecutor
    @Inject
    constructor(
        private val loggingPort: LoggingPort,
        private val messageRepository: MessageRepository,
        private val analyzeImageUseCase: AnalyzeImageUseCase,
    ) : ToolExecutorPort {
        companion object {
            private const val TAG = "ImageInspectTool"
        }

        override suspend fun execute(request: ToolCallRequest): ToolExecutionResult {
            requireSupportedTool(request.toolName)
            val params = request.parameters as AttachedImageInspectParams
            val question = params.question

            loggingPort.info(
                TAG,
                "Executing image inspect tool provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} question=$question",
            )

            val chatId = request.chatId
            if (chatId == null) {
                loggingPort.warning(
                    TAG,
                    "Image inspect tool invoked without chat context " +
                        "(chatId=$chatId, userMessageId=${request.userMessageId})",
                )
                return ToolExecutionResult(
                    toolName = request.toolName,
                    resultJson =
                        """{"error": "no context", """ +
                            """"message": "Image inspection requires chat context that is not available in this execution path."}""",
                )
            }

            val resolvedTarget =
                resolveTarget(
                    chatId = chatId,
                    userMessageId = request.userMessageId,
                )

            if (resolvedTarget == null) {
                loggingPort.warning(TAG, "Image inspect tool invoked but no resolvable image exists")
                return ToolExecutionResult(
                    toolName = request.toolName,
                    resultJson = """{"error": "no image", "message": "No previously attached image is available in this chat."}""",
                )
            }

            loggingPort.info(
                TAG,
                "Image inspect tool resolved target chatId=${chatId.value} " +
                    "userMessageId=${resolvedTarget.userMessageId.value} " +
                    "imageUrisCount=${resolvedTarget.imageUris.size}",
            )

            val analysis =
                try {
                    analyzeImageUseCase(resolvedTarget.imageUris, question)
                } catch (t: Throwable) {
                    val rootCause = t.rootCause()
                    loggingPort.error(
                        TAG,
                        "Image inspect tool execution failed provider=${request.provider} " +
                            "modelType=${request.modelType} tool=${request.toolName} " +
                            "cause=${rootCause::class.java.simpleName}: ${rootCause.message}",
                        rootCause,
                    )
                    return ToolExecutionResult(
                        toolName = request.toolName,
                        resultJson = buildErrorJson(resolvedTarget, question, rootCause),
                    )
                }

            loggingPort.info(
                TAG,
                "Image inspect tool execution complete provider=${request.provider} " +
                    "modelType=${request.modelType} tool=${request.toolName} " +
                    "resultChars=${analysis.length}",
            )

            return ToolExecutionResult(
                toolName = request.toolName,
                resultJson = buildResultJson(resolvedTarget, question, analysis),
            )
        }

        private suspend fun resolveTarget(
            chatId: com.browntowndev.enclave.domain.model.chat.ChatId,
            userMessageId: com.browntowndev.enclave.domain.model.chat.MessageId?,
        ): ResolvedImageTarget? {
            if (userMessageId != null) {
                val resolved =
                    messageRepository.resolveLatestImageBearingUserMessage(
                        chatId = chatId,
                        currentUserMessageId = userMessageId,
                    )
                if (resolved != null) {
                    return resolved
                }
            }

            val latestImageMessage =
                messageRepository
                    .getMessagesForChat(chatId)
                    .filter { message ->
                        message.role == Role.USER && message.content.imageUris.isNotEmpty()
                    }.maxByOrNull { it.createdAt ?: 0L }

            return latestImageMessage?.let { message ->
                ResolvedImageTarget(
                    userMessageId = message.id,
                    imageUris = message.content.imageUris,
                )
            }
        }

        private fun requireSupportedTool(toolName: String) {
            require(toolName == ToolDefinition.ATTACHED_IMAGE_INSPECT.name) {
                "Unsupported tool: $toolName"
            }
        }

        private fun buildResultJson(
            target: ResolvedImageTarget,
            question: String,
            analysis: String,
        ): String =
            Json.encodeToString(
                ImageInspectResult(
                    resolved_message_id = target.userMessageId.value,
                    image_uris = target.imageUris,
                    question = question,
                    analysis = analysis,
                ),
            )

        private fun buildErrorJson(
            target: ResolvedImageTarget,
            question: String,
            throwable: Throwable,
        ): String =
            Json.encodeToString(
                ImageInspectError(
                    error = "vision_tool_failed",
                    resolved_message_id = target.userMessageId.value,
                    image_uris = target.imageUris,
                    question = question,
                    exception = throwable::class.java.simpleName,
                    message = throwable.message ?: "Unknown error",
                ),
            )

        private fun Throwable.rootCause(): Throwable {
            val cause =
                when (this) {
                    is InvocationTargetException -> targetException ?: cause
                    else -> cause
                }
            return if (cause == null || cause === this) this else cause.rootCause()
        }
    }
