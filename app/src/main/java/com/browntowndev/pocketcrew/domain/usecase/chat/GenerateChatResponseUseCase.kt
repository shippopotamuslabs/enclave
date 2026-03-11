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
import com.browntowndev.pocketcrew.inference.llama.ChatMessage
import com.browntowndev.pocketcrew.inference.llama.ChatRole
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    operator fun invoke(prompt: String, userMessageId: Long, assistantMessageId: Long, chatId: Long, mode: Mode): Flow<MessageGenerationState> {
        return when (mode) {
            Mode.FAST -> generateWithService(prompt, userMessageId, assistantMessageId, chatId, fastModelService)
            Mode.THINKING -> generateWithService(prompt, userMessageId, assistantMessageId, chatId, thinkingModelService)
            Mode.CREW -> pipelineExecutor.executePipeline(
                chatId = chatId.toString(),
                userMessage = prompt,
                assistantMessageId = assistantMessageId.toString()
            )
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
        service: LlmInferencePort
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
                        emit(MessageGenerationState.ThinkingLive(currentSteps.toList()))
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
                            durationSeconds = duration,
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
    data class ThinkingLive(val steps: List<String>) : MessageGenerationState
    data class GeneratingText(val textDelta: String) : MessageGenerationState
    object Finished : MessageGenerationState
    data class Blocked(val reason: String) : MessageGenerationState
    data class Failed(val error: Throwable) : MessageGenerationState
}
