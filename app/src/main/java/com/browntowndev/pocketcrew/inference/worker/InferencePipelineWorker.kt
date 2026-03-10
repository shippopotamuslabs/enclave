package com.browntowndev.pocketcrew.inference.worker

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.browntowndev.pocketcrew.app.DraftOneModelEngine
import com.browntowndev.pocketcrew.app.DraftTwoModelEngine
import com.browntowndev.pocketcrew.app.FinalSynthesizerModelEngine
import com.browntowndev.pocketcrew.app.MainModelEngine
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

/**
 * WorkManager worker for executing a single step in the Crew Mode pipeline.
 * Each worker execution processes one PipelineStep (DRAFT_ONE, DRAFT_TWO, SYNTHESIS, or FINAL).
 *
 * The worker:
 * 1. Runs as a foreground service with progress notifications
 * 2. Executes the inference for the current step
 * 3. Emits thinking chunks via OutputData for real-time UI updates
 * 4. Returns success with updated state (or next step ready to run)
 * 5. Returns retry on failure
 */
@HiltWorker
class InferencePipelineWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val logger: LoggingPort,
    private val notificationManager: InferenceNotificationManager,
    @DraftOneModelEngine private val draftOneServiceProvider: dagger.Lazy<LlmInferencePort>,
    @DraftTwoModelEngine private val draftTwoServiceProvider: dagger.Lazy<LlmInferencePort>,
    @MainModelEngine private val mainServiceProvider: dagger.Lazy<LlmInferencePort>,
    @FinalSynthesizerModelEngine private val finalSynthesizerServiceProvider: dagger.Lazy<LlmInferencePort>
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "InferencePipelineWorker"
        const val WORK_NAME_PREFIX = "pipeline_work_"

        /**
         * Unique work name for a chat session.
         */
        fun workName(chatId: String): String = "$WORK_NAME_PREFIX$chatId"
    }

    override suspend fun doWork(): Result {
        logger.info(TAG, "Starting pipeline worker")

        // Parse input state
        val stateJson = inputData.getString(InferenceNotificationManager.KEY_STATE_JSON)
            ?: return Result.failure(workDataOf("error" to "No pipeline state provided"))

        val state = try {
            PipelineState.fromJson(stateJson)
        } catch (e: Exception) {
            logger.error(TAG, "Failed to parse pipeline state: ${e.message}")
            return Result.failure(workDataOf("error" to "Invalid pipeline state"))
        }

        logger.info(TAG, "Executing step: ${state.currentStep.name} for chat: ${state.chatId}")

        // Create notification channel
        notificationManager.createNotificationChannel()

        // Set as foreground service with progress notification
        try {
            val cancelIntent = WorkManager.getInstance(applicationContext)
                .createCancelPendingIntent(id)
            val foregroundInfo = notificationManager.createForegroundInfo(
                currentStep = state.currentStep,
                cancelPendingIntent = cancelIntent
            )
            setForeground(foregroundInfo)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            logger.warning(TAG, "Foreground not allowed, returning retry: ${e.message}")
            return Result.retry()
        }

        // Execute the current step
        return try {
            val result = executeStep(state)

            // Check if this is the final step
            if (state.currentStep == PipelineStep.FINAL) {
                // Final step complete - return success with final response
                logger.info(TAG, "Pipeline complete for chat: ${state.chatId}")
                Result.success(workDataOf(
                    PipelineState.KEY_FINAL_RESPONSE to result.output,
                    PipelineState.KEY_DURATION_SECONDS to state.durationSeconds(),
                    PipelineState.KEY_ALL_THINKING_STEPS_JSON to result.allThinkingStepsJson
                ))
            } else {
                // More steps to go - return success with next step state
                val nextState = state.withStepOutput(state.currentStep, result.output)
                    .withNextStep()

                if (nextState != null) {
                    Result.success(workDataOf(
                        InferenceNotificationManager.KEY_STATE_JSON to nextState.toJson(),
                        PipelineState.KEY_STEP_OUTPUT to result.output,
                        PipelineState.KEY_THINKING_CHUNK to result.thinkingChunk
                    ))
                } else {
                    Result.failure(workDataOf("error" to "Next step is null but not final step"))
                }
            }
        } catch (e: CancellationException) {
            logger.info(TAG, "Pipeline cancelled for chat: ${state.chatId}")
            // Save partial state for potential resume
            Result.success(workDataOf(
                InferenceNotificationManager.KEY_STATE_JSON to state.toJson(),
                PipelineState.KEY_STEP_OUTPUT to "Partial progress saved"
            ))
        } catch (e: Exception) {
            logger.error(TAG, "Pipeline error for chat ${state.chatId}: ${e.message}", e)
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf(
                    "error" to (e.message ?: "Unknown error"),
                    PipelineState.KEY_ALL_THINKING_STEPS_JSON to state.thinkingSteps.joinToString("|||")
                ))
            }
        }
    }

    /**
     * Executes a single pipeline step.
     */
    private suspend fun executeStep(state: PipelineState): StepResult {
        val service = getServiceForStep(state.currentStep)
        val prompt = buildPromptForStep(state)

        var output = ""
        var thinkingChunk = ""
        val allThinkingSteps = state.thinkingSteps.toMutableList()

        try {
            service.sendPrompt(prompt, closeConversation = true).collect { event ->
                when (event) {
                    is InferenceEvent.Thinking -> {
                        thinkingChunk = event.chunk
                        // Emit thinking chunk for real-time UI update
                        setProgressAsync(workDataOf(
                            PipelineState.KEY_THINKING_CHUNK to thinkingChunk
                        ))
                    }
                    is InferenceEvent.PartialResponse -> {
                        // Only collect text for final output step
                        if (state.currentStep == PipelineStep.FINAL) {
                            output += event.chunk
                        }
                    }
                    is InferenceEvent.Completed -> {
                        output = event.finalResponse
                        allThinkingSteps.add("${state.currentStep.displayName()}: ${output.take(100)}...")

                        // Update notification with progress
                        updateNotification(state.currentStep, state.currentStep.next() != null)
                    }
                    is InferenceEvent.SafetyBlocked -> {
                        throw SecurityException("Content blocked: ${event.reason}")
                    }
                    is InferenceEvent.Error -> {
                        throw event.cause
                    }
                }
            }
        } finally {
            try {
                service.closeSession()
            } catch (e: Exception) {
                logger.warning(TAG, "Error closing service: ${e.message}")
            }
        }

        return StepResult(
            output = output,
            thinkingChunk = thinkingChunk,
            allThinkingStepsJson = allThinkingSteps.joinToString("|||")
        )
    }

    /**
     * Gets the appropriate inference service for the given step.
     */
    private fun getServiceForStep(step: PipelineStep): LlmInferencePort {
        return when (step) {
            PipelineStep.DRAFT_ONE -> draftOneServiceProvider.get()
            PipelineStep.DRAFT_TWO -> draftTwoServiceProvider.get()
            PipelineStep.SYNTHESIS -> mainServiceProvider.get()
            PipelineStep.FINAL -> finalSynthesizerServiceProvider.get()
        }
    }

    /**
     * Builds the prompt for the given step, incorporating previous outputs.
     */
    private fun buildPromptForStep(state: PipelineState): String {
        val userPrompt = state.userMessage

        return when (state.currentStep) {
            PipelineStep.DRAFT_ONE -> buildCreativeDraftPrompt(userPrompt)
            PipelineStep.DRAFT_TWO -> buildAnalyticalDraftPrompt(userPrompt)
            PipelineStep.SYNTHESIS -> buildMainSynthesisPrompt(
                userPrompt,
                state.stepOutputs[PipelineStep.DRAFT_ONE] ?: "",
                state.stepOutputs[PipelineStep.DRAFT_TWO] ?: ""
            )
            PipelineStep.FINAL -> buildFinalReviewPrompt(
                userPrompt,
                state.stepOutputs[PipelineStep.SYNTHESIS] ?: ""
            )
        }
    }

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

    /**
     * Updates the foreground notification with current progress.
     */
    private suspend fun updateNotification(currentStep: PipelineStep, hasMoreSteps: Boolean) {
        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)
        val foregroundInfo = notificationManager.createForegroundInfoForStep(
            currentStep = currentStep,
            hasMoreSteps = hasMoreSteps,
            cancelPendingIntent = cancelIntent
        )
        setForegroundAsync(foregroundInfo)
    }

    /**
     * Result of executing a single step.
     */
    private data class StepResult(
        val output: String,
        val thinkingChunk: String,
        val allThinkingStepsJson: String
    )
}
