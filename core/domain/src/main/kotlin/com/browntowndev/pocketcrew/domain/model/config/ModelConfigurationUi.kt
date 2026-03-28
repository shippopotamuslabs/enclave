package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

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
    val minP: Double,
    val repetitionPenalty: Double,
    val maxTokens: String,
    val contextWindow: String
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
    minP = tunings.minP,
    repetitionPenalty = tunings.repetitionPenalty,
    maxTokens = tunings.maxTokens.toString(),
    contextWindow = tunings.contextWindow.toString()
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
        minP = minP,
        repetitionPenalty = repetitionPenalty,
        maxTokens = maxTokens.toIntOrNull() ?: existingConfig.tunings.maxTokens,
        contextWindow = contextWindow.toIntOrNull() ?: existingConfig.tunings.contextWindow
    )
)
