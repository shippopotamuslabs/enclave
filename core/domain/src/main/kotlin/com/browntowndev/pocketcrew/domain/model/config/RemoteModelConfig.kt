package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.download.DownloadSource
import com.browntowndev.pocketcrew.domain.model.inference.ModelFileFormat
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Represents model configuration fetched from remote server (model_config.json).
 *
 * @property modelType The logical slot this config applies to (VISION, DRAFT_ONE, DRAFT_TWO, MAIN, FAST)
 * @property fileName The filename on the remote server
 * @property displayName Human-readable name for UI display
 * @property sha256 SHA256 hash for integrity verification
 * @property sizeInBytes Exact file size in bytes for validation
 * @property modelFileFormat The file format of the model (LITERTLM, GGUF, or TASK)
 * @property source The source to download the model from (HF, R2)
 * @property temperature Sampling temperature for LLM generation
 * @property topK Top-k sampling parameter
 * @property topP Top-p (nucleus) sampling parameter
 * @property minP Min-p sampling parameter (required - throws if missing)
 * @property repetitionPenalty Repetition penalty for sampling
 * @property maxTokens Maximum tokens to generate
 * @property contextWindow LLM context window size in tokens (for llama.cpp)
 * @property systemPrompt Optional system prompt to prepend to conversations
 * @property thinkingEnabled Whether to enable thinking/reasoning mode for this model (llama.cpp extraction)
 * @property visionCapable Whether this model is capable of image understanding (multimodal)
 */
data class RemoteModelConfig(
    val modelType: ModelType,
    val fileName: String,
    val huggingFaceModelName: String = "", // Used for HuggingFace download URL
    val displayName: String,
    val sha256: String,
    val sizeInBytes: Long = 0L,
    val modelFileFormat: ModelFileFormat = ModelFileFormat.LITERTLM,
    val source: DownloadSource = DownloadSource.HUGGING_FACE,
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val maxTokens: Int,
    val contextWindow: Int,
    val systemPrompt: String,
    val thinkingEnabled: Boolean = false,
    val visionCapable: Boolean = false
)
