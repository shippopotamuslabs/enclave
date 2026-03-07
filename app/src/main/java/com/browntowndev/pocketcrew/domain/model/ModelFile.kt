package com.browntowndev.pocketcrew.domain.model

/**
 * Represents a model file that needs to be downloaded.
 *
 * @property sizeBytes The exact size in bytes (critical for validation & resume)
 * @property url The full URL to download from
 * @property md5 Optional MD5 hash for verification after download
 * @property modelTypes The logical slots this file serves (VISION, DRAFT, MAIN, FAST)
 * @property originalFileName The original filename on the remote server
 * @property displayName Human-readable name for UI and registry tracking
 * @property modelFileFormat The file format of the model (LITERTLM or TASK) - used to compute filename
 * @property temperature Sampling temperature for LLM generation
 * @property topK Top-k sampling parameter
 * @property topP Top-p (nucleus) sampling parameter
 * @property maxTokens Maximum tokens to generate
 * @property systemPrompt Optional system prompt to prepend to conversations
 */
data class ModelFile(
    val sizeBytes: Long,
    val url: String,
    val md5: String,
    val modelTypes: List<ModelType>,
    val originalFileName: String,
    val displayName: String,
    val modelFileFormat: ModelFileFormat,
    val temperature: Double = 0.0,
    val topK: Int = 40,
    val topP: Double = 0.95,
    val maxTokens: Int,
    val systemPrompt: String,
) {
    val filenames: List<String>
        get() = modelTypes.map { "${it.name.lowercase()}.${modelFileFormat.extension.removePrefix(".")}" }

    /**
     * Checks to see if this instance has any models that `other` does not
     * @param other The other instance to compare against
     */
    fun anyDifferentModelsThan(other: ModelFile?): Boolean =
        other == null ||
        modelTypes.any { it !in other.modelTypes }

    /**
     * Checks to see if this instance has the same format as `other`
     * @param other The other instance to compare against
     */
    fun hasSameFormatAs(other: ModelFile?): Boolean =
        other != null &&
        modelFileFormat == other.modelFileFormat

}
