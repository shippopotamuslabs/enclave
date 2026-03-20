package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of LlmInferencePort for testing.
 * Allows controlling inference events and verifying interactions.
 */
class FakeInferenceService : LlmInferencePort {

    private var shouldThrowOnSendPrompt = false
    private var shouldThrowOnSetHistory = false
    private val sentPrompts = mutableListOf<String>()
    private var historyMessages: List<ChatMessage> = emptyList()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        sentPrompts.add(prompt)
        
        if (shouldThrowOnSendPrompt) {
            shouldThrowOnSendPrompt = false
            throw RuntimeException("Simulated sendPrompt error")
        }
        
        // Default behavior - emit nothing unless configured
        // Subclasses or tests should override this behavior
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        historyMessages = messages
        
        if (shouldThrowOnSetHistory) {
            shouldThrowOnSetHistory = false
            throw RuntimeException("Simulated setHistory error")
        }
    }

    override fun closeSession() {
        // No-op for testing
    }

    fun getSentPrompts(): List<String> = sentPrompts.toList()

    fun getHistory(): List<ChatMessage> = historyMessages

    fun reset() {
        sentPrompts.clear()
        historyMessages = emptyList()
        shouldThrowOnSendPrompt = false
        shouldThrowOnSetHistory = false
    }

    fun setShouldThrowOnSendPrompt(shouldThrow: Boolean) {
        shouldThrowOnSendPrompt = shouldThrow
    }

    fun setShouldThrowOnSetHistory(shouldThrow: Boolean) {
        shouldThrowOnSetHistory = shouldThrow
    }

    /**
     * Configure to emit a completed response.
     */
    fun configureCompletedResponse(finalResponse: String, rawFullThought: String? = null, modelType: ModelType = ModelType.FAST) {
        // This method should be overridden in test setup
    }
}
