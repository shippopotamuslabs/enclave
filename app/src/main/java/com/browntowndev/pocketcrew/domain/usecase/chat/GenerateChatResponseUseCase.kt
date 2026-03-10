package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn


import javax.inject.Inject

/**
 * Use case for generating chat responses using the Fast model.
 * This is used for Fast mode in the UI.
 * Crew mode is handled by InferencePipelineWorker.
 */
class GenerateChatResponseUseCase @Inject constructor(
    @FastModelEngine private val fastModelService: LlmInferencePort,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    operator fun invoke(prompt: String, messageId: String): Flow<MessageGenerationState> = flow {
        val startTime = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()

        fastModelService.sendPrompt(prompt, closeConversation = false).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    val latestStep = extractAndTruncateStep(event.chunk, maxWords = 10)
                    if (latestStep.isNotBlank() && latestStep != currentSteps.lastOrNull()) {
                        currentSteps.add(latestStep)
                        emit(MessageGenerationState.ThinkingLive(currentSteps.toList()))
                    }
                }

                is InferenceEvent.PartialResponse -> {
                    emit(MessageGenerationState.GeneratingText(event.chunk))
                }

                is InferenceEvent.Completed -> {
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

    private fun extractAndTruncateStep(chunk: String, maxWords: Int): String {
        val words = chunk.trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) chunk.trim() else "${
            words.take(maxWords).joinToString(" ")
        }..."
    }
}

sealed interface MessageGenerationState {
    data class ThinkingLive(val steps: List<String>) : MessageGenerationState
    data class GeneratingText(val textDelta: String) : MessageGenerationState
    object Finished : MessageGenerationState
    data class Blocked(val reason: String) : MessageGenerationState
    data class Failed(val error: Throwable) : MessageGenerationState
}
