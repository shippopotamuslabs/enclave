package com.browntowndev.pocketcrew.domain.model

import com.browntowndev.pocketcrew.domain.model.ModelFileFormat

/**
 * Represents model configuration fetched from remote server (model_config.json).
 *
 * @property modelType The logical slot this config applies to (VISION, DRAFT_ONE, DRAFT_TWO, MAIN, FAST)
 * @property fileName The filename on the remote server
 * @property displayName Human-readable name for UI display
 * @property sha256 SHA256 hash for integrity verification
 * @property sizeInBytes Exact file size in bytes for validation
 * @property modelFileFormat The file format of the model (LITERTLM, GGUF, or TASK)
 * @property temperature Sampling temperature for LLM generation
 * @property topK Top-k sampling parameter
 * @property topP Top-p (nucleus) sampling parameter
 * @property maxTokens Maximum tokens to generate
 * @property contextWindow LLM context window size in tokens (for llama.cpp)
 * @property systemPrompt Optional system prompt to prepend to conversations
 */
data class RemoteModelConfig(
    val modelType: ModelType,
    val fileName: String,
    val huggingFaceModelName: String = "", // Used for HuggingFace download URL
    val displayName: String,
    val sha256: String,
    val sizeInBytes: Long = 0L,
    val modelFileFormat: ModelFileFormat = ModelFileFormat.LITERTLM,
    val temperature: Double = 0.0,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int,
    val contextWindow: Int,
    val systemPrompt: String
)
