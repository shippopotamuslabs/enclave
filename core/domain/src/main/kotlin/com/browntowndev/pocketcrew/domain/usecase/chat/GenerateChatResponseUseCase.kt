package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.chat.ChatId
import com.browntowndev.pocketcrew.domain.model.chat.MessageId
import com.browntowndev.pocketcrew.domain.model.inference.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceBusyException
import com.browntowndev.pocketcrew.domain.port.inference.InferenceFactoryPort
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ActiveModelProviderPort
import com.browntowndev.pocketcrew.domain.port.repository.SettingsRepository
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.model.inference.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import java.util.concurrent.CancellationException

import javax.inject.Inject

/**
 * Use case for generating chat responses.
 * 
 * ARCHITECTURE (Real-Time Flow Refactor):
 * 1. Accumulates state internally using MessageAccumulatorManager (not DB on every token)
 * 2. Emits AccumulatedMessages on every state change for real-time UI updates
 * 3. Persists all accumulated state to DB in single transaction on completion
 * 4. Uses buffer(64) for backpressure handling
 */
class GenerateChatResponseUseCase @Inject constructor(
    private val inferenceFactory: InferenceFactoryPort,
    private val pipelineExecutor: PipelineExecutorPort,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val loggingPort: LoggingPort,
    private val activeModelProvider: ActiveModelProviderPort,
    private val settingsRepository: SettingsRepository,
    private val searchToolPromptComposer: SearchToolPromptComposer,
    private val analyzeImageUseCase: AnalyzeImageUseCase,
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
        private const val FLOW_BUFFER_SIZE = 64
        private const val PROMPT_LOG_CHUNK_SIZE = 2_000
        private val TOOL_CALL_TRACE_REGEX = Regex("<tool_call>.*?</tool_call>", setOf(RegexOption.DOT_MATCHES_ALL))
        private val TOOL_RESULT_TRACE_REGEX = Regex("<tool_result>.*?</tool_result>", setOf(RegexOption.DOT_MATCHES_ALL))
    }

    operator fun invoke(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        mode: Mode
    ): Flow<AccumulatedMessages> {
        return flow {
            val userMessage = messageRepository.getMessageById(userMessageId)
                ?: throw IllegalStateException("User message $userMessageId was not found")
            val baseFlow: Flow<MessageGenerationState> = when (mode) {
                Mode.FAST -> flow {
                    try {
                        if (userMessage.content.imageUri != null) {
                            emit(MessageGenerationState.Processing(ModelType.FAST))
                        }
                        val effectivePrompt = withContext(Dispatchers.IO) {
                            preparePrompt(prompt, userMessage)
                        }
                        inferenceFactory.withInferenceService(ModelType.FAST) { service ->
                            emitAll(
                                generateWithService(
                                    effectivePrompt, userMessageId, assistantMessageId, chatId, service, ModelType.FAST
                                )
                            )
                        }
                    } catch (e: InferenceBusyException) {
                        emitBusyState(ModelType.FAST)
                    } catch (e: IllegalStateException) {
                        emit(MessageGenerationState.Failed(e, ModelType.FAST))
                    } catch (e: java.io.IOException) {
                        emit(MessageGenerationState.Failed(e, ModelType.FAST))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        loggingPort.error(TAG, "Unexpected error in FAST mode", e)
                        emit(
                            MessageGenerationState.Failed(
                                error = e,
                                modelType = ModelType.FAST
                            )
                        )
                    }
                }

                Mode.THINKING -> flow {
                    try {
                        if (userMessage.content.imageUri != null) {
                            emit(MessageGenerationState.Processing(ModelType.THINKING))
                        }
                        val effectivePrompt = withContext(Dispatchers.IO) {
                            preparePrompt(prompt, userMessage)
                        }
                        inferenceFactory.withInferenceService(ModelType.THINKING) { service ->
                            emitAll(
                                generateWithService(
                                    effectivePrompt, userMessageId, assistantMessageId, chatId, service, ModelType.THINKING
                                )
                            )
                        }
                    } catch (e: InferenceBusyException) {
                        emitBusyState(ModelType.THINKING)
                    } catch (e: IllegalStateException) {
                        emit(MessageGenerationState.Failed(e, ModelType.THINKING))
                    } catch (e: java.io.IOException) {
                        emit(MessageGenerationState.Failed(e, ModelType.THINKING))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        loggingPort.error(TAG, "Unexpected error in THINKING mode", e)
                        emit(
                            MessageGenerationState.Failed(
                                error = e,
                                modelType = ModelType.THINKING
                            )
                        )
                    }
                }

                Mode.CREW -> flow {
                    try {
                        if (userMessage.content.imageUri != null) {
                            emit(MessageGenerationState.Processing(ModelType.MAIN))
                        }
                        val effectivePrompt = withContext(Dispatchers.IO) {
                            preparePrompt(prompt, userMessage)
                        }
                        emitAll(
                            pipelineExecutor.executePipeline(
                                chatId = chatId.value,
                                userMessage = effectivePrompt,
                            )
                        )
                    } catch (e: InferenceBusyException) {
                        emitBusyState(ModelType.MAIN)
                    } catch (e: IllegalStateException) {
                        emit(MessageGenerationState.Failed(e, ModelType.MAIN))
                    } catch (e: java.io.IOException) {
                        emit(MessageGenerationState.Failed(e, ModelType.MAIN))
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        loggingPort.error(TAG, "Unexpected error in CREW mode", e)
                        emit(MessageGenerationState.Failed(e, ModelType.MAIN))
                    }
                }
            }

            val assistantMessageIds = mutableMapOf(ModelType.DRAFT_ONE to assistantMessageId)

            // Create accumulator manager for this invocation
            val accumulatorManager = MessageAccumulatorManager(
                mode = mode,
                chatId = chatId,
                userMessageId = userMessageId,
                defaultAssistantMessageId = assistantMessageId,
                assistantMessageIds = assistantMessageIds
            )
            val loggedThinkingFor = mutableMapOf(
                ModelType.DRAFT_ONE to false,
                ModelType.DRAFT_TWO to false,
                ModelType.MAIN to false,
                ModelType.FINAL_SYNTHESIS to false
            )
            val loggedProcessingFor = mutableMapOf(
                ModelType.DRAFT_ONE to false,
                ModelType.DRAFT_TWO to false,
                ModelType.MAIN to false,
                ModelType.FINAL_SYNTHESIS to false
            )
            val loggedGenerationFor = mutableMapOf(
                ModelType.DRAFT_ONE to false,
                ModelType.DRAFT_TWO to false,
                ModelType.MAIN to false,
                ModelType.FINAL_SYNTHESIS to false
            )
            val loggedStepCompletionFor = mutableMapOf(
                ModelType.DRAFT_ONE to false,
                ModelType.DRAFT_TWO to false,
                ModelType.MAIN to false,
                ModelType.FINAL_SYNTHESIS to false
            )

            try {
                baseFlow.collect { state ->
                    when (state) {
                        is MessageGenerationState.Processing -> {
                            if (loggedProcessingFor[state.modelType] == false) {
                                loggedProcessingFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.currentState = MessageState.PROCESSING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.ThinkingLive -> {
                            if (loggedThinkingFor[state.modelType] == false) {
                                loggedThinkingFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.appendThinking(state.thinkingChunk)
                            accumulator.currentState = MessageState.THINKING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.GeneratingText -> {
                            if (loggedGenerationFor[state.modelType] == false) {
                                loggedGenerationFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.appendContent(state.textDelta)
                            accumulator.currentState = MessageState.GENERATING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.StepCompleted -> {
                            if (loggedStepCompletionFor[state.modelType] == false) {
                                loggedStepCompletionFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.isComplete = true
                            accumulator.currentState = MessageState.COMPLETE
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.Finished -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.isComplete = true
                            accumulator.currentState = MessageState.COMPLETE
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.Blocked -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.clear()
                            accumulator.content.append("[Blocked: ${state.reason}]")
                            accumulator.isComplete = true
                            accumulator.currentState = MessageState.COMPLETE
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.Failed -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.clear()
                            if (state.error is InferenceBusyException) {
                                accumulator.content.append(state.error.message ?: "Another message is in progress. Please wait until it completes.")
                            } else {
                                accumulator.content.append("Error: ${state.error.message ?: "Unknown error"}")
                            }
                            accumulator.isComplete = true
                            accumulator.currentState = MessageState.COMPLETE
                            emit(accumulatorManager.toMessagesState())
                        }
                    }
                }
            } finally {
                try {
                    withContext(Dispatchers.IO) {
                        persistAccumulatedMessages(accumulatorManager)
                    }
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to persist messages", e)
                }
            }
        }.buffer(FLOW_BUFFER_SIZE).flowOn(Dispatchers.Default)
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<MessageGenerationState>.emitBusyState(
        modelType: ModelType
    ) {
        emit(
            MessageGenerationState.Failed(
                error = InferenceBusyException(),
                modelType = modelType
            )
        )
    }

    /**
     * Persists all accumulated messages to the database using single transaction.
     * Called once on flow completion.
     */
    private suspend fun persistAccumulatedMessages(
        accumulatorManager: MessageAccumulatorManager
    ) {
        accumulatorManager.messages.values.forEach { accumulator ->
            val finalState = if (accumulator.isComplete) {
                MessageState.COMPLETE
            } else {
                MessageState.PROCESSING
            }

            // Compute pipelineStep from modelType using the existing helper
            val pipelineStep = getPipelineStepForModelType(accumulator.modelType)

            chatRepository.persistAllMessageData(
                messageId = accumulator.messageId,
                modelType = accumulator.modelType,
                thinkingStartTime = accumulator.thinkingStartTime ?: 0L,
                thinkingEndTime = accumulator.thinkingEndTime ?: 0L,
                thinkingDuration = accumulator.thinkingDurationSeconds.toInt(),
                thinkingRaw = accumulator.thinkingRaw.toString().ifBlank { null },
                content = sanitizePersistedContent(accumulator.content.toString()),
                messageState = finalState,
                pipelineStep = pipelineStep
            )
        }
    }

    private fun sanitizePersistedContent(content: String): String =
        TOOL_RESULT_TRACE_REGEX
            .replace(TOOL_CALL_TRACE_REGEX.replace(content, ""), "")
            .trim()

    /**
     * Manager for accumulating message state across multiple events.
     * Uses StringBuilder for in-place updates (not data class).
     */
    private inner class MessageAccumulatorManager(
        private val mode: Mode,
        private val chatId: ChatId,
        private val userMessageId: MessageId,
        private val defaultAssistantMessageId: MessageId,
        private val assistantMessageIds: MutableMap<ModelType, MessageId> = mutableMapOf()
    ) {
        private val _messages = mutableMapOf<MessageId, MessageAccumulator>()
        val messages: Map<MessageId, MessageAccumulator> = _messages

        suspend fun getOrCreateAccumulator(modelType: ModelType): MessageAccumulator {
            val messageId = if (mode != Mode.CREW) {
                defaultAssistantMessageId
            } else {
                assistantMessageIds[modelType]
                    ?: chatRepository.createAssistantMessage(
                        chatId = chatId,
                        userMessageId = userMessageId,
                        modelType = modelType,
                        pipelineStep = getPipelineStepForModelType(modelType)
                    ).also { newId ->
                        assistantMessageIds[modelType] = newId
                    }
            }

            return _messages.getOrPut(messageId) {
                MessageAccumulator(
                    messageId = messageId,
                    modelType = modelType,
                    pipelineStep = getPipelineStepForModelType(modelType)
                )
            }
        }

        fun toMessagesState(): AccumulatedMessages {
            return AccumulatedMessages(
                messages = _messages.mapValues { (_, accumulator) ->
                    accumulator.toSnapshot()
                }
            )
        }
    }

    /**
     * Accumulated state of all messages for real-time UI updates.
     * This is the primary emission type after the flow transformation.
     */
    data class AccumulatedMessages(
        val messages: Map<MessageId, MessageSnapshot>
    )

    /**
     * Accumulates state for a single message using StringBuilder (in-place updates).
     * NOT a data class - allows efficient StringBuilder mutation.
     */
    private class MessageAccumulator(
        val messageId: MessageId,
        val modelType: ModelType,
        val content: StringBuilder = StringBuilder(),
        val thinkingRaw: StringBuilder = StringBuilder(),
        var thinkingStartTime: Long? = null,
        var thinkingEndTime: Long? = null,
        var isComplete: Boolean = false,
        var currentState: MessageState = MessageState.GENERATING,
        var pipelineStep: PipelineStep? = null
    ) {
        val thinkingDurationSeconds: Long
            get() = if (thinkingStartTime != null && thinkingEndTime != null) {
                (thinkingEndTime!! - thinkingStartTime!!) / 1000
            } else 0

        fun appendThinking(chunk: String) {
            thinkingRaw.append(chunk)
            if (thinkingStartTime == null) {
                thinkingStartTime = System.currentTimeMillis()
            }
        }

        fun appendContent(chunk: String) {
            content.append(chunk)
            if (thinkingStartTime != null && thinkingEndTime == null) {
                thinkingEndTime = System.currentTimeMillis()
            }
        }

        fun toSnapshot(): MessageSnapshot = MessageSnapshot(
            messageId = messageId,
            modelType = modelType,
            content = content.toString(),
            thinkingRaw = thinkingRaw.toString(),
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime ?: 0L,
            thinkingEndTime = thinkingEndTime ?: 0L,
            isComplete = isComplete,
            messageState = currentState,
            pipelineStep = pipelineStep,
        )
    }

    private fun getPipelineStepForModelType(modelType: ModelType): PipelineStep {
        return when (modelType) {
            ModelType.DRAFT_ONE -> PipelineStep.DRAFT_ONE
            ModelType.DRAFT_TWO -> PipelineStep.DRAFT_TWO
            ModelType.MAIN -> PipelineStep.SYNTHESIS
            ModelType.FINAL_SYNTHESIS -> PipelineStep.FINAL
            else -> PipelineStep.FINAL
        }
    }

    private suspend fun rehydrateHistory(
        chatId: ChatId,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        service: LlmInferencePort
    ) {
        val messages = messageRepository.getMessagesForChat(chatId)
            .filter { it.content.text.isNotBlank() || it.content.imageUri != null }
            .filter { it.id != userMessageId }
            .filter { it.id != assistantMessageId }
        val analysesByMessage = messageRepository.getVisionAnalysesForMessages(messages.map { it.id })

        val chatMessages = messages.map { message ->
            val visionAnalyses = analysesByMessage[message.id].orEmpty()
            ChatMessage(
                role = message.role,
                content = buildHistoryContent(message, visionAnalyses)
            )
        }
        
        service.setHistory(chatMessages)
        loggingPort.debug(TAG, "Rehydrated ${chatMessages.size} messages")
    }

    private fun generateWithService(
        prompt: String,
        userMessageId: MessageId,
        assistantMessageId: MessageId,
        chatId: ChatId,
        service: LlmInferencePort,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        try {
            rehydrateHistory(chatId, userMessageId, assistantMessageId, service)
        } catch (e: Exception) {
            // Rehydration failures are not fatal to generation
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        val config = activeModelProvider.getActiveConfiguration(modelType)
        val searchEnabled = settingsRepository.settingsFlow.first().searchEnabled
        val reasoningBudget = if (config?.isLocal == true && config.thinkingEnabled) 2048 else 0
        loggingPort.debug(
            TAG,
            "generateWithService config modelType=$modelType configName=${config?.name} isLocal=${config?.isLocal} searchEnabled=$searchEnabled thinkingEnabled=${config?.thinkingEnabled} reasoningEffort=${config?.reasoningEffort} derivedReasoningBudget=$reasoningBudget"
        )
        if (config?.isLocal == true) {
            logLocalPrompt(prompt, modelType)
        }
        
        // ARCHITECTURE: Explicitly provide modelType to options for event tagging
        val systemPrompt = when {
            config?.isLocal == true && searchEnabled -> searchToolPromptComposer.compose(config.systemPrompt)
            else -> config?.systemPrompt
        }
        val options = GenerationOptions(
            reasoningBudget = reasoningBudget,
            modelType = modelType,
            systemPrompt = systemPrompt,
            reasoningEffort = config?.reasoningEffort,
            temperature = config?.temperature?.toFloat(),
            topK = config?.topK,
            topP = config?.topP?.toFloat(),
            maxTokens = config?.maxTokens,
            contextWindow = config?.contextWindow,
            toolingEnabled = searchEnabled,
            availableTools = if (searchEnabled) {
                listOf(ToolDefinition.TAVILY_WEB_SEARCH)
            } else {
                emptyList()
            },
        )

        service.sendPrompt(prompt, options, closeConversation = false).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    emit(MessageGenerationState.ThinkingLive(event.chunk, modelType))
                }
                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                }
                is InferenceEvent.Finished -> {
                    emit(MessageGenerationState.Finished(event.modelType))
                }
                is InferenceEvent.SafetyBlocked -> {
                    loggingPort.warning(TAG, "InferenceEvent.SafetyBlocked modelType=${event.modelType} reason=${event.reason}")
                    emit(MessageGenerationState.Blocked(event.reason, event.modelType))
                }
                is InferenceEvent.Error -> {
                    loggingPort.error(TAG, "InferenceEvent.Error modelType=${event.modelType} message=${event.cause.message}", event.cause)
                    emit(MessageGenerationState.Failed(event.cause, event.modelType))
                }
            }
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun preparePrompt(
        prompt: String,
        userMessage: com.browntowndev.pocketcrew.domain.model.chat.Message,
    ): String {
        val imageUri = userMessage.content.imageUri ?: return prompt
        val description = messageRepository
            .getVisionAnalysesForMessages(listOf(userMessage.id))[userMessage.id]
            .orEmpty()
            .firstOrNull { it.imageUri == imageUri }
            ?.analysisText
            ?: analyzeImageUseCase(imageUri, prompt).also { analysisText ->
                messageRepository.saveVisionAnalysis(
                    userMessageId = userMessage.id,
                    imageUri = imageUri,
                    promptText = prompt,
                    analysisText = analysisText,
                    modelType = ModelType.VISION,
                )
             }
        loggingPort.debug(
            TAG,
            "preparePrompt image analysis chars=${description.length} containsDataUri=${description.contains("data:image", ignoreCase = true)} containsBase64Marker=${description.contains("base64,", ignoreCase = true)} containsMarkdownImage=${description.contains("![](") || description.contains("![")}"
        )
        return if (prompt.isBlank()) {
            """
            The user attached an image without additional text.

            Attached image description:
            $description

            Respond helpfully based on the image description.
            """.trimIndent()
        } else {
            """
            Attached image description:
            $description

            User request:
            $prompt
            """.trimIndent()
        }
    }

    private fun logLocalPrompt(
        prompt: String,
        modelType: ModelType,
    ) {
        val containsImageDescription = prompt.contains("Attached image description:")
        val chunks = prompt.chunked(PROMPT_LOG_CHUNK_SIZE)
        loggingPort.debug(
            TAG,
            "Local prompt handoff modelType=$modelType chars=${prompt.length} containsImageDescription=$containsImageDescription containsDataUri=${prompt.contains("data:image", ignoreCase = true)} containsBase64Marker=${prompt.contains("base64,", ignoreCase = true)}"
        )
        chunks.forEachIndexed { index, chunk ->
            loggingPort.debug(
                TAG,
                "Local prompt handoff chunk ${index + 1}/${chunks.size} modelType=$modelType:\n$chunk"
            )
        }
    }

    private fun buildHistoryContent(
        message: com.browntowndev.pocketcrew.domain.model.chat.Message,
        visionAnalyses: List<com.browntowndev.pocketcrew.domain.model.chat.MessageVisionAnalysis>,
    ): String {
        if (visionAnalyses.isEmpty()) {
            return message.content.text.ifBlank {
                if (message.content.imageUri != null) {
                    "[User attached an image]"
                } else {
                    ""
                }
            }
        }

        val analysisBlock = visionAnalyses.joinToString(separator = "\n\n") { analysis ->
            """
            Attached image description:
            ${analysis.analysisText}
            """.trimIndent()
        }

        return if (message.content.text.isBlank()) {
            analysisBlock
        } else {
            """
            $analysisBlock

            User request:
            ${message.content.text}
            """.trimIndent()
        }
    }
}

/**
 * Immutable snapshot of a message's accumulated state.
 * Used by AccumulatedMessages for UI consumption.
 */
data class MessageSnapshot(
    val messageId: MessageId,
    val modelType: ModelType,
    val content: String,
    val thinkingRaw: String,
    val thinkingDurationSeconds: Long = 0,
    val thinkingStartTime: Long = 0,
    val thinkingEndTime: Long = 0,
    val isComplete: Boolean = false,
    val messageState: MessageState = if (isComplete) MessageState.COMPLETE else MessageState.GENERATING,
    val pipelineStep: PipelineStep? = null
)
