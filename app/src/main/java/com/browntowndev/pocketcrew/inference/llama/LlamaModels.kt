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
 */
data class LlamaSamplingConfig(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val maxTokens: Int = 512,
    val repeatPenalty: Float = 1.1f,
    val contextSize: Int = 4096,
    val threads: Int = 4,
    val batchSize: Int = 512,
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
