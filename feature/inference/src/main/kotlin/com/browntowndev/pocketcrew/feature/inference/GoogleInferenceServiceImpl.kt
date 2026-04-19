package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.ContextExceededResult
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.util.ContextWindowPlanner
import com.browntowndev.pocketcrew.domain.util.ToolContextBudget
import com.browntowndev.pocketcrew.domain.util.JTokkitTokenCounter
import com.browntowndev.pocketcrew.domain.util.NativeToolResultFormatter
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.google.genai.types.Content
import com.google.genai.types.FunctionCall
import com.google.genai.types.Part
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

class GoogleInferenceServiceImpl(
    private val sdkClient: GoogleGenAiSdkClient,
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
    val orchestrator: LlmToolingOrchestrator,
) : LlmInferencePort {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
    }

    private val conversationHistory = CopyOnWriteArrayList<ChatMessage>()

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

        logImagePayloads(options)

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

        sdkClient.generateContentStream(
            modelId = modelId,
            contents = request.contents,
            config = request.config,
            emitEvent = emitEvent
        )
        emitEvent(InferenceEvent.Finished(modelType))
    }

    private suspend fun executeToolingPrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        logImagePayloads(options)
        val request = GoogleRequestMapper.mapToGenerateContentRequest(
            prompt = prompt,
            history = requestHistory,
            options = options,
        )

        val toolLoopPayloads = mutableListOf<String>()
        try {
            orchestrator.execute<List<Content>, GoogleSdkResult>(
                providerName = PROVIDER,
                initialParams = request.contents,
                tag = TAG,
                maxToolCalls = options.maxToolCalls,
                onInferencePass = { params, allowToolCall ->

                    sdkClient.generateContentStream(
                        modelId = modelId,
                        contents = params,
                        config = request.config,
                        allowToolCall = allowToolCall,
                        emitEvent = emitEvent
                    )
                },
                onToolCallDetected = { response ->
                    response.functionCall?.let { functionCall ->
                        listOf(
                            ToolCallRequest(
                                toolName = functionCall.name().orElseThrow {
                                    IllegalStateException("Google tool execution failed before final response")
                                },
                                argumentsJson = ToolEnvelopeParser.buildArgumentsJson(functionCall.args().orElse(emptyMap())),
                                provider = PROVIDER,
                                modelType = modelType,
                                chatId = options.chatId,
                                userMessageId = options.userMessageId,
                            )
                        )
                    } ?: emptyList()
                },
                onToolResultsMapped = { params, response, results ->
                    // Truncate large tool results when context window is known
                    val truncationAvailableTokens = options.contextWindow?.let { cw ->
                        val budget = ContextWindowPlanner.budgetFor(
                            contextWindowTokens = cw,
                            options = options,
                            modelId = modelId,
                            tokenCounter = JTokkitTokenCounter,
                        )
                        val usedTokens = ContextWindowPlanner.estimatePromptTokens(
                            history = requestHistory,
                            systemPrompt = null,
                            currentPrompt = prompt,
                            toolResultPayloads = toolLoopPayloads,
                            modelId = modelId,
                            tokenCounter = JTokkitTokenCounter,
                        ) ?: 0
                        (budget.usablePromptTokens - usedTokens).coerceAtLeast(0)
                    }
                    val functionResponses = results.map { (toolCall, resultJson) ->
                        val truncatedResult = if (truncationAvailableTokens != null && truncationAvailableTokens > 0) {
                            NativeToolResultFormatter.truncateForApiContext(
                                resultJson = resultJson,
                                availableTokens = maxOf(truncationAvailableTokens / results.size.coerceAtLeast(1), 100),
                                tokenCounter = JTokkitTokenCounter,
                                modelId = modelId,
                            )
                        } else {
                            resultJson
                        }
                        // Google expects one part per function response in a user-role Content
                        // If they were parallel, we'd still have the initial function call names
                        // For now, we assume the model emitted only one or we map each by its toolName.
                        buildFunctionResponsePart(toolCall.toolName, truncatedResult)
                    }

                    toolLoopPayloads += functionResponses.map { it.toString() }
                    val contextExceeded = estimateContextExceeded(
                        requestHistory = requestHistory,
                        prompt = prompt,
                        options = options,
                        toolResultPayloads = toolLoopPayloads,
                    ).contextExceeded

                    params + listOfNotNull(
                        response.assistantContent,
                        Content.builder()
                            .role("user")
                            .parts(functionResponses)
                            .build(),
                        stopToolsWarningContent().takeIf { contextExceeded },
                    )
                },
                onContextExceeded = { params, results ->
                    estimateContextExceeded(
                        requestHistory = requestHistory,
                        prompt = prompt,
                        options = options,
                        toolResultPayloads = toolLoopPayloads,
                    )
                },
                onToolResult = { toolCall, resultJson ->
                    if (toolCall.toolName == ToolDefinition.TAVILY_WEB_SEARCH.name) {
                        val assistantMessageId = options.assistantMessageId
                        if (assistantMessageId != null) {
                            val sources = com.browntowndev.pocketcrew.domain.util.TavilyResultParser.parse(assistantMessageId, resultJson)
                            if (sources.isNotEmpty()) {
                                emitEvent(InferenceEvent.TavilyResults(sources, modelType))
                            }
                        }
                    }
                },
                onFinished = { _, _, _ ->
                    emitEvent(InferenceEvent.Finished(modelType))
                }
            )
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Google tool execution failed before final response", e)
        }
    }

    /**
     * Estimates whether the request plus accumulated tool results exceeds the context window threshold.
     * API providers are stateless between requests, so summarization happens before the request is sent;
     * mid-loop history rebuilding is possible but not yet implemented for API providers.
     * Returns a [ContextExceededResult] indicating whether context is exceeded after
     * any mid-loop history rebuilding.
     */
    private fun estimateContextExceeded(
        requestHistory: List<ChatMessage>,
        prompt: String,
        options: GenerationOptions,
        toolResultPayloads: List<String>,
    ): ContextExceededResult<List<Content>> {
        val contextWindow = options.contextWindow ?: return ContextExceededResult(false)
        val contextExceeded = ToolContextBudget.isApiContextExceeded(
            contextWindowTokens = contextWindow,
            history = requestHistory,
            systemPrompt = null,
            currentPrompt = prompt,
            toolResultPayloads = toolResultPayloads,
            options = options,
            modelId = modelId,
        )
        if (contextExceeded) {
            loggingPort.warning(
                TAG,
                "Google context exceeded mid-loop: window=$contextWindow"
            )
        }
        return ContextExceededResult(contextExceeded)
    }

    private fun stopToolsWarningContent(): Content =
        Content.builder()
            .role("user")
            .parts(listOf(Part.fromText(ContextWindowPlanner.STOP_TOOLS_WARNING)))
            .build()

    private fun buildFunctionResponsePart(
        toolName: String,
        resultJson: String,
    ): Part =
        Part.fromFunctionResponse(
            toolName,
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

    private fun logImagePayloads(options: GenerationOptions) {
        if (options.imageUris.isEmpty()) {
            return
        }
        val payloads = runCatching {
            ImagePayloads.fromUris(options.imageUris)
        }.getOrElse { error ->
            loggingPort.error(
                TAG,
                "Failed to load image payloads provider=$PROVIDER model=$modelId message=${error.message}",
                error,
            )
            return
        }

        val details = payloads.joinToString(separator = "; ") { payload ->
            "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${payload.sha256.take(8)}"
        }
        loggingPort.info(
            TAG,
            "Image payloads provider=$PROVIDER model=$modelId count=${payloads.size} $details"
        )
    }
}
