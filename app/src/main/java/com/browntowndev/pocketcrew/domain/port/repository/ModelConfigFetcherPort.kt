package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.ModelConfiguration
import com.browntowndev.pocketcrew.domain.model.RemoteModelConfig

/**
 * Port for fetching and converting remote model configuration.
 */
interface ModelConfigFetcherPort {
    /**
     * Fetches the remote model configuration from the server.
     * @return Result containing list of ModelConfiguration
     */
    suspend fun fetchRemoteConfig(): Result<List<ModelConfiguration>>

    /**
     * Converts remote config list to ModelConfiguration list.
     * @param configs Raw remote configs parsed from JSON
     * @return List of ModelConfiguration
     */
    fun toModelConfigurations(configs: List<RemoteModelConfig>): List<ModelConfiguration>
}
