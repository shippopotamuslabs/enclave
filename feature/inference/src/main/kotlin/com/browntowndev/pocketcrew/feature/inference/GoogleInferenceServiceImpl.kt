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
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
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

        try {
            orchestrator.execute(
                providerName = PROVIDER,
                initialParams = request.contents,
                options = options,
                tag = TAG,
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
                    }
                },
                onToolResultMapped = { params, response, resultJson ->
                    val functionCall = response.functionCall ?: throw IllegalStateException("Missing function call in response")
                    params + listOfNotNull(
                        response.assistantContent,
                        Content.builder()
                            .role("user")
                            .parts(
                                listOf(
                                    buildFunctionResponsePart(functionCall, resultJson)
                                )
                            )
                            .build()
                    )
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
