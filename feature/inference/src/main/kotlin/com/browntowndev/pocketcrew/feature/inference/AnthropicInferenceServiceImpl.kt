package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.anthropic.client.AnthropicClient
import com.anthropic.models.messages.RawMessageStreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runInterruptible

class AnthropicInferenceServiceImpl(
    private val client: AnthropicClient,
    private val modelId: String,
    private val modelType: ModelType,
    private val baseUrl: String? = null,
    private val loggingPort: LoggingPort
) : LlmInferencePort {

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val STREAM_PREVIEW_CHARS = 120
        private const val TAG = "AnthropicInferenceService"
        private const val PROVIDER = "ANTHROPIC"
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
            loggingPort.error(TAG, "API request failed. ${describeException(e)}", e)
            emit(InferenceEvent.Error(Exception("API Error ($PROVIDER): $errorMsg", e), modelType))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executePrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        val params = AnthropicRequestMapper.mapToMessageParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options
        )

        loggingPort.info(
            TAG,
            "Using Anthropic Messages API. model=$modelId reasoningEffort=${options.reasoningEffort} reasoningBudget=${options.reasoningBudget} maxTokens=${options.maxTokens}"
        )
        logRequest(params)

        client.messages().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var finishedEmitted = false
            var outputTextDeltaCount = 0
            var thinkingTextDeltaCount = 0

            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                val contentBlockStart = event.contentBlockStart()
                if (contentBlockStart.isPresent) {
                    val block = contentBlockStart.get().contentBlock()
                    val startText = block.text().map { it.text() }.orElse("")
                    if (startText.isNotEmpty()) {
                        outputTextDeltaCount++
                        emitEvent(InferenceEvent.PartialResponse(startText, modelType))
                    }

                    val startThinking = block.thinking().map { it.thinking() }.orElse("")
                    if (startThinking.isNotEmpty()) {
                        thinkingTextDeltaCount++
                        emitEvent(InferenceEvent.Thinking(startThinking, modelType))
                    }
                }
                val contentBlockDelta = event.contentBlockDelta()
                if (contentBlockDelta.isPresent) {
                    val deltaEvent = contentBlockDelta.get()
                    val delta = deltaEvent.delta()
                    val textDelta = delta.text()
                    if (textDelta.isPresent) {
                        val text = textDelta.get().text()
                        if (text.isNotEmpty()) {
                            outputTextDeltaCount++
                            emitEvent(InferenceEvent.PartialResponse(text, modelType))
                        }
                    }
                    val thinkingDelta = delta.thinking()
                    if (thinkingDelta.isPresent) {
                        val text = thinkingDelta.get().thinking()
                        if (text.isNotEmpty()) {
                            thinkingTextDeltaCount++
                            emitEvent(InferenceEvent.Thinking(text, modelType))
                        }
                    }
                }
                if (event.messageStop().isPresent) {
                    loggingPort.debug(
                        TAG,
                        "Anthropic stream completed model=$modelId outputTextDeltas=$outputTextDeltaCount thinkingTextDeltas=$thinkingTextDeltaCount"
                    )
                    emitEvent(InferenceEvent.Finished(modelType))
                    finishedEmitted = true
                }
                if (event.messageDelta().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream message delta model=$modelId")
                }
                if (event.contentBlockStart().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream content block start model=$modelId")
                }
                if (event.contentBlockStop().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream content block stop model=$modelId")
                }
                if (event.messageStart().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream message start model=$modelId")
                }
            }

            if (!finishedEmitted) {
                loggingPort.debug(
                    TAG,
                    "Anthropic stream ended without message_stop model=$modelId outputTextDeltas=$outputTextDeltaCount thinkingTextDeltas=$thinkingTextDeltaCount"
                )
                emitEvent(InferenceEvent.Finished(modelType))
            }
        }
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
        systemPrompt: String?
    ): List<ChatMessage> {
        val normalizedPrompt = systemPrompt?.trim()?.takeIf { it.isNotEmpty() } ?: return history
        if (history.any { it.role == Role.SYSTEM && it.content == normalizedPrompt }) {
            return history
        }
        return listOf(ChatMessage(role = Role.SYSTEM, content = normalizedPrompt)) + history
    }

    private fun describeException(throwable: Throwable): String =
        buildString {
            append("exception=")
            append(throwable::class.java.simpleName)
            throwable.message?.takeIf { it.isNotBlank() }?.let {
                append(" message=")
                append(it)
            }
        }

    private fun logRequest(params: com.anthropic.models.messages.MessageCreateParams) {
        loggingPort.debug(
            TAG,
            "Messages API request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${truncateForLogs(params.toString())}"
        )
    }

    private fun truncateForLogs(value: String): String {
        if (value.length <= MAX_LOG_BODY_CHARS) {
            return value
        }
        return value.take(MAX_LOG_BODY_CHARS) + "...<truncated>"
    }

    private fun describeStreamEvent(event: RawMessageStreamEvent): String =
        when {
            event.messageStart().isPresent -> "message_start"
            event.messageDelta().isPresent -> "message_delta"
            event.messageStop().isPresent -> "message_stop"
            event.contentBlockStart().isPresent -> "content_block_start"
            event.contentBlockDelta().isPresent -> "content_block_delta"
            event.contentBlockStop().isPresent -> "content_block_stop"
            else -> "other"
        }
}
