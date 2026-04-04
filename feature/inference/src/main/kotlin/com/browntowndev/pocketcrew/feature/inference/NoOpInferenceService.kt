package com.browntowndev.pocketcrew.feature.inference

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback implementation used when a requested model is not available (e.g. not yet downloaded).
 * Instead of crashing, it emits an error event when a prompt is sent.
 */
@Singleton
class NoOpInferenceService @Inject constructor(
    private val modelType: ModelType
) : LlmInferencePort {

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        emit(InferenceEvent.Error(
            cause = IllegalStateException("Model for $modelType is not available. Please download it first."),
            modelType = modelType
        ))
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        // No-op
    }

    override suspend fun closeSession() {
        // No-op
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        emit(InferenceEvent.Error(
            cause = IllegalStateException("Model for $modelType is not available. Please download it first."),
            modelType = modelType
        ))
    }
}
