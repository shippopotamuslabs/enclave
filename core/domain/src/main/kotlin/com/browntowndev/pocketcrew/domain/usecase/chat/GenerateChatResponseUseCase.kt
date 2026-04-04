package com.browntowndev.pocketcrew.domain.usecase.chat

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
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
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
    private val inferenceLockManager: InferenceLockManager,
    private val modelRegistry: ModelRegistryPort,
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
        private const val FLOW_BUFFER_SIZE = 64
    }

    operator fun invoke(
        prompt: String,
        userMessageId: Long,
        assistantMessageId: Long,
        chatId: Long,
        mode: Mode
    ): Flow<AccumulatedMessages> {
        return flow {
            val baseFlow: Flow<MessageGenerationState> = when (mode) {
                Mode.FAST -> flow {
                    try {
                        inferenceFactory.withInferenceService(ModelType.FAST) { service ->
                            emitAll(
                                generateWithService(
                                    prompt, userMessageId, assistantMessageId, chatId, service, ModelType.FAST
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
                        inferenceFactory.withInferenceService(ModelType.THINKING) { service ->
                            emitAll(
                                generateWithService(
                                    prompt, userMessageId, assistantMessageId, chatId, service, ModelType.THINKING
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

                Mode.CREW -> pipelineExecutor.executePipeline(
                    chatId = chatId.toString(),
                    userMessage = prompt,
                )
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
                                loggingPort.debug(TAG, "Processing Started: ${state.modelType}")
                                loggedProcessingFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.currentState = MessageState.PROCESSING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.ThinkingLive -> {
                            if (loggedThinkingFor[state.modelType] == false) {
                                loggingPort.debug(TAG, "Thinking Started: ${state.modelType}")
                                loggedThinkingFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.appendThinking(state.thinkingChunk)
                            accumulator.currentState = MessageState.THINKING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.GeneratingText -> {
                            if (loggedGenerationFor[state.modelType] == false) {
                                loggingPort.debug(TAG, "Generation Started: ${state.modelType}")
                                loggedGenerationFor[state.modelType] = true
                            }

                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.appendContent(state.textDelta)
                            accumulator.currentState = MessageState.GENERATING
                            emit(accumulatorManager.toMessagesState())
                        }

                        is MessageGenerationState.StepCompleted -> {
                            if (loggedStepCompletionFor[state.modelType] == false) {
                                loggingPort.debug(TAG, "Step Completed: ${state.modelType}")
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
                    persistAccumulatedMessages(accumulatorManager)
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to persist messages", e)
                }
            }
        }.buffer(FLOW_BUFFER_SIZE)
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
                content = accumulator.content.toString(),
                messageState = finalState,
                pipelineStep = pipelineStep
            )
        }
    }

    /**
     * Manager for accumulating message state across multiple events.
     * Uses StringBuilder for in-place updates (not data class).
     */
    private inner class MessageAccumulatorManager(
        private val mode: Mode,
        private val chatId: Long,
        private val userMessageId: Long,
        private val defaultAssistantMessageId: Long,
        private val assistantMessageIds: MutableMap<ModelType, Long> = mutableMapOf()
    ) {
        private val _messages = mutableMapOf<Long, MessageAccumulator>()
        val messages: Map<Long, MessageAccumulator> = _messages

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
        val messages: Map<Long, MessageSnapshot>
    )

    /**
     * Accumulates state for a single message using StringBuilder (in-place updates).
     * NOT a data class - allows efficient StringBuilder mutation.
     */
    private class MessageAccumulator(
        val messageId: Long,
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
        chatId: Long,
        userMessageId: Long,
        assistantMessageId: Long,
        service: LlmInferencePort
    ) {
        val messages = messageRepository.getMessagesForChat(chatId)
            .filter { it.content.text.isNotBlank() }
            .filter { it.id != userMessageId }
            .filter { it.id != assistantMessageId }
        
        val chatMessages = messages.map { message ->
            ChatMessage(role = message.role, content = message.content.text)
        }
        
        service.setHistory(chatMessages)
        loggingPort.debug(TAG, "Rehydrated ${chatMessages.size} messages")
    }

    private fun generateWithService(
        prompt: String,
        userMessageId: Long,
        assistantMessageId: Long,
        chatId: Long,
        service: LlmInferencePort,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        try {
            rehydrateHistory(chatId, userMessageId, assistantMessageId, service)
        } catch (e: Exception) {
            // Rehydration failures are not fatal to generation
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        val config = modelRegistry.getRegisteredConfiguration(modelType)
        val reasoningBudget = if (config?.thinkingEnabled == true) 2048 else 0
        
        // ARCHITECTURE: Explicitly provide modelType to options for event tagging
        val options = GenerationOptions(
            reasoningBudget = reasoningBudget,
            modelType = modelType,
            temperature = config?.temperature?.toFloat(),
            topK = config?.topK,
            topP = config?.topP?.toFloat(),
            maxTokens = config?.maxTokens
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
                    emit(MessageGenerationState.Blocked(event.reason, event.modelType))
                }
                is InferenceEvent.Error -> {
                    emit(MessageGenerationState.Failed(event.cause, event.modelType))
                }
            }
        }
    }.flowOn(Dispatchers.Default)
}

/**
 * Immutable snapshot of a message's accumulated state.
 * Used by AccumulatedMessages for UI consumption.
 */
data class MessageSnapshot(
    val messageId: Long,
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
