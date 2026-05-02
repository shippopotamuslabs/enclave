package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.MediaProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.MediaProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Media provider configurations (Image and Video).
 */
interface MediaProviderRepositoryPort {
    /**
     * Observes all configured media providers.
     */
    fun getMediaProviders(): Flow<List<MediaProviderAsset>>

    /**
     * Gets all configured media providers synchronously.
     */
    suspend fun getMediaProvidersSync(): List<MediaProviderAsset>

    /**
     * Gets a specific media provider by its ID.
     */
    suspend fun getMediaProvider(id: MediaProviderId): MediaProviderAsset?

    /**
     * Saves a media provider configuration and its API key.
     *
     * @param asset The media asset metadata.
     * @param apiKey The API key to store securely (if provided).
     * @return The ID of the saved media provider.
     */
    suspend fun saveMediaProvider(
        asset: MediaProviderAsset,
        apiKey: String?,
    ): MediaProviderId

    /**
     * Deletes a media provider by its ID.
     */
    suspend fun deleteMediaProvider(id: MediaProviderId)
}
