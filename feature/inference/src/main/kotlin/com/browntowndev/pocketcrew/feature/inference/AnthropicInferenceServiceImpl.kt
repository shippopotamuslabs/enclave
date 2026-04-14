package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.ToolCallRequest
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.usecase.inference.LlmToolingOrchestrator
import com.browntowndev.pocketcrew.domain.util.ToolEnvelopeParser
import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.RawMessageStreamEvent
import com.anthropic.models.messages.ToolUseBlockParam
import com.anthropic.models.messages.ToolResultBlockParam
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
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
    private val loggingPort: LoggingPort,
    val orchestrator: LlmToolingOrchestrator,
) : LlmInferencePort {

    private data class StreamedAnthropicResponse(
        val emittedAny: Boolean,
        val toolUse: CapturedToolUse?,
    )

    private data class CapturedToolUse(
        val id: String,
        val toolName: String,
        val argumentsJson: String,
    )

    private data class PendingToolUse(
        val id: String,
        val toolName: String,
        val initialInput: JsonValue,
        val streamedInputJson: StringBuilder = StringBuilder(),
    )

    companion object {
        private const val MAX_LOG_BODY_CHARS = 4_000
        private const val TAG = "AnthropicInferenceService"
        private const val PROVIDER = "ANTHROPIC"
        private val TOOL_INPUT_JSON_MAPPER = ObjectMapper()
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
            if (e is IllegalArgumentException || e is IllegalStateException) {
                loggingPort.error(TAG, "Tool request failed. ${describeException(e)}", e)
                emit(InferenceEvent.Error(e, modelType))
                return@flow
            }
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
        if (options.toolingEnabled && options.availableTools.isNotEmpty()) {
            executeToolingPrompt(prompt, options, requestHistory, emitEvent)
            return
        }

        logImagePayloads(options)

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
        streamMessages(
            params = params,
            emitEvent = emitEvent,
        )
        emitEvent(InferenceEvent.Finished(modelType))
    }

    private suspend fun executeToolingPrompt(
        prompt: String,
        options: GenerationOptions,
        requestHistory: List<ChatMessage>,
        emitEvent: suspend (InferenceEvent) -> Unit
    ) {
        logImagePayloads(options)

        val initialParams = AnthropicRequestMapper.mapToMessageParams(
            modelId = modelId,
            prompt = prompt,
            history = requestHistory,
            options = options,
        )

        orchestrator.execute(
            providerName = PROVIDER,
            initialParams = initialParams,
            options = options,
            tag = TAG,
            onInferencePass = { params, allowToolUse ->
                streamMessages(
                    params = params,
                    allowToolUse = allowToolUse,
                    emitEvent = emitEvent,
                )
            },
            onToolCallDetected = { response ->
                response.toolUse?.let { toolUse ->
                    ToolCallRequest(
                        toolName = toolUse.toolName,
                        argumentsJson = toolUse.argumentsJson,
                        provider = PROVIDER,
                        modelType = modelType,
                        chatId = options.chatId,
                        userMessageId = options.userMessageId,
                    )
                }
            },
            onToolResultMapped = { params, response, resultJson ->
                val toolUse =
                    response.toolUse ?: throw IllegalStateException("Missing tool use in response")
                params.toBuilder()
                    .messages(
                        params.messages() + listOf(
                            assistantToolUseMessage(toolUse),
                            MessageParam.builder()
                                .role(MessageParam.Role.USER)
                                .contentOfBlockParams(
                                    listOf(
                                        ContentBlockParam.ofToolResult(
                                            ToolResultBlockParam.builder()
                                                .toolUseId(toolUse.id)
                                                .content(resultJson)
                                                .build()
                                        )
                                    )
                                )
                                .build()
                        )
                    )
                    .build()
            },
            onFinished = { _, _, _ ->
                emitEvent(InferenceEvent.Finished(modelType))
            }
        )
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
            "Messages API request provider=$PROVIDER model=$modelId baseUrl=${baseUrl ?: "<default>"} body=${
                truncateForLogs(
                    params.toString()
                )
            }"
        )
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
            "file=${payload.filename}, mime=${payload.mimeType}, bytes=${payload.byteCount}, sha256=${
                payload.sha256.take(
                    8
                )
            }"
        }
        loggingPort.info(
            TAG,
            "Image payloads provider=$PROVIDER model=$modelId count=${payloads.size} $details"
        )
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

    private suspend fun streamMessages(
        params: MessageCreateParams,
        allowToolUse: Boolean = false,
        emitEvent: suspend (InferenceEvent) -> Unit
    ): StreamedAnthropicResponse {
        logRequest(params)
        client.messages().createStreaming(params).use { streamResponse ->
            val iterator = streamResponse.stream().iterator()
            var emittedAny = false
            var outputTextDeltaCount = 0
            var thinkingTextDeltaCount = 0
            var toolUseIndex: Long? = null
            val pendingToolUses = mutableMapOf<Long, PendingToolUse>()

            while (currentCoroutineContext().isActive && runInterruptible { iterator.hasNext() }) {
                val event = runInterruptible { iterator.next() }
                val contentBlockStart = event.contentBlockStart()
                if (contentBlockStart.isPresent) {
                    val startEvent = contentBlockStart.get()
                    val block = contentBlockStart.get().contentBlock()
                    val startText = block.text().map { it.text() }.orElse("")
                    if (startText.isNotEmpty()) {
                        outputTextDeltaCount++
                        emittedAny = true
                        emitEvent(InferenceEvent.PartialResponse(startText, modelType))
                    }

                    val startThinking = block.thinking().map { it.thinking() }.orElse("")
                    if (startThinking.isNotEmpty()) {
                        thinkingTextDeltaCount++
                        emittedAny = true
                        emitEvent(InferenceEvent.Thinking(startThinking, modelType))
                    }
                    if (block.isToolUse()) {
                        val startedToolUse = block.asToolUse()
                        toolUseIndex = startEvent.index()
                        pendingToolUses[startEvent.index()] = PendingToolUse(
                            id = startedToolUse.id(),
                            toolName = startedToolUse.name(),
                            initialInput = startedToolUse._input(),
                        )
                        if (!allowToolUse) {
                            throw IllegalStateException("Search skill recursion limit exceeded")
                        }
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
                            emittedAny = true
                            emitEvent(InferenceEvent.PartialResponse(text, modelType))
                        }
                    }
                    val thinkingDelta = delta.thinking()
                    if (thinkingDelta.isPresent) {
                        val text = thinkingDelta.get().thinking()
                        if (text.isNotEmpty()) {
                            thinkingTextDeltaCount++
                            emittedAny = true
                            emitEvent(InferenceEvent.Thinking(text, modelType))
                        }
                    }
                    val inputJsonDelta = delta.inputJson()
                    if (inputJsonDelta.isPresent) {
                        pendingToolUses[deltaEvent.index()]
                            ?.streamedInputJson
                            ?.append(inputJsonDelta.get().partialJson())
                    }
                }
                if (event.messageStop().isPresent) {
                    loggingPort.debug(
                        TAG,
                        "Anthropic stream completed model=$modelId outputTextDeltas=$outputTextDeltaCount thinkingTextDeltas=$thinkingTextDeltaCount"
                    )
                }
                if (event.messageDelta().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream message delta model=$modelId")
                }
                if (event.contentBlockStart().isPresent) {
                    loggingPort.debug(
                        TAG,
                        "Anthropic stream content block start model=$modelId type=${
                            describeStreamEvent(event)
                        }"
                    )
                }
                if (event.contentBlockStop().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream content block stop model=$modelId")
                }
                if (event.messageStart().isPresent) {
                    loggingPort.debug(TAG, "Anthropic stream message start model=$modelId")
                }
            }

            val capturedToolUse = toolUseIndex
                ?.let { pendingToolUses[it] }
                ?.toCapturedToolUse()
            return StreamedAnthropicResponse(
                emittedAny = emittedAny,
                toolUse = capturedToolUse,
            )
        }
    }

    private fun assistantToolUseMessage(toolUse: CapturedToolUse): MessageParam =
        MessageParam.builder()
            .role(MessageParam.Role.ASSISTANT)
            .contentOfBlockParams(
                listOf(
                    ContentBlockParam.ofToolUse(
                        ToolUseBlockParam.builder()
                            .id(toolUse.id)
                            .name(toolUse.toolName)
                            .input(
                                ToolUseBlockParam.Input.builder()
                                    .putAllAdditionalProperties(toolUseInputProperties(toolUse.argumentsJson))
                                    .build()
                            )
                            .build()
                    )
                )
            )
            .build()

    private fun PendingToolUse.toCapturedToolUse(): CapturedToolUse =
        CapturedToolUse(
            id = id,
            toolName = toolName,
            argumentsJson = if (streamedInputJson.isNotEmpty()) {
                toolUseArgumentsJson(streamedInputJson.toString())
            } else {
                toolUseArgumentsJson(initialInput)
            },
        )

    private fun toolUseArgumentsJson(input: JsonValue): String {
        val properties: Map<*, *> = input.convert(Map::class.java) ?: emptyMap<Any?, Any?>()
        return canonicalToolArgumentsJson(properties)
    }

    private fun toolUseArgumentsJson(inputJson: String): String =
        canonicalToolArgumentsJson(parseToolInputJson(inputJson))

    private fun canonicalToolArgumentsJson(properties: Map<*, *>): String {
        @Suppress("UNCHECKED_CAST")
        return ToolEnvelopeParser.buildArgumentsJson(properties as Map<String, *>)
    }

    private fun toolUseInputProperties(argumentsJson: String): Map<String, JsonValue> =
        parseToolInputJson(argumentsJson).entries.associate { (key, value) ->
            key to JsonValue.from(value)
        }

    private fun parseToolInputJson(inputJson: String): Map<String, Any?> =
        runCatching {
            TOOL_INPUT_JSON_MAPPER.readValue(
                inputJson,
                object : TypeReference<Map<String, Any?>>() {}
            )
        }.getOrElse { error ->
            throw IllegalArgumentException("Tool input JSON is invalid", error)
        }
}
