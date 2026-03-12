package com.browntowndev.pocketcrew.inference

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.usecase.chat.BufferThinkingStepsUseCase
import com.browntowndev.pocketcrew.domain.usecase.chat.MessageGenerationState
import com.browntowndev.pocketcrew.inference.worker.InferenceNotificationManager
import com.browntowndev.pocketcrew.inference.worker.InferencePipelineWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PipelineExecutorPort that uses WorkManager to execute
 * the Crew pipeline (multi-model inference pipeline) in the background.
 */
@Singleton
class WorkManagerPipelineExecutor @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val bufferThinkingSteps: BufferThinkingStepsUseCase
) : PipelineExecutorPort {

    private val workManager = WorkManager.getInstance(context)

    override fun executePipeline(
        chatId: String,
        userMessage: String,
        assistantMessageId: String
    ): Flow<MessageGenerationState> = flow {
        // Enqueue the pipeline worker
        enqueuePipelineWorker(chatId, userMessage)

        // Observe work progress and emit states
        observeWork(chatId).collect { state ->
            emit(state)
        }
    }

    private fun enqueuePipelineWorker(chatId: String, userMessage: String) {
        val initialState = PipelineState.createInitial(chatId, userMessage)

        val inputData = Data.Builder()
            .putString(InferenceNotificationManager.KEY_STATE_JSON, initialState.toJson())
            .build()

        val workRequest = OneTimeWorkRequestBuilder<InferencePipelineWorker>()
            .setInputData(inputData)
            .addTag(chatId)
            .build()

        workManager.enqueueUniqueWork(
            InferencePipelineWorker.workName(chatId),
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun observeWork(chatId: String): Flow<MessageGenerationState> = flow {
        // Accumulate thinking steps across multiple emissions
        val currentSteps = mutableListOf<String>()
        bufferThinkingSteps.reset()

        workManager.getWorkInfosForUniqueWorkFlow(
            InferencePipelineWorker.workName(chatId)
        ).collect { workInfos ->
            val workInfo = workInfos.firstOrNull() ?: return@collect

            when (workInfo.state) {
                WorkInfo.State.RUNNING -> {
                    val thinkingChunk = workInfo.progress.getString(PipelineState.KEY_THINKING_CHUNK)
                    val stepOutput = workInfo.progress.getString(PipelineState.KEY_STEP_OUTPUT)
                    val modelTypeName = workInfo.progress.getString(PipelineState.KEY_CURRENT_MODEL_TYPE)
                    val modelType = modelTypeName?.let {
                        try {
                            ModelType.valueOf(it)
                        } catch (e: Exception) {
                            ModelType.MAIN
                        }
                    } ?: ModelType.MAIN

                    if (stepOutput != null) {
                        emit(MessageGenerationState.GeneratingText(stepOutput))
                    }
                    if (thinkingChunk != null) {
                        // Buffer thinking chunks and emit thoughts as sentences complete
                        val newThoughts = bufferThinkingSteps(thinkingChunk)
                        for (thought in newThoughts) {
                            if (thought != currentSteps.lastOrNull()) {
                                currentSteps.add(thought)
                            }
                        }
                        if (newThoughts.isNotEmpty()) {
                            emit(MessageGenerationState.ThinkingLive(currentSteps.toList(), modelType))
                        }
                    }
                }
                WorkInfo.State.SUCCEEDED -> {
                    // Flush any remaining buffered words
                    val finalStep = bufferThinkingSteps.flush()
                    if (finalStep != null && finalStep != currentSteps.lastOrNull()) {
                        currentSteps.add(finalStep)
                    }

                    val finalResponse = workInfo.outputData.getString(PipelineState.KEY_FINAL_RESPONSE)

                    // Emit the final response as GeneratingText so the ViewModel can update the message
                    if (!finalResponse.isNullOrBlank()) {
                        emit(MessageGenerationState.GeneratingText(finalResponse))
                    }

                    emit(MessageGenerationState.Finished)
                }
                WorkInfo.State.FAILED -> {
                    val error = workInfo.outputData.getString("error") ?: "Unknown error"
                    emit(MessageGenerationState.Failed(IllegalStateException(error)))
                }
                WorkInfo.State.CANCELLED -> {
                    emit(MessageGenerationState.Failed(IllegalStateException("Pipeline cancelled")))
                }
                else -> { /* ENQUEUED, BLOCKED - ignore */ }
            }
        }
    }
}
