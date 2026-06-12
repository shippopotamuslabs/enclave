package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.download.DownloadSource
import com.browntowndev.enclave.domain.model.inference.ModelFileFormat
import com.browntowndev.enclave.domain.model.inference.ModelType
import kotlinx.serialization.Serializable

/**
 * Represents a physical model asset fetched from remote server (model_config.json).
 * Each asset corresponds to a single downloadable file, identified by SHA256.
 * An asset can have multiple configurations (tuning presets) that share the same file.
 *
 * @property displayName Human-readable name for the downloadable file (asset level)
 * @property huggingFaceModelName HuggingFace model repo name (used for HF download URL)
 * @property huggingFacePath HuggingFace model repo path (used for HF download URL)
 * @property fileName The filename on the remote server
 * @property sha256 SHA256 hash for integrity verification
 * @property sizeInBytes Exact file size in bytes for validation
 * @property modelFileFormat The file format of the model (LITERTLM, GGUF, or TASK)
 * @property source The source to download the model from (HF, R2)
 * @property utilityType Utility model kind for zero-configuration support assets
 * @property isMultimodal Whether this model is capable of image understanding (multimodal input)
 * @property mmprojFileName Remote filename of the multimodal projector file (llama.cpp only)
 * @property mmprojSha256 SHA256 of the multimodal projector file
 * @property mmprojSizeInBytes Size of the multimodal projector file
 * @property configurations List of tuning presets for this asset
 */
@Serializable
data class RemoteModelAsset(
    @kotlinx.serialization.json.JsonNames("displayName", "display_name")
    val displayName: String = "",
    val huggingFaceModelName: String = "",
    val huggingFacePath: String? = null,
    val fileName: String,
    val sha256: String,
    val sizeInBytes: Long = 0L,
    val modelFileFormat: ModelFileFormat? = null,
    val source: DownloadSource = DownloadSource.HUGGING_FACE,
    val utilityType: UtilityType? = null,
    val isMultimodal: Boolean = false,
    val mmprojFileName: String? = null,
    val mmprojSha256: String? = null,
    val mmprojSizeInBytes: Long? = null,
    val configurations: List<RemoteModelConfiguration> = emptyList(),
)

/**
 * Resolves the model file format for a catalog asset.
 * Prefers the explicit [RemoteModelAsset.modelFileFormat] field; when the catalog omits it,
 * infers the format from the file name extension. A blanket LITERTLM fallback previously
 * mislabeled GGUF assets and hid llama.cpp-only settings in the UI.
 */
fun RemoteModelAsset.resolvedModelFileFormat(): ModelFileFormat =
    modelFileFormat
        ?: ModelFileFormat.fromFileName(fileName)
        ?: ModelFileFormat.LITERTLM

/**
 * Resolves the human-readable asset label from catalog JSON.
 * Prefers asset-level [displayName], then the first preset label, then a short name from [huggingFaceModelName].
 */
fun RemoteModelAsset.effectiveDisplayName(): String {
    displayName.takeIf { it.isNotBlank() }?.let { return it }
    configurations
        .firstOrNull()
        ?.displayName
        ?.takeIf { it.isNotBlank() }
        ?.let { return it }
    if (huggingFaceModelName.isBlank()) return ""
    return huggingFaceModelName
        .substringAfterLast('/')
        .replace(Regex("""[-_]"""), " ")
        .trim()
}

/**
 * A tuning preset within a remote model asset.
 * Each configuration specifies its own tuning parameters and which ModelType slots
 * it should be assigned to by default.
 *
 * @property configId Unique identifier for this configuration
 * @property displayName Human-readable name for UI display
 * @property systemPrompt System prompt to prepend to conversations
 * @property temperature Sampling temperature for LLM generation
 * @property topK Top-k sampling parameter
 * @property topP Top-p (nucleus) sampling parameter
 * @property minP Min-p sampling parameter
 * @property repetitionPenalty Repetition penalty for sampling
 * @property maxTokens Maximum tokens to generate
 * @property contextWindow LLM context window size in tokens
 * @property thinkingEnabled Whether to enable thinking/reasoning mode
 * @property defaultAssignments Which ModelType slots this config should be assigned to by default
 */
@Serializable
data class RemoteModelConfiguration(
    val configId: LocalModelConfigurationId,
    val displayName: String,
    val systemPrompt: String,
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val maxTokens: Int,
    val contextWindow: Int,
    val thinkingEnabled: Boolean = false,
    val reasoningBudget: Int? = null,
    val defaultAssignments: List<ModelType> = emptyList(),
)
