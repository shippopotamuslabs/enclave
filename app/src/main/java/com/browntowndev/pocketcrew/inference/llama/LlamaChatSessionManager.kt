package com.browntowndev.pocketcrew.inference.llama

import com.browntowndev.pocketcrew.domain.port.inference.LlamaEnginePort
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the lifecycle of llama.cpp chat sessions.
 * Keeps conversation state separate from raw engine lifecycle.
 */
class LlamaChatSessionManager @Inject constructor(
    private val engine: LlamaEnginePort
) {
    /**
     * Initialize the engine with the given model configuration.
     */
    suspend fun initializeEngine(config: LlamaModelConfig) {
        engine.initialize(config)
    }

    /**
     * Start a new conversation with an optional system prompt.
     */
    suspend fun startNewConversation(systemPrompt: String? = null) {
        engine.startConversation(systemPrompt)
    }

    /**
     * Send a user message to the conversation.
     */
    suspend fun sendUserMessage(text: String) {
        engine.appendMessage(ChatMessage(ChatRole.USER, text))
    }

    /**
     * Stream the assistant's response.
     */
    fun streamAssistantResponse(): Flow<GenerationEvent> {
        return engine.generate()
    }

    /**
     * Stop the current generation if in progress.
     */
    suspend fun stopCurrentGeneration() {
        engine.stopGeneration()
    }

    /**
     * Clear the conversation with an optional new system prompt.
     */
    suspend fun clearConversation(systemPrompt: String? = null) {
        engine.resetConversation(systemPrompt)
    }

    /**
     * Shutdown the engine and release all resources.
     */
    suspend fun shutdown() {
        engine.unload()
    }
}
