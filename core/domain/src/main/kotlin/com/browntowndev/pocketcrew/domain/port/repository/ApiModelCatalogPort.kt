package com.browntowndev.pocketcrew.domain.port.repository

import com.browntowndev.pocketcrew.domain.model.inference.ApiProvider
import com.browntowndev.pocketcrew.domain.model.inference.DiscoveredApiModel

interface ApiModelCatalogPort {
    suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String? = null
    ): List<DiscoveredApiModel>

    suspend fun fetchModel(
        provider: ApiProvider,
        apiKey: String,
        modelId: String,
        baseUrl: String? = null
    ): DiscoveredApiModel?
}
