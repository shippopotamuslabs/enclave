package com.browntowndev.pocketcrew.inference

import android.util.Log
import com.browntowndev.pocketcrew.domain.model.chat.ThinkingData
import com.browntowndev.pocketcrew.domain.port.inference.AgentRole
import com.browntowndev.pocketcrew.domain.port.inference.EnginePipelineOrchestrator
import com.browntowndev.pocketcrew.domain.port.inference.PipelineEvent
import com.browntowndev.pocketcrew.domain.port.inference.PipelinePhase
import com.browntowndev.pocketcrew.app.DraftOneModelEngine
import com.browntowndev.pocketcrew.app.DraftTwoModelEngine
import com.browntowndev.pocketcrew.app.FastModelEngine
import com.browntowndev.pocketcrew.app.MainModelEngine
import com.browntowndev.pocketcrew.app.VisionModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.repository.ModelRegistryPort
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
    private val modelRegistry: ModelRegistryPort
) : EnginePipelineOrchestrator {

    companion object {
        const val TAG = "PipelineOrchestrator"
    }

    override fun processComplexPrompt(prompt: String, hasImage: Boolean): Flow<PipelineEvent> = flow {
        Log.d(TAG, "Processing prompt: $prompt")

        val startMs = System.currentTimeMillis()
        val allThinkingSteps = mutableListOf<String>()

        // Get Fast model's persona for the critique phase
        val fastConfig = modelRegistry.getRegisteredModel(ModelType.FAST)
        val fastPersona = fastConfig?.persona?.systemPrompt ?: "You are a helpful assistant."
        Log.d(TAG, "Fast persona: $fastPersona")

        try {
            // ==========================================
            // PHASE 1: DRAFTING (2 Divergent Drafts)
            // ==========================================
            emitPhase(PipelinePhase.DRAFTING, allThinkingSteps)

            // Draft 1: Creative/lateral thinking - divergent perspective
            // Load Draft One service only when needed
            val draftOneService = withContext(Dispatchers.IO) {
                draftOneServiceProvider.get()
            }
            try {
                val draft1 = executeAgent(
                    service = draftOneService,
                    agent = AgentRole.DRAFTER_ONE,
                    prompt = """
                        SYSTEM: You are highly creative and divergent. Think wildly outside the box.
                        Brainstorm a broad, lateral-thinking response to this prompt. Explore unconventional angles,
                        unexpected connections, and creative possibilities that others might miss.
                        Consider metaphors, analogies, and perspectives from entirely different domains.

                        Original prompt: $prompt

                        Provide your most creative, imaginative response.
                    """.trimIndent()
                )
                Log.d(TAG, "Draft 1 (Creative): $draft1")

                // Unload Draft One from RAM immediately after use
                draftOneService.closeSession()

                // ==========================================
                // PHASE 2: DRAFTING - Second Draft
                // ==========================================

                // Draft 2: Analytical/convergent thinking - divergent perspective
                // Load Draft Two service only when needed
                val draftTwoService = withContext(Dispatchers.IO) {
                    draftTwoServiceProvider.get()
                }

                try {
                    val draft2 = executeAgent(
                        service = draftTwoService,
                        agent = AgentRole.DRAFTER_TWO,
                        prompt = """
                            SYSTEM: You are strictly analytical and convergent. Think with precision and depth.
                            Provide a rigorous, structured, logical response to this prompt. Break down the problem
                            into its components, analyze each carefully, and build a methodical argument.

                            Focus on factual accuracy, logical coherence, and thorough reasoning.
                            Consider edge cases and potential counterarguments.

                            Original prompt: $prompt

                            Provide your most analytical, well-reasoned response.
                        """.trimIndent()
                    )
                    Log.d(TAG, "Draft 2 (Analytical): $draft2")

                    // Unload Draft Two from RAM immediately after use
                    draftTwoService.closeSession()

                    // ==========================================
                    // PHASE 2: MAIN MODEL RESPONSE + CRITIQUE
                    // ==========================================
                    emitPhase(PipelinePhase.SYNTHESIS, allThinkingSteps)

                    // ==========================================
                    // PHASE 3: FINAL OUTPUT
                    // ==========================================
                    emitPhase(PipelinePhase.REFINEMENT, allThinkingSteps)

                    // Main model gives its own answer first, then critiques all three (including its own)
                    // Load Main service only when needed
                    val mainService = withContext(Dispatchers.IO) {
                        mainServiceProvider.get()
                    }

                    try {
                        val finalResponse = executeAgent(
                            service = mainService,
                            agent = AgentRole.FINAL_THINKER,
                            prompt = """
                                You need to answer the user's prompt AND evaluate other responses as a critic.

                                ORIGINAL PROMPT: $prompt

                                YOUR OWN ANSWER (Internal):
                                First, provide your own direct answer to the above prompt. This is your independent response.

                                ---

                                CRITIQUE PHASE:
                                Now critically evaluate the following two drafts from different angles:

                                DRAFT 1 (Creative/Lateral):
                                $draft1

                                DRAFT 2 (Analytical/Convergent):
                                $draft2

                                For EACH draft, analyze:
                                1. Logical fallacies present (straw man, false dilemma, ad hominem, circular reasoning, etc.)
                                2. Missing premises or unstated assumptions
                                3. Incorrect reasoning or flawed logic
                                4. Mistaken claims or factual errors
                                5. Overclaims (making statements too broad or absolute)
                                6. Underclaims (failing to acknowledge nuances or limitations)
                                7. Missing counterarguments or alternative perspectives

                                Then, synthesize your own answer with the best elements from both drafts,
                                while addressing the weaknesses you identified. Maintain your logical rigor
                                while adapting your tone to match the persona: $fastPersona

                                Preserve all factual accuracy and logical reasoning - only adapt the communication style.
                            """.trimIndent(),
                            isFinalOutput = true
                        )
                        Log.d(TAG, "Final Response: $finalResponse")

                        // Pipeline Finished
                        val totalSeconds = ((System.currentTimeMillis() - startMs) / 1000).toInt()
                        Log.d(TAG, "Pipeline finished in $totalSeconds seconds")
                        emit(
                            PipelineEvent.Completed(
                                finalResponse = finalResponse,
                                allThinkingSteps = allThinkingSteps,
                                pipelineDurationSeconds = totalSeconds
                            )
                        )
                    } finally {
                        // Ensure Main service is unloaded
                        try { mainService.closeSession() } catch (e: Exception) { }
                    }
                } finally {
                    // Ensure Draft Two is unloaded if an error occurs
                    try { draftTwoService.closeSession() } catch (e: Exception) { }
                }
            } finally {
                // Ensure Draft One is unloaded if an error occurs
                try { draftOneService.closeSession() } catch (e: Exception) { }
            }

        } catch (e: CancellationException) {
            Log.i(TAG, "Pipeline cancelled")
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Pipeline error", e)
            throw e
        }
    }

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