package com.browntowndev.enclave.domain.port.repository

import com.browntowndev.enclave.domain.model.config.OpenRouterModelEndpoint
import com.browntowndev.enclave.domain.model.inference.ApiProvider
import com.browntowndev.enclave.domain.model.inference.DiscoveredApiModel

data class ApiModelCatalogResult(
    val models: List<DiscoveredApiModel>,
    val isTruncated: Boolean = false,
)

interface ApiModelCatalogPort {
    suspend fun fetchModels(
        provider: ApiProvider,
        apiKey: String,
        baseUrl: String? = null,
    ): ApiModelCatalogResult

    suspend fun fetchModel(
        provider: ApiProvider,
        apiKey: String,
        modelId: String,
        baseUrl: String? = null,
    ): DiscoveredApiModel?

    suspend fun fetchModelEndpoints(
        modelId: String,
        apiKey: String,
        baseUrl: String? = null,
    ): List<OpenRouterModelEndpoint>
}
