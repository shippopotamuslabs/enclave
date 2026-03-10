package com.browntowndev.pocketcrew.domain.usecase.chat

import com.browntowndev.pocketcrew.domain.port.inference.ComplexityLevel
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.HeuristicPromptComplexityInterpreter
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext


import javax.inject.Inject

class GenerateChatResponseUseCase @Inject constructor(
    private val complexityInterpreter: HeuristicPromptComplexityInterpreter,
    private val thinkingPipelineOrchestrator: EnginePipelineOrchestrator,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    operator fun invoke(prompt: String, messageId: String): Flow<MessageGenerationState> = flow {
        val startTime = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()

        // Step 1: Evaluate complexity using heuristic interpreter
        val complexityLevel = complexityInterpreter.analyze(prompt)

        // Step 2: Route based on complexity evaluation
        val pipelineFlow = if (complexityLevel == ComplexityLevel.SIMPLE) {
            // Use fast/simple path for simple prompts
            thinkingPipelineOrchestrator.processSimplePrompt(prompt)
        } else {
            // Use full pipeline for medium, complex, and reasoning prompts
            thinkingPipelineOrchestrator.processComplexPrompt(prompt)
        }

        pipelineFlow.collect { pipelineEvent ->
            when (pipelineEvent) {
                is PipelineEvent.PhaseUpdate -> {
                    currentSteps.add(pipelineEvent.phase.name)
                    emit(MessageGenerationState.ThinkingLive(currentSteps.toList()))
                }

                is PipelineEvent.ReasoningChunk -> {
                    val latestStep = extractAndTruncateStep(pipelineEvent.chunk, maxWords = 10)
                    if (latestStep.isNotBlank() && latestStep != currentSteps.lastOrNull()) {
                        currentSteps.add(latestStep)
                    }
                    emit(MessageGenerationState.ThinkingLive(currentSteps.toList()))
                }

                is PipelineEvent.TextChunk -> {
                    emit(MessageGenerationState.GeneratingText(pipelineEvent.chunk))
                }

                is PipelineEvent.SafetyIntervention -> {
                    emit(MessageGenerationState.Blocked(pipelineEvent.reason))
                }

                is PipelineEvent.Completed -> {
                    val duration = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                    val finalThinkingData = if (pipelineEvent.allThinkingSteps.isNotEmpty()) {
                        ThinkingData(
                            durationSeconds = duration,
                            steps = pipelineEvent.allThinkingSteps,
                            rawFullThought = pipelineEvent.allThinkingSteps.joinToString(" -> ")
                        )
                    } else null

                    chatRepository.saveAssistantMessage(
                        messageId = messageId,
                        content = pipelineEvent.finalResponse,
                        thinkingData = finalThinkingData
                    )

                    emit(MessageGenerationState.Finished)
                }

                is PipelineEvent.Error -> {
                    emit(MessageGenerationState.Failed(pipelineEvent.cause))
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
