package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.openai.client.OpenAIClient
import com.openai.errors.BadRequestException
import com.openai.errors.OpenAIServiceException
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.openai.models.responses.ResponseCreateParams

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn

class ApiInferenceServiceImpl(
    private val client: OpenAIClient,
    private val modelId: String,
    private val provider: String, // E.g., "OPENAI", "ANTHROPIC"
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort
) : LlmInferencePort {

    companion object {
        private const val TAG = "ApiInferenceService"
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val XAI_MULTI_AGENT_MODEL = "grok-4.20-multi-agent"
    }

    private val conversationHistory = mutableListOf<ChatMessage>()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)
    }

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean
    ): Flow<InferenceEvent> = flow {
        try {
            val xaiMultiAgentModel = usesXaiCompatibleEndpoint() && modelId.startsWith(XAI_MULTI_AGENT_MODEL)
            val requestHistory = buildRequestHistory(options)

            // First try using the responses API
            val responseParams = OpenAiRequestMapper.mapToResponseParams(
                modelId = modelId,
                prompt = prompt,
                history = requestHistory,
                options = options
            )

            val skipResponsesApi = usesXaiCompatibleEndpoint() && !xaiMultiAgentModel
            if (skipResponsesApi) {
                loggingPort.info(
                    TAG,
                    "Skipping Responses API for xAI-compatible endpoint. provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"}"
                )
            }

            var useFallback = skipResponsesApi
            if (!skipResponsesApi) {
                logResponsesRequest(responseParams)
                try {
                    if (xaiMultiAgentModel) {
                        val response = client.responses().create(responseParams)
                        val apiError = response.error().map { it.message() }.orElse(null)
                        if (!apiError.isNullOrBlank()) {
                            throw RuntimeException("API Error: $apiError")
                        }

                        val outputText = extractResponseOutputText(response)
                        if (outputText.isNotBlank()) {
                            emit(InferenceEvent.PartialResponse(outputText, modelType))
                        }
                        emit(InferenceEvent.Finished(modelType))
                    } else {
                        client.responses().createStreaming(responseParams).use { streamResponse ->
                            val iterator = streamResponse.stream().iterator()
                            var finishedEmitted = false
                            while (iterator.hasNext()) {
                                val event = iterator.next()
                                if (event.isOutputTextDelta()) {
                                    val text = event.outputTextDelta().get().delta()
                                    emit(InferenceEvent.PartialResponse(text, modelType))
                                } else if (event.isReasoningTextDelta()) {
                                    val text = event.reasoningTextDelta().get().delta()
                                    emit(InferenceEvent.Thinking(text, modelType))
                                } else if (event.isCompleted()) {
                                    emit(InferenceEvent.Finished(modelType))
                                    finishedEmitted = true
                                } else if (event.isFailed()) {
                                    val failedEvent = event.failed().get()
                                    val errorMsg = failedEvent.response().error().map { it.message() }.orElse("Unknown API error")
                                    throw RuntimeException("API Error: $errorMsg")
                                } else if (event.isError()) {
                                    val errorEvent = event.error().get()
                                    val errorMsg = errorEvent.message()
                                    throw RuntimeException("Stream Error: $errorMsg")
                                }
                            }
                            if (!finishedEmitted) {
                                emit(InferenceEvent.Finished(modelType))
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is BadRequestException || e.message?.contains("400") == true || e.message?.contains("Bad Request") == true) {
                        if (xaiMultiAgentModel) {
                            loggingPort.error(
                                TAG,
                                "Responses API rejected xAI multi-agent request. Chat fallback is disabled for this model. ${describeException(e)}",
                                e
                            )
                            throw e
                        } else {
                            loggingPort.warning(
                                TAG,
                                "Responses API rejected request. Falling back to chat completions. ${describeException(e)}"
                            )
                            useFallback = true
                        }
                    } else {
                        throw e
                    }
                }
            }

            if (useFallback) {
                // Fallback to chat completions API
                val chatParams = OpenAiRequestMapper.mapToChatCompletionParams(
                    modelId = modelId,
                    prompt = prompt,
                    history = requestHistory,
                    options = options
                )

                logChatRequest(chatParams)
                client.chat().completions().createStreaming(chatParams).use { streamResponse ->
                    val iterator = streamResponse.stream().iterator()
                    var finishedEmitted = false
                    while (iterator.hasNext()) {
                        val event = iterator.next()
                        val choices = event.choices()
                        if (choices.isNotEmpty()) {
                            val choice = choices[0]
                            val delta = choice.delta()
                            
                            if (delta.content().isPresent) {
                                val text = delta.content().get()
                                if (text.isNotEmpty()) {
                                    emit(InferenceEvent.PartialResponse(text, modelType))
                                }
                            }
                            
                            if (choice.finishReason().isPresent) {
                                emit(InferenceEvent.Finished(modelType))
                                finishedEmitted = true
                            }
                        }
                    }
                    
                    if (!finishedEmitted) {
                        emit(InferenceEvent.Finished(modelType))
                    }
                }
            }
            
            // Append the new messages to history if we're keeping it
            conversationHistory.add(ChatMessage(com.browntowndev.pocketcrew.domain.model.chat.Role.USER, prompt))
            
            if (closeConversation) {
                closeSession()
            }
        } catch (e: Exception) {
            var errorMsg = e.message ?: "Unknown error"
            if (e is OpenAIServiceException) {
                val bodyStr = serializeBody(e.body())
                if (bodyStr.isNotBlank() && !errorMsg.contains(bodyStr)) {
                    errorMsg += " | Body: $bodyStr"
                }
            }
            loggingPort.error(TAG, "API request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(Exception("API Error ($provider): $errorMsg", e), modelType))
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }

    private fun usesXaiCompatibleEndpoint(): Boolean =
        baseUrl?.contains("api.x.ai", ignoreCase = true) == true

    private fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            TAG,
            "Prepared request history provider=$provider model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}"
        )
        return historyWithSystemPrompt
    }

    private fun logResponsesRequest(params: ResponseCreateParams) {
        loggingPort.debug(
            TAG,
            "Responses API request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(serializeBody(params._body()))}"
        )
    }

    private fun logChatRequest(params: ChatCompletionCreateParams) {
        loggingPort.debug(
            TAG,
            "Chat Completions request provider=$provider model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(serializeBody(params._body()))}"
        )
    }

    private fun describeException(throwable: Throwable): String {
        val baseMessage = buildString {
            append("exception=")
            append(throwable::class.java.simpleName)
            throwable.message?.takeIf { it.isNotBlank() }?.let {
                append(" message=")
                append(it)
            }
        }

        if (throwable !is OpenAIServiceException) {
            return baseMessage
        }

        return buildString {
            append(baseMessage)
            append(" status=")
            append(throwable.statusCode())
            throwable.code().orElse(null)?.let {
                append(" code=")
                append(it)
            }
            throwable.type().orElse(null)?.let {
                append(" type=")
                append(it)
            }
            throwable.param().orElse(null)?.let {
                append(" param=")
                append(it)
            }
            val body = serializeBody(throwable.body())
            if (body.isNotBlank()) {
                append(" body=")
                append(truncateForLogs(body))
            }
        }
    }

    private fun serializeBody(body: Any): String = body.toString()

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

    private fun extractResponseOutputText(response: com.openai.models.responses.Response): String =
        response.output()
            .asSequence()
            .filter { it.isMessage() }
            .map { it.asMessage() }
            .flatMap { message -> message.content().asSequence() }
            .filter { it.isOutputText() }
            .joinToString(separator = "") { it.asOutputText().text() }

    internal fun mergeSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }
}
