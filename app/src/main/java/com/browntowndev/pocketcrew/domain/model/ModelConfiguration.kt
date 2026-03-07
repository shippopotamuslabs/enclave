package com.browntowndev.pocketcrew.domain.model

/**
 * Unified model configuration that represents a downloaded model.
 * Combines what was previously RemoteModelConfig + RegisteredModel.
 *
 * @property modelType The logical slot this config applies to (VISION, DRAFT, MAIN, FAST)
 * @property metadata File identity and storage information
 * @property tunings LLM generation parameters
 * @property persona How the model behaves (system prompt)
 */
data class ModelConfiguration(
    val modelType: ModelType,
    val metadata: Metadata,
    val tunings: Tunings,
    val persona: Persona
) {
    /**
     * File identity and storage information.
     */
    data class Metadata(
        val huggingFaceModelName: String,  // e.g., "TheBloke/Mistral-7B-v0.1-GGUF"
        val remoteFileName: String,       // e.g., "mistral-7b-v0.1.Q4_K_M.gguf"
        val localFileName: String,        // Same as remoteFileName - saved as-is locally
        val displayName: String,
        val md5: String,
        val sizeInBytes: Long,
        val modelFileFormat: ModelFileFormat
        // downloadUrl is computed by ModelUrlProviderPort, not stored
    )

    /**
     * LLM generation parameters.
     */
    data class Tunings(
        val temperature: Double = 0.0,
        val topK: Int = 40,
        val topP: Double = 0.95,
        val maxTokens: Int = 2048
    )

    /**
     * How the model behaves.
     */
    data class Persona(
        val systemPrompt: String
    )
}
