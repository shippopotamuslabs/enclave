package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.app.DraftOneModelEngine
import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import com.browntowndev.pocketcrew.presentation.screen.chat.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onCompletion


import javax.inject.Inject

/**
 * Use case for generating chat responses.
 * Routes to the appropriate service based on the selected mode:
 * - FAST: Uses Fast model for quick single-model responses
 * - THINKING: Uses Draft One model for reasoning responses
 * - CREW: Uses PipelineExecutorPort for multi-model pipeline
 */
class GenerateChatResponseUseCase @Inject constructor(
    @FastModelEngine private val fastModelService: LlmInferencePort,
    @DraftOneModelEngine private val draftOneModelService: LlmInferencePort,
    private val pipelineExecutor: PipelineExecutorPort,
    private val chatRepository: ChatRepository,
    private val bufferThinkingSteps: BufferThinkingStepsUseCase
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    operator fun invoke(prompt: String, messageId: String, mode: Mode): Flow<MessageGenerationState> {
        return when (mode) {
            Mode.FAST -> generateWithService(prompt, messageId, fastModelService)
            Mode.THINKING -> generateWithService(prompt, messageId, draftOneModelService)
            Mode.CREW -> pipelineExecutor.executePipeline(
                chatId = messageId.replace("_assistant", "_chat"),
                userMessage = prompt,
                assistantMessageId = messageId
            )
        }
    }

    private fun generateWithService(
        prompt: String,
        messageId: String,
        service: LlmInferencePort
    ): Flow<MessageGenerationState> = flow {
        val startTime = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()
        bufferThinkingSteps.reset()

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
                        messageId = messageId,
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
