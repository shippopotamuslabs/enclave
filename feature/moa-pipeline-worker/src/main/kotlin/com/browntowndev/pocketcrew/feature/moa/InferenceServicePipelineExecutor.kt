package com.browntowndev.pocketcrew.feature.moa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.feature.moa.service.InferenceService
import com.browntowndev.pocketcrew.feature.moa.service.InferenceServiceStarter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PipelineExecutorPort that uses InferenceService (custom foreground Service)
 * to execute the Crew pipeline (multi-model inference pipeline) in the background.
 *
 * This replaces the WorkManager-based approach which used dataSync foreground type
 * that has a 6-hour quota limit on Android 15+. The new approach uses specialUse
 * foreground type which has no quota limits.
 */
@Singleton
class InferenceServicePipelineExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val serviceStarter: InferenceServiceStarter
) : PipelineExecutorPort {

    override fun executePipeline(
        chatId: String,
        userMessage: String,
    ): Flow<MessageGenerationState> = callbackFlow {
        val initialState = PipelineState.createInitial(chatId, userMessage)
        val stateJson = initialState.toJson()

        // Track thinking content as raw text (no chunking)
        val thinkingBuffer = StringBuilder()

        // Track timing for thinking duration calculation
        val thinkingStartTimeRef = longArrayOf(0L)  // When first thinking chunk arrives
        val thinkingEndTimeRef = longArrayOf(0L)    // When first visible text (stepOutput) arrives

        // Create broadcast receiver for progress updates
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return

                when (intent.action) {
                    InferenceService.BROADCAST_PROGRESS -> {
                        handleProgressIntent(intent, thinkingBuffer, thinkingStartTimeRef, thinkingEndTimeRef) { state ->
                            trySend(state)
                        }
                    }
                    InferenceService.BROADCAST_COMPLETE -> {
                        handleCompleteIntent(intent, thinkingBuffer) { state ->
                            trySend(state)
                            // Don't close here - wait for BROADCAST_STEP_COMPLETED to close
                            // This ensures StepCompleted is processed with thinking data before closing
                        }
                    }
                    InferenceService.BROADCAST_ERROR -> {
                        val error = intent.getStringExtra(InferenceService.EXTRA_ERROR_MESSAGE)
                            ?: "Unknown error"
                        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
                        val modelType = modelTypeName?.let {
                            try {
                                ModelType.valueOf(it)
                            } catch (e: Exception) {
                                ModelType.MAIN
                            }
                        } ?: ModelType.MAIN
                        trySend(MessageGenerationState.Failed(IllegalStateException(error), modelType))
                        close()
                    }
                    InferenceService.BROADCAST_STEP_COMPLETED -> {
                        handleStepCompletedIntent(intent, thinkingBuffer, thinkingStartTimeRef, thinkingEndTimeRef) { state ->
                            trySend(state)
                            // Close flow after FINAL StepCompleted is processed (with thinking data)
                            val stepTypeName = intent.getStringExtra(InferenceService.EXTRA_STEP_TYPE)
                            if (stepTypeName == PipelineStep.FINAL.name) {
                                close()
                            }
                        }
                    }
                }
            }
        }

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(InferenceService.BROADCAST_PROGRESS)
            addAction(InferenceService.BROADCAST_COMPLETE)
            addAction(InferenceService.BROADCAST_ERROR)
            addAction(InferenceService.BROADCAST_STEP_COMPLETED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Start the service
        try {
            serviceStarter.startService(chatId, userMessage, stateJson)
        } catch (e: Exception) {
            trySend(MessageGenerationState.Failed(e, ModelType.DRAFT_ONE))
            close()
            return@callbackFlow
        }

        // Wait for completion or cancellation
        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    private fun handleProgressIntent(
        intent: Intent,
        thinkingBuffer: StringBuilder,
        thinkingStartTimeRef: LongArray,  // [0] = start time
        thinkingEndTimeRef: LongArray,    // [0] = end time
        send: (MessageGenerationState) -> Unit
    ) {
        val thinkingChunk = intent.getStringExtra(InferenceService.EXTRA_THINKING_CHUNK)
        val stepOutput = intent.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT)
        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
        val modelType = modelTypeName?.let {
            try {
                ModelType.valueOf(it)
            } catch (e: Exception) {
                ModelType.MAIN
            }
        } ?: ModelType.MAIN

        // Track thinking start time - first thinking chunk marks the beginning
        if (thinkingChunk != null && thinkingStartTimeRef[0] == 0L) {
            thinkingStartTimeRef[0] = System.currentTimeMillis()
        }

        // FIX: Emit GeneratingText for partial responses during ALL steps (not just FINAL)
        // This shows the "Generating..." indicator while the step is running
        if (!stepOutput.isNullOrBlank()) {
            // Track thinking end time - first visible text marks the end of thinking
            if (thinkingEndTimeRef[0] == 0L) {
                thinkingEndTimeRef[0] = System.currentTimeMillis()
            }
            send(MessageGenerationState.GeneratingText(stepOutput, modelType))
        }

        // Handle thinking chunk - accumulate raw text
        if (thinkingChunk != null) {
            thinkingBuffer.append(thinkingChunk)
            // Emit thinking with raw text
            send(MessageGenerationState.ThinkingLive(thinkingBuffer.toString(), modelType))
        }
    }

    private fun handleCompleteIntent(
        intent: Intent,
        thinkingBuffer: StringBuilder,
        complete: (MessageGenerationState) -> Unit
    ) {
        val finalResponse = intent.getStringExtra(InferenceService.EXTRA_FINAL_RESPONSE)
        val isFinalStep = intent.getBooleanExtra(InferenceService.EXTRA_IS_FINAL_STEP, true)

        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
        val modelType = modelTypeName?.let {
            try {
                ModelType.valueOf(it)
            } catch (e: Exception) {
                ModelType.MAIN
            }
        } ?: ModelType.MAIN

        // FIX: For FINAL step, don't emit GeneratingText here since handleProgressIntent
        // already emits it during the step for streaming effect.
        // For non-FINAL steps, we still emit GeneratingText at step completion.
        if (!isFinalStep && !finalResponse.isNullOrBlank()) {
            complete(MessageGenerationState.GeneratingText(finalResponse, modelType))
        }

        // Only emit Finished for the final step - for non-final steps, we continue to next step
        if (isFinalStep) {
            complete(MessageGenerationState.Finished(modelType))
        }
    }

    private fun handleStepCompletedIntent(
        intent: Intent,
        thinkingBuffer: StringBuilder,
        thinkingStartTimeRef: LongArray,
        thinkingEndTimeRef: LongArray,
        send: (MessageGenerationState) -> Unit
    ) {
        val stepOutput = intent.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) ?: ""
        val reportedDuration = intent.getIntExtra(InferenceService.EXTRA_STEP_DURATION, 0)
        val totalDurationSeconds = intent.getIntExtra(InferenceService.EXTRA_STEP_TOTAL_DURATION, 0)
        val modelDisplayName = intent.getStringExtra(InferenceService.EXTRA_STEP_MODEL_DISPLAY_NAME) ?: ""
        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
        val modelType = modelTypeName?.let {
            try {
                ModelType.valueOf(it)
            } catch (e: Exception) {
                ModelType.MAIN
            }
        } ?: ModelType.MAIN

        val stepTypeName = intent.getStringExtra(InferenceService.EXTRA_STEP_TYPE)
        val stepType = stepTypeName?.let {
            try {
                PipelineStep.valueOf(it)
            } catch (e: Exception) {
                PipelineStep.DRAFT_ONE
            }
        } ?: PipelineStep.DRAFT_ONE

        // Use buffered thinking (raw markdown)
        val thinkingRaw = thinkingBuffer.toString()

        // Calculate thinking duration from our tracked timing
        val thinkingDurationSeconds = if (thinkingRaw.isNotBlank() && reportedDuration == 0
            && thinkingStartTimeRef[0] > 0 && thinkingEndTimeRef[0] > 0) {
            ((thinkingEndTimeRef[0] - thinkingStartTimeRef[0]) / 1000).toInt()
        } else {
            reportedDuration
        }

        // Reset timing for next step
        thinkingStartTimeRef[0] = 0L
        thinkingEndTimeRef[0] = 0L

        // Clear thinking buffer for the next step - each step has independent thinking
        thinkingBuffer.clear()

        // Emit StepCompleted state - stepName is derived from stepType
        send(
            MessageGenerationState.StepCompleted(
                stepOutput = stepOutput,
                thinkingDurationSeconds = thinkingDurationSeconds,              // Thinking time only
                totalDurationSeconds = totalDurationSeconds,  // Total time
                thinkingRaw = thinkingRaw,  // Use raw markdown thinking
                modelDisplayName = modelDisplayName,
                modelType = modelType,
                stepType = stepType
            )
        )
    }
}
