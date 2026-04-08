package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.google.genai.Client
import com.google.genai.ResponseStream
import com.google.genai.types.Content
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
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort,
) : LlmInferencePort {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "GoogleInferenceService"
        private const val PROVIDER = "GOOGLE"
    }

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
            client.models.generateContentStream(modelId, request.contents, request.config)
        }.use { responseStream ->
            streamResponses(responseStream, emitEvent)
        }
    }

    private suspend fun streamResponses(
        responseStream: ResponseStream<GenerateContentResponse>,
        emitEvent: suspend (InferenceEvent) -> Unit,
    ) {
        val iterator = responseStream.iterator()
        while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
            val response = runInterruptible { iterator.next() }
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
            }
        }

        emitEvent(InferenceEvent.Finished(modelType))
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
