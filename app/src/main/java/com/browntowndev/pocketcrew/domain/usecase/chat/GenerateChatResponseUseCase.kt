package com.browntowndev.pocketcrew.domain.usecase.chat

import android.util.Log
import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.model.ThinkingData
import com.browntowndev.pocketcrew.domain.port.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext


import javax.inject.Inject
import javax.inject.Provider

class GenerateChatResponseUseCase @Inject constructor(
    @FastModelEngine private val fastModelServiceProvider: Provider<LlmInferencePort>,
    private val thinkingPipelineOrchestrator: EnginePipelineOrchestrator,
    private val safetyProbe: SafetyProbe,
    private val chatRepository: ChatRepository
) {
    companion object {
        private const val TAG = "GenerateChatResponse"
    }

    operator fun invoke(prompt: String, messageId: String): Flow<MessageGenerationState> = flow {
        val startTime = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()

        val fastModelService = withContext(Dispatchers.IO) {
            fastModelServiceProvider.get()
        }

        try {
            // Step 1: Evaluate complexity using draft service
            val complexityPrompt = buildComplexityPrompt(prompt)

            var requiresReasoning = false

            /*fastModelService.sendPrompt(complexityPrompt, closeConversation = true).collect { event ->
                when (event) {
                    is InferenceEvent.Thinking -> {
                        // Ignore thinking during complexity check
                    }
                    is InferenceEvent.PartialResponse -> {
                        // Ignore partial responses during complexity check
                    }
                    is InferenceEvent.Completed -> {
                        requiresReasoning = parseComplexityResponse(event.finalResponse)
                    }
                    is InferenceEvent.SafetyBlocked -> {
                        emit(MessageGenerationState.Blocked(event.reason))
                        return@collect
                    }
                    is InferenceEvent.Error -> {
                        // Default to reasoning on error for safety
                        requiresReasoning = true
                    }
                }
            }*/

            // Step 2: Route based on complexity evaluation
            if (requiresReasoning) {
                fastModelService.closeSession()

                // Call thinking pipeline orchestrator
                thinkingPipelineOrchestrator.processPrompt(prompt).collect { pipelineEvent ->
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

                            val finalThinkingData = ThinkingData(
                                durationSeconds = duration,
                                steps = pipelineEvent.allThinkingSteps,
                                rawFullThought = pipelineEvent.allThinkingSteps.joinToString(" -> ")
                            )

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
            } else {
                // Continue with simple draft inference
                fastModelService.sendPrompt(prompt).collect { event ->
                    when (event) {
                        is InferenceEvent.Thinking -> {
                            if (!safetyProbe.isSafe(event.accumulatedThought)) {
                                emit(MessageGenerationState.Blocked("Potential harm detected in reasoning."))
                                return@collect
                            }

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

                            val finalThinkingData = event.rawFullThought?.let {
                                ThinkingData(
                                    durationSeconds = duration,
                                    steps = currentSteps,
                                    rawFullThought = it
                                )
                            }

                            chatRepository.saveAssistantMessage(
                                messageId = messageId,
                                content = event.finalResponse,
                                thinkingData = finalThinkingData
                            )

                            emit(MessageGenerationState.Finished)
                        }

                        is InferenceEvent.SafetyBlocked -> emit(MessageGenerationState.Blocked(event.reason))
                        is InferenceEvent.Error -> emit(MessageGenerationState.Failed(event.cause))
                    }
                }
            }
        } finally {
            // Always close draft session
            fastModelService.closeSession()
        }
    }.flowOn(Dispatchers.Default)

    private fun buildComplexityPrompt(prompt: String): String {
        return """
            You are a strict binary classifier. 
            Analyze ONLY whether the user prompt REQUIRES REASONING.
    
            DEFINITION:
            requires_reasoning = true if the task needs any of these:
            - Multi-step logic, analysis, critique, synthesis, or counter-arguments
            - Applying knowledge creatively or in a specific style/voice
            - Step-by-step derivation or problem-solving
            - Non-trivial philosophical, ethical, scientific, or strategic thinking
    
            requires_reasoning = false only for:
            - Pure factual recall or lookup
            - Simple list generation
            - Basic creative writing without depth/analysis (e.g. "write a short story about cats")
            - Template-style responses with no real thinking
    
            Respond with EXACTLY this JSON and nothing else (no explanations, no markdown, no extra text):
            {"requires_reasoning": true/false, "reason": "one short sentence explaining your decision"}
            
            PROMPT TO ANALYZE:
            $prompt
        """.trimIndent()
    }

    private fun parseComplexityResponse(response: String): Boolean {
        return try {
            Log.d(TAG, "Complexity Response: $response")
            val jsonString = response.trim()
            val requiresReasoning = jsonString.contains("true", ignoreCase = true) &&
                !jsonString.contains("\"requires_reasoning\": false", ignoreCase = true)
            requiresReasoning
        } catch (e: Exception) {
            // Default to reasoning on parse error for safety
            true
        }
    }

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
