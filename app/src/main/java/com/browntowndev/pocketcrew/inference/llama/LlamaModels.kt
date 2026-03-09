package com.browntowndev.pocketcrew.inference.llama

/**
 * Represents the role of a message in a chat conversation.
 */
enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

/**
 * A single message in a chat conversation.
 */
data class ChatMessage(
    val role: ChatRole,
    val content: String
)

/**
 * Sampling configuration for llama.cpp inference.
 * Optimized defaults for mobile devices.
 *
 * @property maxTokens Maximum tokens to generate (output limit)
 * @property contextWindow LLM context window size in tokens (input + output limit)
 */
data class LlamaSamplingConfig(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 512,    // Maximum tokens to generate
    val repeatPenalty: Float = 1.1f,
    val contextWindow: Int = 2048,  // Context window size (input + output)
    val threads: Int = 4,           // Increased for better CPU parallelism
    val batchSize: Int = 256,       // Reduced from 512 to reduce memory pressure
    val gpuLayers: Int = 0
)

/**
 * Full model configuration for llama.cpp.
 */
data class LlamaModelConfig(
    val modelPath: String,
    val systemPrompt: String = "You are a helpful assistant.",
    val sampling: LlamaSamplingConfig = LlamaSamplingConfig()
)

/**
 * Events emitted during llama.cpp text generation.
 */
sealed interface GenerationEvent {
    /**
     * Emitted when a new token is generated.
     */
    data class Token(val text: String) : GenerationEvent

    /**
     * Emitted when generation is complete.
     */
    data class Completed(
        val fullText: String,
        val promptTokens: Int? = null,
        val generatedTokens: Int? = null
    ) : GenerationEvent

    /**
     * Emitted when an error occurs during generation.
     */
    data class Error(val throwable: Throwable) : GenerationEvent
}
