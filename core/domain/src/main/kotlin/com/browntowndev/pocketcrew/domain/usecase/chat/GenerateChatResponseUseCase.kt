package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.model.inference.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.Mode
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.model.chat.Role
import com.browntowndev.pocketcrew.domain.model.chat.Message
import com.browntowndev.pocketcrew.domain.model.chat.Content
import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transform

import javax.inject.Inject

/**
 * Use case for generating chat responses.
 * 
 * ARCHITECTURE (Real-Time Flow Refactor):
 * 1. Accumulates state internally using MessageAccumulatorManager (not DB on every token)
 * 2. Emits MessagesState on every state change for real-time UI updates
 * 3. Persists all accumulated state to DB in single transaction on completion
 * 4. Uses buffer(64) for backpressure handling
 */
class GenerateChatResponseUseCase @Inject constructor(
    @param:FastModelEngine private val fastModelService: LlmInferencePort,
    @param:ThinkingModelEngine private val thinkingModelService: LlmInferencePort,
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

    suspend operator fun invoke(
        prompt: String,
        userMessageId: Long,
        assistantMessageId: Long,
        chatId: Long,
        mode: Mode
    ): Flow<MessageGenerationState> {
        val inferenceType = determineInferenceType(mode)

        // Try to acquire the global inference lock
        if (!inferenceLockManager.acquireLock(inferenceType)) {
            return flow {
                emit(
                    MessageGenerationState.MessagesState(
                        mapOf(
                            assistantMessageId to MessageSnapshot(
                                messageId = assistantMessageId,
                                modelType = ModelType.FAST,
                                content = "Another message is in progress. Please wait until it completes.",
                                thinkingRaw = "",
                                isComplete = true
                            )
                        )
                    )
                )
            }
        }

        val baseFlow: Flow<MessageGenerationState> = when (mode) {
            Mode.FAST -> generateWithService(
                prompt, userMessageId, assistantMessageId, chatId, fastModelService, ModelType.FAST
            )
            Mode.THINKING -> generateWithService(
                prompt, userMessageId, assistantMessageId, chatId, thinkingModelService, ModelType.THINKING
            )
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

        return flow {
            baseFlow
                .collect { state ->
                    when (state) {
                        is MessageGenerationState.ThinkingLive -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.thinkingRaw.append(state.thinkingChunk)
                            if (accumulator.thinkingStartTime == null) {
                                accumulator.thinkingStartTime = System.currentTimeMillis()
                            }
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.GeneratingText -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.append(state.textDelta)
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.StepCompleted -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.clear()
                            accumulator.content.append(state.stepOutput)
                            accumulator.thinkingRaw.clear()
                            accumulator.thinkingRaw.append(state.thinkingRaw)
                            accumulator.thinkingEndTime = System.currentTimeMillis()
                            accumulator.isComplete = true
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.Finished -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.thinkingEndTime = System.currentTimeMillis()
                            accumulator.isComplete = true
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.Blocked -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.clear()
                            accumulator.content.append("[Blocked: ${state.reason}]")
                            accumulator.isComplete = true
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.Failed -> {
                            val accumulator = accumulatorManager.getOrCreateAccumulator(state.modelType)
                            accumulator.content.clear()
                            accumulator.content.append("Error: ${state.error.message ?: "Unknown error"}")
                            accumulator.isComplete = true
                            emit(accumulatorManager.toMessagesState())
                        }
                        is MessageGenerationState.MessagesState -> {
                            emit(state)
                        }
                    }
                }
        }
            .buffer(FLOW_BUFFER_SIZE)
            .onCompletion { cause ->
                if (cause != null) {
                    loggingPort.error(TAG, "Flow failed", cause)
                }
                
                try {
                    persistAccumulatedMessages(accumulatorManager, mode)
                } catch (e: Exception) {
                    loggingPort.error(TAG, "Failed to persist messages", e)
                }
                
                inferenceLockManager.releaseLock()
            }
    }

    /**
     * Persists all accumulated messages to the database using single transaction.
     * Called once on flow completion.
     */
    private suspend fun persistAccumulatedMessages(
        accumulatorManager: MessageAccumulatorManager,
        mode: Mode
    ) {
        accumulatorManager.messages.values.forEach { accumulator ->
            val finalState = if (accumulator.isComplete) {
                MessageState.COMPLETE
            } else {
                MessageState.PROCESSING
            }
            
            chatRepository.persistAllMessageData(
                messageId = accumulator.messageId,
                modelType = accumulator.modelType,
                thinkingStartTime = accumulator.thinkingStartTime ?: 0L,
                thinkingEndTime = accumulator.thinkingEndTime ?: 0L,
                thinkingDuration = accumulator.thinkingDurationSeconds.toInt(),
                thinkingRaw = accumulator.thinkingRaw.toString().ifBlank { null },
                content = accumulator.content.toString(),
                messageState = finalState
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
                    modelType = modelType
                )
            }
        }

        fun toMessagesState(): MessageGenerationState.MessagesState {
            return MessageGenerationState.MessagesState(
                messages = _messages.mapValues { (_, accumulator) ->
                    accumulator.toSnapshot()
                }
            )
        }
    }

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
        var isComplete: Boolean = false
    ) {
        val thinkingDurationSeconds: Long
            get() = if (thinkingStartTime != null && thinkingEndTime != null) {
                (thinkingEndTime!! - thinkingStartTime!!) / 1000
            } else 0

        fun toSnapshot(): MessageSnapshot = MessageSnapshot(
            messageId = messageId,
            modelType = modelType,
            content = content.toString(),
            thinkingRaw = thinkingRaw.toString(),
            thinkingDurationSeconds = thinkingDurationSeconds,
            thinkingStartTime = thinkingStartTime ?: 0L,
            thinkingEndTime = thinkingEndTime ?: 0L,
            isComplete = isComplete
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

    private fun determineInferenceType(mode: Mode): InferenceType {
        return when (mode) {
            Mode.FAST -> {
                if (modelRegistry.getRegisteredModelSync(ModelType.FAST) != null) {
                    InferenceType.ON_DEVICE
                } else {
                    InferenceType.ON_DEVICE
                }
            }
            Mode.THINKING -> {
                if (modelRegistry.getRegisteredModelSync(ModelType.THINKING) != null) {
                    InferenceType.ON_DEVICE
                } else {
                    InferenceType.ON_DEVICE
                }
            }
            Mode.CREW -> {
                val draftOneOnDevice = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) != null
                val draftTwoOnDevice = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) != null
                val mainOnDevice = modelRegistry.getRegisteredModelSync(ModelType.MAIN) != null

                if (draftOneOnDevice || draftTwoOnDevice || mainOnDevice) {
                    InferenceType.ON_DEVICE
                } else {
                    InferenceType.ON_DEVICE
                }
            }
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
            loggingPort.debug(TAG, "Failed to rehydrate history: ${e.message}")
        }

        service.sendPrompt(prompt, closeConversation = false).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    emit(MessageGenerationState.ThinkingLive(event.chunk, modelType))
                }
                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                }
                is InferenceEvent.Completed -> {
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
 * Sealed interface for message generation states.
 * MessagesState is the primary emission for UI updates.
 */
sealed interface MessageGenerationState {
    data class ThinkingLive(val thinkingChunk: String, val modelType: ModelType) : MessageGenerationState
    data class GeneratingText(val textDelta: String, val modelType: ModelType) : MessageGenerationState
    data class Finished(val modelType: ModelType) : MessageGenerationState
    data class Blocked(val reason: String, val modelType: ModelType) : MessageGenerationState
    data class Failed(val error: Throwable, val modelType: ModelType) : MessageGenerationState
    data class StepCompleted(
        val stepOutput: String,
        val thinkingDurationSeconds: Int,
        val totalDurationSeconds: Int = 0,
        val thinkingRaw: String,
        val modelDisplayName: String,
        val modelType: ModelType,
        val stepType: PipelineStep
    ) : MessageGenerationState

    /**
     * Accumulated state of all messages for real-time UI updates.
     * This is the primary emission type after the flow transformation.
     */
    data class MessagesState(
        val messages: Map<Long, MessageSnapshot>
    ) : MessageGenerationState
}

/**
 * Immutable snapshot of a message's accumulated state.
 * Used by MessagesState for UI consumption.
 */
data class MessageSnapshot(
    val messageId: Long,
    val modelType: ModelType,
    val content: String,
    val thinkingRaw: String,
    val thinkingDurationSeconds: Long = 0,
    val thinkingStartTime: Long = 0,
    val thinkingEndTime: Long = 0,
    val isComplete: Boolean = false
)
