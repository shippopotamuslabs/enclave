package com.browntowndev.pocketcrew.domain.usecase

import com.browntowndev.pocketcrew.domain.model.chat.ChatMessage
import com.browntowndev.pocketcrew.domain.model.inference.GenerationOptions
import com.browntowndev.pocketcrew.domain.model.inference.ModelType
import com.browntowndev.pocketcrew.domain.port.inference.InferenceEvent
import com.browntowndev.pocketcrew.domain.port.inference.LlmInferencePort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Fake implementation of LlmInferencePort for testing.
 * Allows controlling inference events and verifying interactions.
 */
class FakeInferenceService(private val modelType: ModelType = ModelType.FAST) : LlmInferencePort {

    private var shouldThrowOnSendPrompt = false
    private var shouldThrowOnSetHistory = false
    private val sentPrompts = mutableListOf<String>()
    private val sentOptions = mutableListOf<GenerationOptions>()
    private var emittedEvents: List<InferenceEvent> = emptyList()
    private var historyMessages: List<ChatMessage> = emptyList()

    override fun sendPrompt(prompt: String, closeConversation: Boolean): Flow<InferenceEvent> {
        return sendPrompt(prompt, GenerationOptions(reasoningBudget = 0, modelType = modelType), closeConversation)
    }

    override fun sendPrompt(prompt: String, options: GenerationOptions, closeConversation: Boolean): Flow<InferenceEvent> = flow {
        sentPrompts.add(prompt)
        sentOptions.add(options)

        if (shouldThrowOnSendPrompt) {
            shouldThrowOnSendPrompt = false
            throw RuntimeException("Simulated sendPrompt with options error")
        }

        emittedEvents.forEach { emit(it) }
    }

    override suspend fun setHistory(messages: List<ChatMessage>) {
        historyMessages = messages
        
        if (shouldThrowOnSetHistory) {
            shouldThrowOnSetHistory = false
            throw RuntimeException("Simulated setHistory error")
        }
    }

    override suspend fun closeSession() {
        // No-op for fake
    }

    fun getSentPrompts(): List<String> = sentPrompts.toList()

    fun getSentOptions(): List<GenerationOptions> = sentOptions.toList()

    fun getHistory(): List<ChatMessage> = historyMessages

    fun reset() {
        sentPrompts.clear()
        sentOptions.clear()
        emittedEvents = emptyList()
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

    fun setEmittedEvents(events: List<InferenceEvent>) {
        emittedEvents = events
    }

    /**
     * Configure to emit a completed response.
     */
    fun configureCompletedResponse(finalResponse: String, rawFullThought: String? = null, modelType: ModelType = ModelType.FAST) {
        // This method should be overridden in test setup
    }
}
