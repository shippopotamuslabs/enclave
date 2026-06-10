package com.browntowndev.enclave.feature.moa

import com.browntowndev.enclave.domain.model.chat.MessageGenerationState
import com.browntowndev.enclave.domain.model.chat.MessageId
import com.browntowndev.enclave.domain.model.chat.isPipelineTerminal
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.model.inference.PipelineState
import com.browntowndev.enclave.domain.port.inference.PipelineExecutorPort
import com.browntowndev.enclave.domain.port.repository.PipelineStateRepository
import com.browntowndev.enclave.feature.inference.InferenceEventBus
import com.browntowndev.enclave.feature.moa.service.InferenceServiceStarter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.transformWhile
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of PipelineExecutorPort that uses InferenceService (custom foreground Service)
 * to execute the Council pipeline (multi-model inference pipeline) in the background.
 *
 * This replaces the WorkManager-based approach which used dataSync foreground type
 * that has a 6-hour quota limit on Android 15+. The new approach uses specialUse
 * foreground type which has no quota limits.
 *
 * Pipeline progress is communicated via [InferenceEventBus] using per-chatId
 * streams. This avoids the IPC overhead of sending broadcast Intents for every token.
 */
@Singleton
class InferenceServicePipelineExecutor
    @Inject
    constructor(
        private val serviceStarter: InferenceServiceStarter,
        private val pipelineStateRepository: PipelineStateRepository,
        private val inferenceEventBus: InferenceEventBus,
    ) : PipelineExecutorPort {
        override fun executePipeline(
            chatId: String,
            userMessage: String,
            userMessageId: MessageId?,
            assistantMessageId: MessageId?,
            toolingEnabled: Boolean,
        ): Flow<MessageGenerationState> =
            flow {
                val initialState = PipelineState.createInitial(chatId, userMessage, toolingEnabled)
                val stateJson = initialState.toJson()
                val stateFlow = inferenceEventBus.openPipelineRequest(chatId)
                try {
                    serviceStarter.startService(
                        chatId = chatId,
                        userMessage = userMessage,
                        stateJson = stateJson,
                        userMessageId = userMessageId?.value,
                        assistantMessageId = assistantMessageId?.value,
                    )
                } catch (ex: Exception) {
                    inferenceEventBus.clearPipelineRequest(chatId)
                    throw ex
                }
                try {
                    emitAll(
                        stateFlow.transformWhile { state ->
                            emit(state)
                            !state.isPipelineTerminal
                        },
                    )
                } finally {
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
            onError: (Throwable) -> Unit,
        ): Flow<MessageGenerationState> {
            val savedState = pipelineStateRepository.getPipelineState(chatId)

            if (savedState == null) {
                val error = IllegalStateException("No saved pipeline state found for resume")
                onError(error)
                return flow {
                    emit(
                        MessageGenerationState.Failed(
                            error,
                            ModelType.MAIN,
                        ),
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
                emitAll(
                    stateFlow.transformWhile { state ->
                        emit(state)
                        val terminal = state.isPipelineTerminal
                        if (terminal) {
                            onComplete()
                        }
                        !terminal
                    },
                )
            }.onCompletion {
                inferenceEventBus.clearPipelineRequest(chatId)
            }
        }
    }
