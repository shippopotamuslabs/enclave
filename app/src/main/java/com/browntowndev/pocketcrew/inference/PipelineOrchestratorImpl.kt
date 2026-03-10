package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import com.browntowndev.pocketcrew.app.DraftOneModelEngine
import com.browntowndev.pocketcrew.app.DraftTwoModelEngine
import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.app.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.app.MainModelEngine
import com.browntowndev.pocketcrew.app.VisionModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

class PipelineOrchestratorImpl @Inject constructor(
    @param:MainModelEngine private val mainServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:DraftOneModelEngine private val draftOneServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:DraftTwoModelEngine private val draftTwoServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:VisionModelEngine private val visionServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:FastModelEngine private val fastServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:FinalSynthesizerModelEngine private val finalSynthesizerServiceProvider: dagger.Lazy<LlmInferencePort>
) : EnginePipelineOrchestrator {

    companion object {
        const val TAG = "PipelineOrchestrator"
    }

    /**
     * State machine for the complex prompt pipeline.
     * Each state represents a pipeline phase with its service lifecycle.
     */
    private sealed class PipelineState {
        data object Idle : PipelineState()
        data class DraftOne(val prompt: String) : PipelineState()
        data class DraftTwo(val prompt: String, val draft1: String) : PipelineState()
        data class MainSynthesis(val prompt: String, val draft1: String, val draft2: String) : PipelineState()
        data class FinalSynthesis(val prompt: String, val candidateAnswer: String) : PipelineState()
        data class Completed(val finalResponse: String, val durationSeconds: Int) : PipelineState()
        data class Error(val cause: Throwable) : PipelineState()
    }

    override fun processComplexPrompt(prompt: String, hasImage: Boolean): Flow<PipelineEvent> = flow {
        Log.d(TAG, "Processing complex prompt: $prompt")

        val startMs = System.currentTimeMillis()
        val allThinkingSteps = mutableListOf<String>()

        // Initialize pipeline state
        var state: PipelineState = PipelineState.DraftOne(prompt)

        try {
            // State transition: DRAFT_ONE -> DRAFT_TWO
            emitPhase(PipelinePhase.DRAFTING, allThinkingSteps)
            val draft1 = runStep(
                serviceProvider = draftOneServiceProvider,
                agent = AgentRole.DRAFTER_ONE,
                prompt = buildCreativeDraftPrompt(prompt),
                stepName = "Draft 1"
            )
            state = PipelineState.DraftTwo(prompt, draft1)

            // State transition: DRAFT_TWO -> MAIN_SYNTHESIS
            val draft2 = runStep(
                serviceProvider = draftTwoServiceProvider,
                agent = AgentRole.DRAFTER_TWO,
                prompt = buildAnalyticalDraftPrompt(prompt),
                stepName = "Draft 2"
            )
            state = PipelineState.MainSynthesis(prompt, draft1, draft2)

            // State transition: MAIN_SYNTHESIS -> FINAL_SYNTHESIS
            emitPhase(PipelinePhase.SYNTHESIS, allThinkingSteps)
            val candidateAnswer = runStep(
                serviceProvider = mainServiceProvider,
                agent = AgentRole.FINAL_THINKER,
                prompt = buildMainSynthesisPrompt(prompt, draft1, draft2),
                stepName = "Main Synthesis"
            )
            state = PipelineState.FinalSynthesis(prompt, candidateAnswer)

            // State transition: FINAL_SYNTHESIS -> COMPLETED
            emitPhase(PipelinePhase.REFINEMENT, allThinkingSteps)
            val finalResponse = runStep(
                serviceProvider = finalSynthesizerServiceProvider,
                agent = AgentRole.FINAL_SYNTHESIZER,
                prompt = buildFinalReviewPrompt(prompt, candidateAnswer),
                stepName = "Final Synthesis",
                isFinalOutput = true
            )

            val totalSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
            Log.d(TAG, "Pipeline finished in $totalSeconds seconds")

            emit(
                PipelineEvent.Completed(
                    finalResponse = finalResponse,
                    allThinkingSteps = allThinkingSteps,
                    pipelineDurationSeconds = totalSeconds
                )
            )
            state = PipelineState.Completed(finalResponse, totalSeconds)

        } catch (e: CancellationException) {
            Log.i(TAG, "Pipeline cancelled")
            state = PipelineState.Error(e)
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Pipeline error", e)
            state = PipelineState.Error(e)
            throw e
        }
    }

    /**
     * Executes a single pipeline step with proper service lifecycle management.
     * Loads the service, runs the agent, and ensures cleanup in all cases.
     */
    private suspend fun FlowCollector<PipelineEvent>.runStep(
        serviceProvider: dagger.Lazy<LlmInferencePort>,
        agent: AgentRole,
        prompt: String,
        stepName: String,
        isFinalOutput: Boolean = false
    ): String {
        val service = withContext(Dispatchers.IO) {
            serviceProvider.get()
        }
        return try {
            val result = executeAgent(
                service = service,
                agent = agent,
                prompt = prompt,
                isFinalOutput = isFinalOutput
            )
            Log.d(TAG, "$stepName complete: ${result.take(100)}...")
            result
        } finally {
            try {
                service.closeSession()
                Log.d(TAG, "$stepName service closed")
            } catch (e: Exception) {
                Log.w(TAG, "Error closing $stepName service", e)
            }
        }
    }

    // Prompt builders - clean, focused task descriptions
    private fun buildCreativeDraftPrompt(userPrompt: String): String = """
TASK: COMPLEX_DRAFT_CREATIVE

USER_PROMPT:
$userPrompt

OUTPUT_CONTRACT:
Produce a creative, divergent draft.
Prioritize breadth, novelty, unusual angles, metaphors, and lateral thinking.
Return a usable draft, not just brainstorming fragments.
    """.trimIndent()

    private fun buildAnalyticalDraftPrompt(userPrompt: String): String = """
TASK: COMPLEX_DRAFT_ANALYTICAL

USER_PROMPT:
$userPrompt

OUTPUT_CONTRACT:
Produce a rigorous draft.
Be structured, careful, logically explicit, and sensitive to edge cases.
Return a usable draft, not just notes.
    """.trimIndent()

    private fun buildMainSynthesisPrompt(userPrompt: String, draft1: String, draft2: String): String = """
TASK: COMPLEX_SYNTHESIZE

ORIGINAL_USER_PROMPT:
$userPrompt

DRAFT_1:
$draft1

DRAFT_2:
$draft2

OUTPUT_CONTRACT:
First evaluate the drafts critically.
Identify strengths, weaknesses, blind spots, false moves, and missing considerations.
Then synthesize the best candidate answer.
This is not necessarily the final user-facing answer; it is the strongest candidate for final review.
    """.trimIndent()

    private fun buildFinalReviewPrompt(userPrompt: String, candidateAnswer: String): String = """
TASK: FINAL_REVIEW_AND_REPLY
ORIGINAL_USER_PROMPT: $userPrompt
CANDIDATE_ANSWER: $candidateAnswer
    """.trimIndent()

    override fun processSimplePrompt(prompt: String): Flow<PipelineEvent> = flow {
        Log.d(TAG, "Processing simple prompt: $prompt")

        val startMs = System.currentTimeMillis()
        val currentSteps = mutableListOf<String>()

        val fastService = withContext(Dispatchers.IO) {
            fastServiceProvider.get()
        }
        try {
            fastService.sendPrompt(prompt, closeConversation = false).collect { event ->
                when (event) {
                    is InferenceEvent.Thinking -> {
                        val latestStep = extractAndTruncateStep(event.chunk, maxWords = 10)
                        if (latestStep.isNotBlank() && latestStep != currentSteps.lastOrNull()) {
                            currentSteps.add(latestStep)
                            emit(PipelineEvent.ReasoningChunk(AgentRole.FAST_MODEL, event.chunk, event.accumulatedThought))
                            emit(PipelineEvent.PhaseUpdate(PipelinePhase.FAST_INFERENCE))
                            emit(PipelineEvent.ReasoningChunk(AgentRole.FAST_MODEL, latestStep, currentSteps.joinToString(" -> ")))
                        }
                    }

                    is InferenceEvent.PartialResponse -> {
                        emit(PipelineEvent.TextChunk(AgentRole.FAST_MODEL, event.chunk, ""))
                    }

                    is InferenceEvent.Completed -> {
                        val duration = ((System.currentTimeMillis() - startMs) / 1000).toInt()

                        emit(
                            PipelineEvent.Completed(
                                finalResponse = event.finalResponse,
                                allThinkingSteps = currentSteps,
                                pipelineDurationSeconds = duration
                            )
                        )
                    }

                    is InferenceEvent.SafetyBlocked -> {
                        emit(PipelineEvent.SafetyIntervention(event.reason, AgentRole.WATCHDOG))
                    }

                    is InferenceEvent.Error -> {
                        emit(PipelineEvent.Error(event.cause))
                    }
                }
            }
        } finally {
            try { fastService.closeSession() } catch (e: Exception) { }
        }
    }

    private fun extractAndTruncateStep(chunk: String, maxWords: Int): String {
        val words = chunk.trim().split(Regex("\\s+"))
        return if (words.size <= maxWords) chunk.trim() else "${
            words.take(maxWords).joinToString(" ")
        }..."
    }

    override fun cancelPipeline() {
        // Pipeline uses lazy loading - services are unloaded after each use
        // No persistent references to clean up
    } 
    /**
     * Helper to map the raw InferenceEvents from the Service layer into PipelineEvents for the UI,
     * while collecting the complete text string to pass to the next phase of the pipeline.
     */
    private suspend fun FlowCollector<PipelineEvent>.executeAgent(
        service: LlmInferencePort,
        agent: AgentRole,
        prompt: String,
        isFinalOutput: Boolean = false
    ): String {
        var fullResponse = ""

        service.sendPrompt(prompt, closeConversation = true).collect { event ->
            when (event) {
                is InferenceEvent.Thinking -> {
                    emit(PipelineEvent.ReasoningChunk(agent, event.chunk, event.accumulatedThought))
                }
                is InferenceEvent.PartialResponse -> {
                    // We only stream the text to the UI if it's the final cleanup phase.
                    // Otherwise, intermediate drafts remain hidden from the main chat bubble.
                    if (isFinalOutput) {
                        emit(PipelineEvent.TextChunk(agent, event.chunk, fullResponse + event.chunk))
                    }
                }
                is InferenceEvent.Completed -> {
                    fullResponse = event.finalResponse
                }
                is InferenceEvent.SafetyBlocked -> {
                    emit(PipelineEvent.SafetyIntervention(event.reason, AgentRole.WATCHDOG))
                }
                is InferenceEvent.Error -> throw event.cause
            }
        }
        return fullResponse
    }

    private suspend fun FlowCollector<PipelineEvent>.emitPhase(
        phase: PipelinePhase,
        stepList: MutableList<String>
    ) {
        val phaseName = phase.name // You will map this to the UI string in the presentation layer
        stepList.add(phaseName)
        emit(PipelineEvent.PhaseUpdate(phase))
    }
}