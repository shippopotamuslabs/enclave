package com.browntowndev.enclave.domain.model.config

import com.browntowndev.enclave.domain.model.inference.ApiProvider

/**
 * Unique identifier for a Live Voice provider configuration.
 */
@JvmInline
value class LiveVoiceProviderId(
    val value: String,
)

/**
 * Domain model representing a configured Live Voice (realtime speech-to-speech) provider.
 */
data class LiveVoiceProviderAsset(
    val id: LiveVoiceProviderId,
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
)
