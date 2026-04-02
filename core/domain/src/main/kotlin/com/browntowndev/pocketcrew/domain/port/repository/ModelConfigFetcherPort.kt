package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.config.LocalModelAsset
import com.browntowndev.pocketcrew.domain.model.config.RemoteModelConfig
import com.browntowndev.pocketcrew.domain.model.inference.ModelType

/**
 * Port for fetching and converting remote model configuration.
 */
interface ModelConfigFetcherPort {
    /**
     * Fetches the remote model configuration from the server.
     * @return Result containing map of ModelType to LocalModelAsset
     */
    suspend fun fetchRemoteConfig(): Result<Map<ModelType, LocalModelAsset>>

    /**
     * Converts remote config list to a map of ModelType to LocalModelAsset.
     * @param configs Raw remote configs parsed from JSON
     * @return Map of ModelType to LocalModelAsset
     */
    fun toLocalModelAssets(configs: List<RemoteModelConfig>): Map<ModelType, LocalModelAsset>
}
