package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import com.browntowndev.pocketcrew.app.DraftModelEngine
import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.app.MainModelEngine
import com.browntowndev.pocketcrew.app.VisionModelEngine
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

class PipelineOrchestratorImpl @Inject constructor(
    @param:MainModelEngine private val mainServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:DraftModelEngine private val draftServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:VisionModelEngine private val visionServiceProvider: dagger.Lazy<LlmInferencePort>,
    @param:FastModelEngine private val fastServiceProvider: dagger.Lazy<LlmInferencePort>
) : EnginePipelineOrchestrator {

    companion object {
        const val TAG = "PipelineOrchestrator"
    }

    override fun processPrompt(prompt: String, hasImage: Boolean): Flow<PipelineEvent> = flow {
        Log.d(TAG, "Processing prompt: $prompt")

        val startMs = System.currentTimeMillis()
        val allThinkingSteps = mutableListOf<String>()
        val mainService = mainServiceProvider.get()
        val draftService = draftServiceProvider.get()
        val visionService = visionServiceProvider.get()

        try {
            // ==========================================
            // PHASE 1: DRAFTING (4 Distinct Personas)
            // ==========================================
            emitPhase(PipelinePhase.DRAFTING, allThinkingSteps)

            val draft1 = executeAgent(
                service = draftService,
                agent = AgentRole.DRAFTER_ONE,
                prompt = """
                    SYSTEM: You are highly creative and divergent. 
                    Brainstorm a broad, lateral-thinking response to this prompt: $prompt
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Draft 1: $draft1")
            val draft2 = executeAgent(
                service = draftService,
                agent = AgentRole.DRAFTER_TWO,
                prompt = """
                    SYSTEM: You are strictly analytical and concise. 
                    Provide a logical, structured response to this prompt: $prompt
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Draft 2: $draft2")
            val draft3 = executeAgent(
                service = draftService,
                agent = AgentRole.DRAFTER_THREE,
                prompt = """
                    SYSTEM: You are a skeptic. 
                    Focus on edge cases, potential failures, and counter-arguments to this prompt: $prompt
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Draft 3: $draft3")
            val draft4 = executeAgent(
                service = draftService,
                agent = AgentRole.DRAFTER_FOUR,
                prompt = """
                    SYSTEM: You are a pragmatist. 
                    Provide the most direct, actionable, real-world solution to this prompt: $prompt
                    
                    <think>
                """.trimMargin()
            )
            Log.d(TAG, "Draft 4: $draft4")

            // Drop draft engine from RAM before loading Main for synthesis
            draftService.closeSession()

            // ==========================================
            // PHASE 2: FIRST SYNTHESIS (2 Synthesizers)
            // ==========================================
            emitPhase(PipelinePhase.SYNTHESIS, allThinkingSteps)

            // Drafts are already cleaned by LiteRtInferenceService via [begin_answer] markers
            // No additional stripping needed at pipeline level

            val synthesisA = executeAgent(
                service = mainService,
                agent = AgentRole.SYNTHESIZER_ONE,
                prompt = """
                    SYSTEM: Synthesize these two drafts into a single cohesive argument. 
                    Extract the best creative ideas from Draft 1 and the logical structure of Draft 2.
                    Original prompt these drafts answer: $prompt
                    Draft 1: $draft1
                    Draft 2: $draft2
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Synthesis A: $synthesisA")

            val synthesisB = executeAgent(
                service = mainService,
                agent = AgentRole.SYNTHESIZER_TWO,
                prompt = """
                    SYSTEM: Synthesize these two drafts. Merge the skeptical edge-cases of Draft 3 
                    with the actionable solutions of Draft 4.
                    Original prompt these drafts answer: $prompt
                    Draft 3: $draft3
                    Draft 4: $draft4
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Synthesis B: $synthesisB")

            // Free intermediate drafts for garbage collection
            var currentWorkingDraft = ""

            // ==========================================
            // PHASE 3: FINAL SYNTHESIS
            // ==========================================
            emitPhase(PipelinePhase.REFINEMENT, allThinkingSteps)

            // Syntheses are already cleaned by LiteRtInferenceService via [begin_answer] markers

            currentWorkingDraft = executeAgent(
                service = mainService,
                agent = AgentRole.FINAL_THINKER,
                prompt = """
                    SYSTEM: Merge Synthesis A and Synthesis B into a single, comprehensive master draft.
                    Original prompt these drafts answer: $prompt
                    Synthesis A: $synthesisA
                    Synthesis B: $synthesisB
                    
                    <think>
                """.trimIndent()
            )
            Log.d(TAG, "Final Draft: $currentWorkingDraft")

            // ==========================================
            // PHASE 4: RECURSIVE SELF-REFINE (4 Iterations)
            // ==========================================
            for (i in 1..4) {
                // Previous iteration results are already cleaned by LiteRtInferenceService

                currentWorkingDraft = executeAgent(
                    service = mainService,
                    agent = AgentRole.FINAL_THINKER,
                    prompt = """
                        SYSTEM: You are improving the following response (Iteration $i/4).
                        1. Identify weaknesses, inaccuracies, or missing logic.
                        2. Output explicitly what you changed and what you learned.
                        3. Output the improved response.
                        Original prompt the response answers: $prompt
                        Current Response: $currentWorkingDraft
                        
                    <think>
                    """.trimIndent()
                )
                Log.d(TAG, "Iteration $i: $currentWorkingDraft")
            }

            // ==========================================
            // PHASE 5: FINAL CLEANUP & EMISSION
            // ==========================================
            val finalCleanResponse = executeAgent(
                service = mainService,
                agent = AgentRole.SYSTEM,
                prompt = """
                    SYSTEM: Extract ONLY the final, polished response from the following text. 
                    Strip out all notes about what was learned, changed, or improved. 
                    Output only the direct answer to the user's original prompt: "$prompt".
                    Text to clean: $currentWorkingDraft
                    
                    <think>
                """.trimIndent(),
                isFinalOutput = true
            )
            Log.d(TAG, "Final Response: $finalCleanResponse")

            // Pipeline Finished
            val totalSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
            Log.d(TAG, "Pipeline finished in $totalSeconds seconds")
            emit(
                PipelineEvent.Completed(
                    finalResponse = finalCleanResponse,
                    allThinkingSteps = allThinkingSteps,
                    pipelineDurationSeconds = totalSeconds
                )
            )

        } catch (e: CancellationException) {
            Log.i(TAG, "Pipeline cancelled")
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Pipeline cancelled", e)
            emit(PipelineEvent.Error(e))
        } finally {
            // Guarantee RAM is freed
            draftService.closeSession()
            mainService.closeSession()
            visionService.closeSession()
        }
    }

    override fun cancelPipeline() {
        // Can't safely retrieve lazy services in cancel context without initializing them.
        // In practice this class would likely hold onto references once loaded.
        // For now, doing nothing is safer than initializing just to close them.
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