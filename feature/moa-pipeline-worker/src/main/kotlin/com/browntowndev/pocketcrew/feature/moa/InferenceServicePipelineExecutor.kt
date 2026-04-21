package com.browntowndev.pocketcrew.feature.moa

import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.model.inference.PipelineState
import com.browntowndev.pocketcrew.domain.port.inference.PipelineExecutorPort
import com.browntowndev.pocketcrew.domain.port.repository.PipelineStateRepository
import com.browntowndev.pocketcrew.domain.model.chat.MessageGenerationState
import com.browntowndev.pocketcrew.domain.model.chat.isPipelineTerminal
import com.browntowndev.pocketcrew.feature.inference.InferenceEventBus
import com.browntowndev.pocketcrew.feature.moa.service.InferenceServiceStarter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile

/**
 * Implementation of PipelineExecutorPort that uses InferenceService (custom foreground Service)
 * to execute the Crew pipeline (multi-model inference pipeline) in the background.
 *
 * This replaces the WorkManager-based approach which used dataSync foreground type
 * that has a 6-hour quota limit on Android 15+. The new approach uses specialUse
 * foreground type which has no quota limits.
 *
 * Pipeline progress is communicated via [InferenceEventBus] using per-chatId
 * streams. This avoids the IPC overhead of sending broadcast Intents for every token.
 */
@Singleton
class InferenceServicePipelineExecutor @Inject constructor(
    private val serviceStarter: InferenceServiceStarter,
    private val pipelineStateRepository: PipelineStateRepository,
    private val inferenceEventBus: InferenceEventBus,
) : PipelineExecutorPort {

    companion object {
        private const val TAG = "InferenceServicePipelineExecutor"
    }

    override fun executePipeline(
        chatId: String,
        userMessage: String,
    ): Flow<MessageGenerationState> {
        val initialState = PipelineState.createInitial(chatId, userMessage)
        val stateJson = initialState.toJson()

        // Open the stream BEFORE starting the service to prevent a race condition:
        // if the service starts first, it may emit to a stream the executor hasn't
        // subscribed to yet, causing those emissions to be lost or orphaned.
        val stateFlow = inferenceEventBus.openPipelineRequest(chatId)

        try {
            serviceStarter.startService(chatId, userMessage, stateJson)
        } catch (ex: Exception) {
            inferenceEventBus.clearPipelineRequest(chatId)
            throw ex
        }

        return flow {
            emitAll(stateFlow.transformWhile { state ->
                emit(state)
                !state.isPipelineTerminal
            })
        }.onCompletion {
            inferenceEventBus.clearPipelineRequest(chatId)
        }
    }

    override suspend fun stopPipeline(pipelineId: String) {
        serviceStarter.stopService()
        pipelineStateRepository.clearPipelineState(pipelineId)
    }

    override suspend fun resumeFromState(
        chatId: String,
        pipelineId: String,
        onComplete: () -> Unit,
        onError: (Throwable) -> Unit
    ): Flow<MessageGenerationState> {
        val savedState = pipelineStateRepository.getPipelineState(chatId)

        if (savedState == null) {
            val error = IllegalStateException("No saved pipeline state found for resume")
            onError(error)
            return flow {
                emit(
                    MessageGenerationState.Failed(
                        error,
                        ModelType.MAIN
                    )
                )
            }
        }

        val stateJson = savedState.toJson()

        // Open the stream BEFORE starting the service (same race-condition fix as executePipeline).
        val stateFlow = inferenceEventBus.openPipelineRequest(chatId)

        try {
            serviceStarter.startServiceResume(chatId, stateJson)
        } catch (ex: Exception) {
            inferenceEventBus.clearPipelineRequest(chatId)
            throw ex
        }

        return flow {
            emitAll(stateFlow.transformWhile { state ->
                emit(state)
                val terminal = state.isPipelineTerminal
                if (terminal) {
                    onComplete()
                }
                !terminal
            })
        }.onCompletion {
            inferenceEventBus.clearPipelineRequest(chatId)
        }
    }
}