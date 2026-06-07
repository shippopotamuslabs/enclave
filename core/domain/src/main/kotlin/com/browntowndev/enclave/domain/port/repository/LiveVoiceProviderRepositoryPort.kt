package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderAsset
import com.browntowndev.enclave.domain.model.config.LiveVoiceProviderId
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing Live Voice provider configurations.
 */
interface LiveVoiceProviderRepositoryPort {
    fun getLiveVoiceProviders(): Flow<List<LiveVoiceProviderAsset>>

    suspend fun getLiveVoiceProvidersSync(): List<LiveVoiceProviderAsset>

    suspend fun getLiveVoiceProvider(id: LiveVoiceProviderId): LiveVoiceProviderAsset?

    suspend fun saveLiveVoiceProvider(
        asset: LiveVoiceProviderAsset,
        apiKey: String?,
    ): LiveVoiceProviderId

    suspend fun deleteLiveVoiceProvider(id: LiveVoiceProviderId)
}
