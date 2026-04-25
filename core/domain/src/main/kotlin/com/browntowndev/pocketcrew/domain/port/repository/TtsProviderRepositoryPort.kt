package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.TtsProviderAsset
import com.browntowndev.pocketcrew.domain.model.config.TtsProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Text-to-Speech provider configurations.
 */
interface TtsProviderRepositoryPort {
    /**
     * Observes all configured TTS providers.
     */
    fun getTtsProviders(): Flow<List<TtsProviderAsset>>

    /**
     * Gets all configured TTS providers synchronously.
     */
    suspend fun getTtsProvidersSync(): List<TtsProviderAsset>

    /**
     * Gets a specific TTS provider by its ID.
     */
    suspend fun getTtsProvider(id: TtsProviderId): TtsProviderAsset?

    /**
     * Saves a TTS provider configuration and its API key.
     *
     * @param asset The TTS asset metadata.
     * @param apiKey The API key to store securely (if provided).
     * @return The ID of the saved TTS provider.
     */
    suspend fun saveTtsProvider(
        asset: TtsProviderAsset,
        apiKey: String?,
    ): TtsProviderId

    /**
     * Deletes a TTS provider by its ID.
     */
    suspend fun deleteTtsProvider(id: TtsProviderId)
}
