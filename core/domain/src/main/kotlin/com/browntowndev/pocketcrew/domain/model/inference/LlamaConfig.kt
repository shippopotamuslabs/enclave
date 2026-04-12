package com.browntowndev.pocketcrew.domain.model.inference

/**
 * Sampling configuration for llama.cpp inference.
 * Optimized defaults for mobile devices.
 *
 * @property temperature Sampling temperature (higher = more creative, lower = more deterministic)
 * @property topK Top-k sampling parameter
 * @property topP Nucleus sampling parameter (0.0-1.0)
 * @property minP Minimum probability threshold for nucleus sampling
 * @property maxTokens Maximum tokens to generate (output limit)
 * @property repeatPenalty Repetition penalty for token generation
 * @property contextWindow LLM context window size in tokens (input + output limit)
 * @property batchSize Batch size for processing
 * @property gpuLayers Number of layers to offload to GPU (0 = CPU only)
 * @property thinkingEnabled Whether to enable thinking/reasoning mode
 */
data class LlamaSamplingConfig(
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
    val minP: Float,
    val maxTokens: Int = 2048,
    val repeatPenalty: Float = 1.1f,
    val contextWindow: Int = 4096,
    val batchSize: Int = 256,
    val gpuLayers: Int = 0,
    val thinkingEnabled: Boolean = false
)

/**
 * Full model configuration for llama.cpp.
 *
 * @property modelPath Path to the model file on disk
 * @property systemPrompt System prompt to use for the conversation
 * @property sampling Sampling configuration
 */
data class LlamaModelConfig(
    val modelPath: String,
    val mmprojPath: String? = null,
    val systemPrompt: String = "You are a helpful assistant.",
    val sampling: LlamaSamplingConfig = LlamaSamplingConfig(minP = 0.0f)
)
