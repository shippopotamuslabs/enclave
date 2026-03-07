package com.browntowndev.pocketcrew.domain.port.inference

import com.browntowndev.pocketcrew.inference.llama.ChatMessage
import com.browntowndev.pocketcrew.inference.llama.GenerationEvent
import com.browntowndev.pocketcrew.inference.llama.LlamaModelConfig
import kotlinx.coroutines.flow.Flow

/**
 * Engine contract for llama.cpp operations.
 * Provides a clean Kotlin interface over the JNI layer.
 */
interface LlamaEnginePort {
    /**
     * Initialize the engine with the given model configuration.
     * Must be called before any other operations.
     */
    suspend fun initialize(config: LlamaModelConfig)

    /**
     * Start a new conversation with an optional system prompt.
     * Clears any previous conversation history.
     */
    suspend fun startConversation(systemPrompt: String? = null)

    /**
     * Append a message to the conversation history.
     */
    suspend fun appendMessage(message: ChatMessage)

    /**
     * Start text generation based on conversation history.
     * Returns a flow of GenerationEvent for streaming tokens.
     */
    fun generate(): Flow<GenerationEvent>

    /**
     * Stop the current generation if in progress.
     */
    suspend fun stopGeneration()

    /**
     * Reset the conversation, optionally with a new system prompt.
     */
    suspend fun resetConversation(systemPrompt: String? = null)

    /**
     * Unload the model and release all resources.
     */
    suspend fun unload()

    /**
     * Check if a model is currently loaded.
     */
    fun isLoaded(): Boolean
}
