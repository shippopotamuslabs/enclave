package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.inference.ToolExecutorPort
import com.google.genai.Client
import com.google.genai.Models
import com.google.genai.ResponseStream
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Part
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible

class GoogleInferenceServiceImpl(
    private val client: Client,
    private val explicitModelsApi: Models? = null,
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
    internal val toolExecutor: ToolExecutorPort? = null,
) : LlmInferencePort {

    private data class StreamedGoogleResponse(
        val emittedAny: Boolean,
        val functionCall: FunctionCall?,
        val assistantContent: Content?,
    )

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
    }

    private val modelsApi: Models
        get() = explicitModelsApi ?: client.models

    private val conversationHistory = mutableListOf<ChatMessage>()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0), closeConversation)
    }

    override fun sendPrompt(
        prompt: String,
        options: GenerationOptions,
        closeConversation: Boolean,
    ): Flow<InferenceEvent> = flow {
        try {
            val requestHistory = buildRequestHistory(options)
            loggingPort.debug(
                TAG,
                "sendPrompt start provider=$PROVIDER model=$modelId modelType=$modelType closeConversation=$closeConversation reasoningBudget=${options.reasoningBudget} reasoningEffort=${options.reasoningEffort} maxTokens=${options.maxTokens}"
            )
            executePrompt(prompt, options, requestHistory) { emit(it) }

            conversationHistory.add(ChatMessage(Role.USER, prompt))
            if (closeConversation) {
                closeSession()
            }
        } catch (e: CancellationException) {
            loggingPort.debug(TAG, "sendPrompt cancelled provider=$PROVIDER model=$modelId")
            throw e
        } catch (e: Exception) {
            if (e is IllegalArgumentException || e is IllegalStateException) {
                loggingPort.error(TAG, "Tool request failed. exception=${e::class.java.simpleName} message=${e.message}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            }
            val errorMsg = e.message ?: "Unknown error"
            loggingPort.error(TAG, "API request failed. exception=${e::class.java.simpleName} message=$errorMsg", e)
            emit(InferenceEvent.Error(Exception("API Error ($PROVIDER): $errorMsg", e), modelType))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
            executeToolingPrompt(prompt, options, requestHistory, emitEvent)
            return
        }

        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = prompt,
            history = requestHistory,
            options = options,
        )

        loggingPort.info(
            TAG,
            "Using Google GenAI SDK. model=$modelId reasoningBudget=${options.reasoningBudget} maxTokens=${options.maxTokens}"
        )
        loggingPort.debug(
            TAG,
            "GenerateContent request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(request.config.toString())}"
        )

        runInterruptible {
            modelsApi.generateContentStream(modelId, request.contents, request.config)
        }.use { responseStream ->
            streamResponses(responseStream, emitEvent)
        }
    }

    private suspend fun executeToolingPrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        val executor = requireNotNull(toolExecutor) {
            "Tool executor not configured for provider=$PROVIDER"
        }

        try {
            val request = GoogleRequestMapper.mapToGenerateContentRequest(
                prompt = prompt,
                history = requestHistory,
                options = options,
            )
            loggingPort.debug(
                TAG,
                "GenerateContent tool request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(request.config.toString())}"
            )

            val initialStream = runInterruptible {
                modelsApi.generateContentStream(modelId, request.contents, request.config)
            }
            val initialResponse = initialStream.use { responseStream ->
                streamResponses(responseStream, emitEvent)
            }
            val functionCall = initialResponse.functionCall
            if (functionCall == null) {
                loggingPort.info(
                    TAG,
                    "Tool loop complete without tool call provider=$PROVIDER model=$modelId modelType=$modelType"
                )
                return
            }

            val toolRequest = ToolCallRequest(
                toolName = functionCall.name().orElseThrow {
                    IllegalStateException("Google tool execution failed before final response")
                },
                argumentsJson = SearchToolSupport.buildArgumentsJson(functionCall.args().orElse(emptyMap())),
                provider = PROVIDER,
                modelType = modelType,
            )
            SearchToolSupport.requireSupportedTool(toolRequest.toolName)
            val query = SearchToolSupport.extractRequiredQuery(toolRequest.argumentsJson)
            loggingPort.info(
                TAG,
                "Tool call detected provider=$PROVIDER model=$modelId tool=${toolRequest.toolName} query=$query"
            )
            val toolResult = executor.execute(toolRequest)
            loggingPort.info(
                TAG,
                "Tool call completed provider=$PROVIDER model=$modelId tool=${toolRequest.toolName} resultChars=${toolResult.resultJson.length}"
            )

            val followUpContents = request.contents + listOfNotNull(
                initialResponse.assistantContent,
                Content.builder()
                    .role("user")
                    .parts(
                        listOf(
                            buildFunctionResponsePart(functionCall, toolResult.resultJson)
                        )
                    )
                    .build()
            )

            val followUpStream = runInterruptible {
                modelsApi.generateContentStream(modelId, followUpContents, request.config)
            }
            val followUpResponse = followUpStream.use { responseStream ->
                streamResponses(responseStream, emitEvent)
            }
            if (followUpResponse.functionCall != null) {
                loggingPort.warning(
                    TAG,
                    "Recursive tool call detected provider=$PROVIDER model=$modelId"
                )
                throw IllegalStateException("Search skill recursion limit exceeded")
            }

            if (!followUpResponse.emittedAny) {
                throw IllegalStateException("Google tool execution failed before final response")
            }
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Google tool execution failed before final response", e)
        }
    }

    private suspend fun streamResponses(
        responseStream: ResponseStream<GenerateContentResponse>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): StreamedGoogleResponse {
        val iterator = responseStream.iterator()
        var emittedAny = false
        var lastFunctionCall: FunctionCall? = null
        var assistantContent: Content? = null
        while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
            val response = runInterruptible { iterator.next() }
            response.candidates().orElse(emptyList()).forEach { candidate ->
                val content = candidate.content().orElse(null)
                if (content != null) {
                    assistantContent = content
                    emittedAny = emitContentParts(content, emitEvent) || emittedAny
                }
            }
            lastFunctionCall = response.functionCalls()?.firstOrNull() ?: lastFunctionCall

            val responseText = response.text().orEmpty()
            if (responseText.isNotBlank() && !emittedAny) {
                emitEvent(InferenceEvent.PartialResponse(responseText, modelType))
                emittedAny = true
            }
        }

        emitEvent(InferenceEvent.Finished(modelType))
        return StreamedGoogleResponse(
            emittedAny = emittedAny,
            functionCall = lastFunctionCall,
            assistantContent = assistantContent,
        )
    }

    private suspend fun emitContentParts(
        content: Content,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): Boolean {
        var emitted = false
        content.parts().orElse(emptyList()).forEach { part ->
            val text = part.text().orElse("")
            if (text.isNotBlank()) {
                val isThought = part.thought().orElse(false)
                emitted = true
                if (isThought) {
                    emitEvent(InferenceEvent.Thinking(text, modelType))
                } else {
                    emitEvent(InferenceEvent.PartialResponse(text, modelType))
                }
            }
        }
        return emitted
    }

    private suspend fun emitResponseText(
        response: GenerateContentResponse,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ): Boolean {
        var emittedAny = false
        response.candidates().orElse(emptyList()).forEach { candidate ->
            val content = candidate.content().orElse(null)
            if (content != null) {
                emittedAny = emitContentParts(content, emitEvent) || emittedAny
            }
        }

        val responseText = response.text().orEmpty()
        if (responseText.isNotBlank() && !emittedAny) {
            emitEvent(InferenceEvent.PartialResponse(responseText, modelType))
            emittedAny = true
        }
        return emittedAny
    }

    private fun buildFunctionResponsePart(
        functionCall: FunctionCall,
        resultJson: String,
    ): Part =
        Part.fromFunctionResponse(
            functionCall.name().orElseThrow {
                IllegalStateException("Google tool execution failed before final response")
            },
            mapOf("output" to resultJson),
        )

    override suspend fun setHistory(messages: List<ChatMessage>) {
        conversationHistory.clear()
        conversationHistory.addAll(messages)
    }

    override suspend fun closeSession() {
        conversationHistory.clear()
    }

    private fun buildRequestHistory(options: GenerationOptions): List<ChatMessage> {
        val historyWithSystemPrompt = mergeSystemPrompt(conversationHistory, options.systemPrompt)
        loggingPort.debug(
            TAG,
            "Prepared request history provider=$PROVIDER model=$modelId modelType=$modelType historyCount=${historyWithSystemPrompt.size} systemPromptIncluded=${historyWithSystemPrompt.firstOrNull()?.role == Role.SYSTEM}"
        )
        return historyWithSystemPrompt
    }

    private fun mergeSystemPrompt(
        history: List<ChatMessage>,
        systemPrompt: String?,
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }
}
