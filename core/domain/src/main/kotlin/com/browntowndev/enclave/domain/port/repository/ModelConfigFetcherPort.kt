package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.LocalModelAsset
import com.browntowndev.enclave.domain.model.config.RemoteModelAsset

/**
 * Port for fetching and converting remote model configuration.
 */
interface ModelConfigFetcherPort {
    /**
     * Fetches the remote model configuration from the server.
     * @return Result containing list of LocalModelAsset (each asset may have multiple configurations)
     */
    suspend fun fetchRemoteConfig(): Result<List<LocalModelAsset>>

    /**
     * Converts remote asset list to a list of LocalModelAsset.
     * @param assets Raw remote assets parsed from JSON
     * @return List of LocalModelAsset
     */
    fun toLocalModelAssets(assets: List<RemoteModelAsset>): List<LocalModelAsset>
}
