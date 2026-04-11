package com.browntowndev.pocketcrew.domain.model.config

/**
 * Configuration for a local model tuning preset.
 *
 * @param isSystemPreset When true, this config came from a remote R2 download (factory preset).
 *   Such configs are read-only in the UI — users cannot modify tuning parameters.
 *   When false, this is a user-created config and is editable.
 *   NOTE: Renamed from 'isDefault' to avoid confusion with DefaultModelEntity.
 *   'isSystemPreset' means "is a factory-provided preset from R2", NOT "is the currently active default".
 */
data class LocalModelConfiguration(
    val id: LocalModelConfigurationId = LocalModelConfigurationId(""),
    val localModelId: LocalModelId,
    override val displayName: String,
    override val maxTokens: Int,
    override val contextWindow: Int,
    override val temperature: Double,
    override val topP: Double,
    override val topK: Int?,
    val minP: Double = 0.0,
    val repetitionPenalty: Double,
    val thinkingEnabled: Boolean = false,
    val systemPrompt: String,
    /**
     * When true, this config is a factory preset from R2 and is read-only in UI.
     * When false, this is a user-created config and is editable.
     */
    val isSystemPreset: Boolean = false
) : ModelTuningConfiguration
