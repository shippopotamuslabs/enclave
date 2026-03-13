package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.app.ThinkingModelEngine
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
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext


import javax.inject.Inject

/**
 * Use case for generating chat responses.
 * Routes to the appropriate service based on the selected mode:
 * - FAST: Uses Fast model for quick single-model responses
 * - THINKING: Uses Draft One model for reasoning responses
 * - CREW: Uses PipelineExecutorPort for multi-model pipeline
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

    operator fun invoke(prompt: String, userMessageId: Long, assistantMessageId: Long, chatId: Long, mode: Mode): Flow<MessageGenerationState> {
        // Determine inference type based on mode and available models
        // For Crew mode, check if ANY model in the pipeline is on-device
        val inferenceType = determineInferenceType(mode)

        // Try to acquire the global inference lock
        if (!inferenceLockManager.acquireLock(inferenceType)) {
            return flow {
                emit(MessageGenerationState.Blocked("Another inference is in progress. Please wait."))
            }
        }

        val baseFlow: Flow<MessageGenerationState> = when (mode) {
            Mode.FAST -> generateWithService(prompt, userMessageId, assistantMessageId, chatId, fastModelService, ModelType.FAST, inferenceType)
            Mode.THINKING -> generateWithService(prompt, userMessageId, assistantMessageId, chatId, thinkingModelService, ModelType.THINKING, inferenceType)
            Mode.CREW -> pipelineExecutor.executePipeline(
                chatId = chatId.toString(),
                userMessage = prompt,
                assistantMessageId = assistantMessageId.toString()
            )
        }

        // Wrap the flow to ensure lock is released when the flow completes (for all modes)
        return baseFlow.onCompletion { cause ->
            // Release lock when flow completes (success, error, or cancellation)
            inferenceLockManager.releaseLock()
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
            .filter { it.content.isNotBlank() } // Exclude empty placeholder messages
            .filter { it.id != userMessageId } // Exclude the user message being sent (not historical yet)
            .filter { it.id != assistantMessageId } // Exclude assistant placeholder (not historical yet)
        val chatMessages = messages.map { message ->
            ChatMessage(
                role = ChatRole.fromDomainRole(message.role),
                content = message.content
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
        inferenceType: InferenceType
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
                    emit(MessageGenerationState.GeneratingText(event.chunk))
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

                    emit(MessageGenerationState.Finished)
                }

                is InferenceEvent.SafetyBlocked -> {
                    emit(MessageGenerationState.Blocked(event.reason))
                }

                is InferenceEvent.Error -> {
                    emit(MessageGenerationState.Failed(event.cause))
                }
            }
        }
    }.flowOn(Dispatchers.Default)
}

sealed interface MessageGenerationState {
    data class ThinkingLive(val steps: List<String>, val modelType: ModelType) : MessageGenerationState
    data class GeneratingText(val textDelta: String) : MessageGenerationState
    object Finished : MessageGenerationState
    data class Blocked(val reason: String) : MessageGenerationState
    data class Failed(val error: Throwable) : MessageGenerationState
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
