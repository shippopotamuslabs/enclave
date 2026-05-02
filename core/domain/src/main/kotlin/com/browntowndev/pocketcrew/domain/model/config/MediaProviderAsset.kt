package com.browntowndev.pocketcrew.domain.model.config

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider

/**
 * Unique identifier for a media provider configuration.
 */
@JvmInline
value class MediaProviderId(val value: String)

/**
 * Enum representing media capabilities.
 */
enum class MediaCapability(val displayName: String) {
    IMAGE("Image"),
    VIDEO("Video"),
    MUSIC("Music")
}

/**
 * Domain model representing a configured Media provider.
 */
data class MediaProviderAsset(
    val id: MediaProviderId,
    val displayName: String,
    val provider: ApiProvider,
    val capability: MediaCapability,
    val modelName: String? = null,
    val baseUrl: String? = null,
    val credentialAlias: String,
)
