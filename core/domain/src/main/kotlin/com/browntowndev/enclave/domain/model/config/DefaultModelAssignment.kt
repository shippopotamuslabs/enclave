package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ModelType

/**
 * Represents which model (on-device or API) is assigned to a given ModelType slot.
 */
data class DefaultModelAssignment(
    val modelType: ModelType,
    val localConfigId: LocalModelConfigurationId? = null,
    val apiConfigId: ApiModelConfigurationId? = null,
    val ttsProviderId: TtsProviderId? = null,
    val mediaProviderId: MediaProviderId? = null,
    val liveVoiceProviderId: LiveVoiceProviderId? = null,
    // Resolved display data for the UI
    val displayName: String? = null,
    val presetName: String? = null,
    val providerName: String? = null,
    /** API preset model id (provider catalog id), e.g. grok-4-3. */
    val modelId: String? = null,
)
