package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Represents which model (on-device or API) is assigned to a given ModelType slot.
 */
data class DefaultModelAssignment(
    val modelType: ModelType,
    val localConfigId: LocalModelConfigurationId? = null,
    val apiConfigId: ApiModelConfigurationId? = null,
    val ttsProviderId: TtsProviderId? = null,
    // Resolved display data for the UI
    val displayName: String? = null,
    val presetName: String? = null,
    val providerName: String? = null,
)