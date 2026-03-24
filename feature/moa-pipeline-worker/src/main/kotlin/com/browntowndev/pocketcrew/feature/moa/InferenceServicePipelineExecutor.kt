package com.browntowndev.pocketcrew.feature.moa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.model.inference.PipelineStep
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.PipelineStateRepository
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.port.inference.LoggingPort
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
    private val serviceStarter: InferenceServiceStarter,
    private val pipelineStateRepository: PipelineStateRepository,
    private val loggingPort: LoggingPort
) : PipelineExecutorPort {

    companion object {
        private const val TAG = "InferenceServicePipelineExecutor"
    }

    override fun executePipeline(
        chatId: String,
        userMessage: String,
    ): Flow<MessageGenerationState> = callbackFlow {
        val initialState = PipelineState.createInitial(chatId, userMessage)
        val stateJson = initialState.toJson()

        // Create broadcast receiver for progress updates
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return

                when (intent.action) {
                    InferenceService.BROADCAST_STEP_STARTED -> {
                        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
                        val modelType = modelTypeName?.let {
                            try {
                                ModelType.valueOf(it)
                            } catch (e: Exception) {
                                loggingPort.error(TAG, "Invalid model type: $modelTypeName")
                                ModelType.MAIN
                            }
                        } ?: ModelType.MAIN
                        trySend(MessageGenerationState.Processing(modelType))
                    }
                    InferenceService.BROADCAST_PROGRESS -> {
                        handleProgressIntent(intent) { state ->
                            trySend(state)
                        }
                    }
                    InferenceService.BROADCAST_STEP_COMPLETED -> {
                        handleStepCompletedIntent(intent) { state ->
                            trySend(state)
                            // Close flow after FINAL StepCompleted is processed (with thinking data)
                            val stepTypeName = intent.getStringExtra(InferenceService.EXTRA_STEP_TYPE)
                            if (stepTypeName == PipelineStep.FINAL.name) {
                                close()
                            }
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
                }
            }
        }

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(InferenceService.BROADCAST_PROGRESS)
            addAction(InferenceService.BROADCAST_ERROR)
            addAction(InferenceService.BROADCAST_STEP_COMPLETED)
            addAction(InferenceService.BROADCAST_STEP_STARTED)
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

    override suspend fun stopPipeline(pipelineId: String) {
        // Send stop broadcast to InferenceService
        val stopIntent = Intent(InferenceService.ACTION_STOP).apply {
            setPackage(context.packageName)
            putExtra(InferenceService.EXTRA_CHAT_ID, pipelineId)
        }
        context.sendBroadcast(stopIntent)
        
        // Clear saved state
        pipelineStateRepository.clearPipelineState(pipelineId)
    }

    override suspend fun resumeFromState(
        chatId: String,
        pipelineId: String,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Flow<MessageGenerationState> = callbackFlow {
        // Retrieve saved state
        val savedState = pipelineStateRepository.getPipelineState(chatId)
        
        if (savedState == null) {
            trySend(MessageGenerationState.Failed(
                IllegalStateException("No saved pipeline state found for resume"),
                ModelType.MAIN
            ))
            close()
            return@callbackFlow
        }

        // Create broadcast receiver for progress updates
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return

                when (intent.action) {
                    InferenceService.BROADCAST_PROGRESS -> {
                        handleProgressIntent(intent) { state ->
                            trySend(state)
                        }
                    }
                    InferenceService.BROADCAST_ERROR -> {
                        val error = intent.getStringExtra(InferenceService.EXTRA_ERROR_MESSAGE)
                            ?: "Unknown error"
                        trySend(MessageGenerationState.Failed(IllegalStateException(error), ModelType.MAIN))
                        close()
                    }
                    InferenceService.BROADCAST_STEP_COMPLETED -> {
                        handleStepCompletedIntent(intent) { state ->
                            trySend(state)
                            // Close flow after FINAL StepCompleted is processed
                            val stepTypeName = intent.getStringExtra(InferenceService.EXTRA_STEP_TYPE)
                            if (stepTypeName == PipelineStep.FINAL.name) {
                                close()
                            }
                        }
                    }
                    InferenceService.BROADCAST_STEP_STARTED -> {
                        val modelTypeName = intent.getStringExtra(InferenceService.EXTRA_MODEL_TYPE)
                        val modelType = modelTypeName?.let {
                            try {
                                ModelType.valueOf(it)
                            } catch (e: Exception) {
                                ModelType.MAIN
                            }
                        } ?: ModelType.MAIN
                        trySend(MessageGenerationState.Processing(modelType))
                    }
                }
            }
        }

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(InferenceService.BROADCAST_PROGRESS)
            addAction(InferenceService.BROADCAST_ERROR)
            addAction(InferenceService.BROADCAST_STEP_COMPLETED)
            addAction(InferenceService.BROADCAST_STEP_STARTED)
        }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // Start the service with resume intent
        try {
            serviceStarter.startServiceResume(chatId, savedState.toJson())
        } catch (e: Exception) {
            onError(e)
            trySend(MessageGenerationState.Failed(e, ModelType.MAIN))
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

        if (!thinkingChunk.isNullOrEmpty()) {
            send(MessageGenerationState.ThinkingLive(thinkingChunk, modelType))
        }
        else if (!stepOutput.isNullOrEmpty()) {
            send(MessageGenerationState.GeneratingText(stepOutput, modelType))
        }
    }

    private fun handleStepCompletedIntent(
        intent: Intent,
        send: (MessageGenerationState) -> Unit
    ) {
        val stepOutput = intent.getStringExtra(InferenceService.EXTRA_STEP_OUTPUT) ?: ""
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
                loggingPort.error(TAG, "Invalid step type: $stepTypeName")
                PipelineStep.DRAFT_ONE
            }
        } ?: PipelineStep.DRAFT_ONE

        // Emit StepCompleted state - stepName is derived from stepType
        send(
            MessageGenerationState.StepCompleted(
                stepOutput = stepOutput,
                modelDisplayName = modelDisplayName,
                modelType = modelType,
                stepType = stepType
            )
        )
    }
}
