package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.app.ThinkingModelEngine
import com.browntowndev.pocketcrew.domain.model.MessageState
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.domain.port.repository.MessageRepository
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceLockManager
import com.browntowndev.pocketcrew.domain.usecase.inference.InferenceType
import com.browntowndev.pocketcrew.inference.llama.ChatMessage
import com.browntowndev.pocketcrew.inference.llama.ChatRole
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext


import javax.inject.Inject

/**
 * Use case for generating chat responses.
 * Routes to the appropriate service based on the selected mode:
 * - FAST: Uses Fast model for quick single-model responses
 * - THINKING: Uses Draft One model for reasoning responses
 * - CREW: Uses PipelineExecutorPort for multimodel pipeline
 *
 * IMPORTANT: This use case performs history rehydration from the database
 * to maintain conversation context across app restarts or model unloads.
 */
class GenerateChatResponseUseCase @Inject constructor(
    @FastModelEngine private val fastModelService: LlmInferencePort,
    @ThinkingModelEngine private val thinkingModelService: LlmInferencePort,
    private val pipelineExecutor: PipelineExecutorPort,
    private val chatRepository: ChatRepository,
    private val messageRepository: MessageRepository,
    private val bufferThinkingSteps: BufferThinkingStepsUseCase,
    private val loggingPort: LoggingPort,
    private val inferenceLockManager: InferenceLockManager,
    private val modelRegistry: ModelRegistryPort,
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    suspend operator fun invoke(
        prompt: String,
        userMessageId: Long,
        assistantMessageId: Long,
        chatId: Long,
        mode: Mode) : Flow<MessageGenerationState> {
        // Determine inference type based on mode and available models
        // For Crew mode, check if ANY model in the pipeline is on-device
        val inferenceType = determineInferenceType(mode)

        // Try to acquire the global inference lock
        if (!inferenceLockManager.acquireLock(inferenceType)) {
            chatRepository.updateMessageContent(assistantMessageId, content = "Another message is in progress. Please wait until it completes.")
            chatRepository.updateMessageState(assistantMessageId, MessageState.COMPLETE)
        }

        val baseFlow: Flow<MessageGenerationState> = when (mode) {
            Mode.FAST -> generateWithService(
                prompt,
                userMessageId,
                assistantMessageId,
                chatId,
                fastModelService,
                ModelType.FAST,
            )

            Mode.THINKING -> generateWithService(
                prompt,
                userMessageId,
                assistantMessageId,
                chatId,
                thinkingModelService,
                ModelType.THINKING,
            )

            Mode.CREW -> pipelineExecutor.executePipeline(
                chatId = chatId.toString(),
                userMessage = prompt,
            )
        }

        val assistantMessageIds = mapOf(ModelType.DRAFT_ONE to assistantMessageId)

        // Wrap the flow to ensure lock is released when the flow completes (for all modes)
        return baseFlow
            .onEach { state ->
                when (state) {
                    is MessageGenerationState.ThinkingLive -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Update model type for the message
                        chatRepository.updateMessageModelType(currentMessageId, state.modelType)

                        // Save thinking steps and update message state to THINKING
                        chatRepository.saveThinkingSteps(currentMessageId, state.steps)
                    }
                    is MessageGenerationState.GeneratingText -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Append content to message and update state to GENERATING
                        chatRepository.appendMessageContent(currentMessageId, state.textDelta)
                        chatRepository.updateMessageState(currentMessageId, MessageState.GENERATING)
                    }
                    is MessageGenerationState.StepCompleted -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Update thinking duration and message state to COMPLETED
                        chatRepository.updateThinkingDuration(currentMessageId, state.thinkingDurationSeconds)
                        chatRepository.updateMessageState(currentMessageId, MessageState.COMPLETE)
                    }
                    is MessageGenerationState.Finished -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Update message state to COMPLETED
                        // Skip for CREW mode - already done in StepCompleted
                        if (mode != Mode.CREW) {
                            chatRepository.updateMessageState(currentMessageId, MessageState.COMPLETE)
                        }
                    }
                    is MessageGenerationState.Blocked -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Clear thinking steps and replace content with reason
                        chatRepository.clearThinkingSteps(currentMessageId)
                        chatRepository.updateMessageContent(currentMessageId, "[Blocked: ${state.reason}]")
                        chatRepository.updateMessageState(currentMessageId, MessageState.COMPLETE)
                    }
                    is MessageGenerationState.Failed -> {
                        val currentMessageId = getCurrentMessageId(
                            mode = mode,
                            modelType = state.modelType,
                            chatId = chatId,
                            userMessageId = userMessageId,
                            defaultValue = assistantMessageId,
                            assistantMessageIds = assistantMessageIds)

                        // Clear thinking steps and content, update state
                        chatRepository.clearThinkingSteps(currentMessageId)
                        chatRepository.updateMessageContent(currentMessageId, "")
                        chatRepository.updateMessageState(currentMessageId, MessageState.COMPLETE)
                    }
                }
            }.onCompletion { cause ->
                if (cause != null) {
                    loggingPort.error(TAG, message = "Flow failed.", throwable = cause)
                }

                // Release lock when flow completes (success, error, or cancellation)
                inferenceLockManager.releaseLock()
            }
    }

    private suspend fun getCurrentMessageId(
        mode: Mode,
        modelType: ModelType,
        chatId: Long,
        userMessageId: Long,
        defaultValue: Long,
        assistantMessageIds: Map<ModelType, Long>): Long {

        return if (mode != Mode.CREW) {
            defaultValue
        }
        else {
            assistantMessageIds[modelType] ?:
                chatRepository.createAssistantMessage(
                    chatId = chatId,
                    userMessageId = userMessageId,
                    modelType = modelType,
                    pipelineStep = getPipelineStepForModelType(modelType)
                )
        }
    }

    /**
     * Maps a ModelType to its corresponding PipelineStep.
     */
    private fun getPipelineStepForModelType(modelType: ModelType): PipelineStep {
        return when (modelType) {
            ModelType.DRAFT_ONE -> PipelineStep.DRAFT_ONE
            ModelType.DRAFT_TWO -> PipelineStep.DRAFT_TWO
            ModelType.MAIN -> PipelineStep.SYNTHESIS
            ModelType.FINAL_SYNTHESIS -> PipelineStep.FINAL
            else -> PipelineStep.FINAL // Default for unknown types
        }
    }

    /**
     * Determines the inference type based on the mode and available models.
     *
     * For CREW mode: Checks if ANY model in the pipeline (DRAFT_ONE, DRAFT_TWO, MAIN)
     * is registered (on-device). If at least one is on-device, uses ON_DEVICE lock.
     * This ensures we block concurrent on-device inference even if some pipeline models are BYOK.
     *
     * For FAST/THINKING modes: Uses the specific model type.
     *
     * TODO: When BYOK support is added, check ModelConfiguration for external API configuration.
     * If ALL models in the pipeline are configured for external APIs, use BYOK type.
     */
    private fun determineInferenceType(mode: Mode): InferenceType {
        return when (mode) {
            Mode.FAST -> {
                // FAST uses FAST model - check if registered (on-device)
                if (modelRegistry.getRegisteredModelSync(ModelType.FAST) != null) {
                    InferenceType.ON_DEVICE
                } else {
                    // Model not registered - could be BYOK in the future
                    InferenceType.ON_DEVICE // Default to blocking for safety
                }
            }
            Mode.THINKING -> {
                // THINKING uses THINKING model - check if registered (on-device)
                if (modelRegistry.getRegisteredModelSync(ModelType.THINKING) != null) {
                    InferenceType.ON_DEVICE
                } else {
                    InferenceType.ON_DEVICE // Default to blocking for safety
                }
            }
            Mode.CREW -> {
                // CREW uses DRAFT_ONE, DRAFT_TWO, and MAIN models
                // If ANY of these are on-device, we need to block concurrent inference
                val draftOneOnDevice = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_ONE) != null
                val draftTwoOnDevice = modelRegistry.getRegisteredModelSync(ModelType.DRAFT_TWO) != null
                val mainOnDevice = modelRegistry.getRegisteredModelSync(ModelType.MAIN) != null

                // If at least one model is on-device, use ON_DEVICE lock
                // This handles the mixed scenario: some BYOK, some on-device
                if (draftOneOnDevice || draftTwoOnDevice || mainOnDevice) {
                    InferenceType.ON_DEVICE
                } else {
                    // TODO: When BYOK is implemented, check if all models are configured for external APIs
                    // For now, default to ON_DEVICE if no models registered (safety)
                    InferenceType.ON_DEVICE
                }
            }
        }
    }

    /**
     * Rehydrates conversation history from database.
     * Loads recent messages for the chat and sets them in the inference service.
     * Filters out:
     * - Empty placeholder messages
     * - The current user message being sent (not historical yet)
     * - The assistant placeholder message being generated (empty content)
     *
     * @param chatId The chat ID to load history from
     * @param userMessageId The ID of the user message being sent (excluded from history)
     * @param assistantMessageId The ID of the assistant message being generated (excluded from history)
     * @param service The inference service to set history in
     */
    private suspend fun rehydrateHistory(chatId: Long, userMessageId: Long, assistantMessageId: Long, service: LlmInferencePort) {
        val messages = messageRepository.getMessagesForChat(chatId)
            .filter { it.content.text.isNotBlank() } // Exclude empty placeholder messages
            .filter { it.id != userMessageId } // Exclude the user message being sent (not historical yet)
            .filter { it.id != assistantMessageId } // Exclude assistant placeholder (not historical yet)
        val chatMessages = messages.map { message ->
            ChatMessage(
                role = ChatRole.fromDomainRole(message.role),
                content = message.content.text
            )
        }
        service.setHistory(chatMessages)
        loggingPort.debug(TAG, message = "Rehydrated ${chatMessages.size} messages")
        chatMessages.forEach { message ->
            loggingPort.debug(TAG, message = "Rehydrated message: role=${message.role}")
            loggingPort.debug(TAG, message = "Rehydrated message: content=${message.content}")
        }
    }

    private fun generateWithService(
        prompt: String,
        userMessageId: Long,
        assistantMessageId: Long,
        chatId: Long,
        service: LlmInferencePort,
        modelType: ModelType,
    ): Flow<MessageGenerationState> = flow {
        val startTime = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()
        bufferThinkingSteps.reset()

        // REHYDRATION PHASE: Load conversation history from database
        // This ensures context is preserved across app restarts or model unloads
        try {
            withContext(Dispatchers.IO) {
                rehydrateHistory(chatId, userMessageId, assistantMessageId, service)
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to rehydrate history: ${e.message}")
            // Continue without rehydration - the service will start with fresh context
        }

        service.sendPrompt(prompt, closeConversation = false).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    val newThoughts = bufferThinkingSteps(event.chunk)
                    for (thought in newThoughts) {
                        if (thought != currentSteps.lastOrNull()) {
                            currentSteps.add(thought)
                        }
                    }
                    if (newThoughts.isNotEmpty()) {
                        emit(MessageGenerationState.ThinkingLive(currentSteps.toList(), modelType))
                    }
                }

                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk, event.modelType))
                }

                is InferenceEvent.Completed -> {
                    // Flush any remaining buffered words
                    val finalStep = bufferThinkingSteps.flush()
                    if (finalStep != null && finalStep != currentSteps.lastOrNull()) {
                        currentSteps.add(finalStep)
                    }

                    val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                    val thinkingData = if (currentSteps.isNotEmpty()) {
                        ThinkingData(
                            thinkingDurationSeconds = duration,
                            steps = currentSteps,
                            rawFullThought = currentSteps.joinToString(" -> ")
                        )
                    } else null

                    chatRepository.saveAssistantMessage(
                        messageId = assistantMessageId,
                        content = event.finalResponse,
                        thinkingData = thinkingData
                    )

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

sealed interface MessageGenerationState {
    data class ThinkingLive(val steps: List<String>, val modelType: ModelType) : MessageGenerationState
    data class GeneratingText(val textDelta: String, val modelType: ModelType) : MessageGenerationState
    data class Finished(val modelType: ModelType) : MessageGenerationState
    data class Blocked(val reason: String, val modelType: ModelType) : MessageGenerationState
    data class Failed(val error: Throwable, val modelType: ModelType) : MessageGenerationState
    data class StepCompleted(
        val stepOutput: String,
        val thinkingDurationSeconds: Int,                  // Thinking time only (for "Thought For Xs")
        val totalDurationSeconds: Int = 0,       // Total time (for BottomSheet display)
        val thinkingSteps: List<String>,
        val modelDisplayName: String,
        val modelType: ModelType,
        val stepType: PipelineStep
    ) : MessageGenerationState
}
