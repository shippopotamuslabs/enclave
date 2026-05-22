package com.browntowndev.enclave.feature.inference

import com.browntowndev.enclave.domain.model.chat.ChatMessage
import com.browntowndev.enclave.domain.model.inference.GenerationOptions
import com.browntowndev.enclave.domain.model.inference.ModelType
import com.browntowndev.enclave.domain.port.inference.InferenceEvent
import com.browntowndev.enclave.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback implementation used when a requested model is not available (e.g. not yet downloaded).
 * Instead of crashing, it emits an error event when a prompt is sent.
 */
@Singleton
class NoOpInferenceService
    @Inject
    constructor(
        private val modelType: ModelType,
    ) : LlmInferencePort {
        override fun sendPrompt(
            prompt: String,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> = sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)

        override suspend fun setHistory(messages: List<ChatMessage>) {
            // No-op
        }

        override suspend fun closeSession() {
            // No-op
        }

        override fun sendPrompt(
            prompt: String,
            options: GenerationOptions,
            closeConversation: Boolean,
        ): Flow<InferenceEvent> =
            flow {
                emit(
                    InferenceEvent.Error(
                        cause = IllegalStateException("${modelType.displayName()} model is not available. Please download it first."),
                        modelType = modelType,
                    ),
                )
            }
    }
