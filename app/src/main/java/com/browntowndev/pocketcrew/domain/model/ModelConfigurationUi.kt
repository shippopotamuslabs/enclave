package com.browntowndev.pocketcrew.domain.model

/**
 * UI-friendly model configuration for the settings screen.
 * Contains only the config values the UI cares about.
 */
data class ModelConfigurationUi(
    val modelType: ModelType,
    val displayName: String,
    val huggingFaceModelName: String,
    val temperature: Double,
    val topK: Int,
    val topP: Double,
    val maxTokens: Int,
    val contextWindow: Int
)

/**
 * Extension to convert ModelConfiguration to UI model.
 */
fun ModelConfiguration.toUi(): ModelConfigurationUi = ModelConfigurationUi(
    modelType = modelType,
    displayName = metadata.displayName,
    huggingFaceModelName = metadata.huggingFaceModelName,
    temperature = tunings.temperature,
    topK = tunings.topK,
    topP = tunings.topP,
    maxTokens = tunings.maxTokens,
    contextWindow = tunings.contextWindow
)

/**
 * Extension to convert UI model back to ModelConfiguration.
 */
fun ModelConfigurationUi.toModelConfiguration(
    existingConfig: ModelConfiguration
): ModelConfiguration = existingConfig.copy(
    metadata = existingConfig.metadata.copy(
        huggingFaceModelName = huggingFaceModelName
    ),
    tunings = ModelConfiguration.Tunings(
        temperature = temperature,
        topK = topK,
        topP = topP,
        maxTokens = maxTokens,
        contextWindow = contextWindow
    )
)
