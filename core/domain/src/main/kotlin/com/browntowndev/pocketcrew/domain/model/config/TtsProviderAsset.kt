package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Unique identifier for a TTS provider configuration.
 */
@JvmInline
value class TtsProviderId(val value: String)

/**
 * Domain model representing a configured Text-to-Speech voice/provider.
 */
data class TtsProviderAsset(
    val id: TtsProviderId,
    val displayName: String,
    val provider: ApiProvider,
    val voiceName: String,
    val baseUrl: String? = null,
    val credentialAlias: String,
)
